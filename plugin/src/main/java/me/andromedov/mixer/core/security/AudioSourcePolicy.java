package me.andromedov.mixer.core.security;

import java.io.IOException;
import java.net.IDN;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

/** Validates untrusted audio sources before they reach HTTP or local-file loaders. */
public final class AudioSourcePolicy {
    private final Path allowedAudioDirectory;
    private final HostResolver hostResolver;

    public AudioSourcePolicy(Path allowedAudioDirectory) {
        this(allowedAudioDirectory, InetAddress::getAllByName);
    }

    AudioSourcePolicy(Path allowedAudioDirectory, HostResolver hostResolver) {
        this.allowedAudioDirectory = Objects.requireNonNull(allowedAudioDirectory,
                "allowedAudioDirectory").toAbsolutePath().normalize();
        this.hostResolver = Objects.requireNonNull(hostResolver, "hostResolver");
    }

    /**
     * Returns a source safe to pass to Lavaplayer. HTTP hosts are resolved and
     * local paths are canonicalized into Mixer's own audio directory.
     */
    public String validateForLoad(String source) {
        if (source == null || source.isBlank()) {
            throw new AudioSourcePolicyException("Audio source is empty");
        }

        String trimmed = source.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            return validateRemoteHttpUrl(trimmed);
        }
        if (lower.startsWith("icy://")) {
            validateRemoteHttpUrl("http://" + trimmed.substring(6));
            return trimmed;
        }
        if (lower.startsWith("file:")) {
            return validateLocalPath(parseFileUri(trimmed));
        }

        try {
            Path path = Path.of(trimmed);
            if (path.isAbsolute()) return validateLocalPath(path);
        } catch (InvalidPathException exception) {
            throw new AudioSourcePolicyException("Audio source contains an invalid path", exception);
        }

        // Service-specific identifiers such as search prefixes are handled by
        // their dedicated Lavaplayer source manager. The safe local manager will
        // still reject a relative file if an opaque identifier happens to name one.
        return trimmed;
    }

    public String validateRemoteHttpUrl(String source) {
        URI uri;
        try {
            uri = new URI(source);
        } catch (URISyntaxException exception) {
            throw new AudioSourcePolicyException("Audio URL is malformed", exception);
        }

        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new AudioSourcePolicyException("Only HTTP and HTTPS audio URLs are allowed");
        }
        if (uri.getUserInfo() != null) {
            throw new AudioSourcePolicyException("Audio URLs must not contain user credentials");
        }
        if (uri.getFragment() != null) {
            throw new AudioSourcePolicyException("Audio URLs must not contain fragments");
        }

        String host = normalizeHost(uri.getHost());
        if (host == null || host.isBlank()) {
            throw new AudioSourcePolicyException("Audio URL does not contain a valid host");
        }
        resolveAndRequirePublic(host, hostResolver);
        return uri.toASCIIString();
    }

    public String validateLocalReference(String source) {
        if (source == null || source.isBlank()) {
            throw new AudioSourcePolicyException("Local audio source is empty");
        }
        String trimmed = source.trim();
        if (trimmed.regionMatches(true, 0, "file:", 0, 5)) {
            return validateLocalPath(parseFileUri(trimmed));
        }
        try {
            return validateLocalPath(Path.of(trimmed));
        } catch (InvalidPathException exception) {
            throw new AudioSourcePolicyException("Local audio path is invalid", exception);
        }
    }

    public static InetAddress[] resolvePublicAddresses(String hostname) throws UnknownHostException {
        return resolveAndRequirePublic(normalizeHost(hostname), InetAddress::getAllByName);
    }

    static boolean isPublicAddress(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return false;
        }

        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address && bytes.length == 4) {
            int first = Byte.toUnsignedInt(bytes[0]);
            int second = Byte.toUnsignedInt(bytes[1]);
            int third = Byte.toUnsignedInt(bytes[2]);
            return first != 0
                    && first != 10
                    && first != 127
                    && !(first == 100 && second >= 64 && second <= 127)
                    && !(first == 169 && second == 254)
                    && !(first == 172 && second >= 16 && second <= 31)
                    && !(first == 192 && second == 0 && (third == 0 || third == 2))
                    && !(first == 192 && second == 88 && third == 99)
                    && !(first == 192 && second == 168)
                    && !(first == 198 && (second == 18 || second == 19))
                    && !(first == 198 && second == 51 && third == 100)
                    && !(first == 203 && second == 0 && third == 113)
                    && first < 224;
        }
        if (address instanceof Inet6Address && bytes.length == 16) {
            int first = Byte.toUnsignedInt(bytes[0]);
            int second = Byte.toUnsignedInt(bytes[1]);
            int third = Byte.toUnsignedInt(bytes[2]);
            int fourth = Byte.toUnsignedInt(bytes[3]);
            boolean globalUnicast = (first & 0xe0) == 0x20;
            boolean teredo = first == 0x20 && second == 0x01 && third == 0 && fourth == 0;
            boolean benchmarking = first == 0x20 && second == 0x01 && third == 0 && fourth == 2;
            boolean documentation = first == 0x20 && second == 0x01 && third == 0x0d && fourth == 0xb8;
            boolean orchid = first == 0x20 && second == 0x01 && third == 0
                    && ((fourth & 0xf0) == 0x10 || (fourth & 0xf0) == 0x20);
            boolean sixToFour = first == 0x20 && second == 0x02;
            return globalUnicast && !teredo && !benchmarking && !documentation
                    && !orchid && !sixToFour;
        }
        return false;
    }

    private String validateLocalPath(Path source) {
        if (!source.isAbsolute()) {
            throw new AudioSourcePolicyException("Relative local audio paths are not allowed");
        }
        try {
            Path allowed = allowedAudioDirectory.toRealPath();
            Path candidate = source.toRealPath();
            if (!candidate.startsWith(allowed) || !Files.isRegularFile(candidate)
                    || !Files.isReadable(candidate)) {
                throw new AudioSourcePolicyException(
                        "Local audio files are restricted to Mixer's audio directory");
            }
            return candidate.toString();
        } catch (AudioSourcePolicyException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new AudioSourcePolicyException("Local audio file is unavailable", exception);
        }
    }

    private static Path parseFileUri(String source) {
        try {
            URI uri = new URI(source);
            if (uri.getAuthority() != null && !uri.getAuthority().isBlank()) {
                throw new AudioSourcePolicyException("Network file URLs are not allowed");
            }
            return Path.of(uri);
        } catch (URISyntaxException | IllegalArgumentException exception) {
            if (exception instanceof AudioSourcePolicyException policyException) {
                throw policyException;
            }
            throw new AudioSourcePolicyException("Local audio URL is malformed", exception);
        }
    }

    private static InetAddress[] resolveAndRequirePublic(String hostname, HostResolver resolver)
            throws AudioSourcePolicyException {
        if (hostname == null || hostname.isBlank()) {
            throw new AudioSourcePolicyException("Audio URL does not contain a valid host");
        }
        InetAddress[] addresses;
        try {
            addresses = resolver.resolve(hostname);
        } catch (UnknownHostException exception) {
            throw new AudioSourcePolicyException("Audio URL host could not be resolved", exception);
        }
        if (addresses == null || addresses.length == 0) {
            throw new AudioSourcePolicyException("Audio URL host resolved to no addresses");
        }
        for (InetAddress address : addresses) {
            if (address == null || !isPublicAddress(address)) {
                throw new AudioSourcePolicyException("Audio URL resolves to a non-public address");
            }
        }
        return addresses;
    }

    private static String normalizeHost(String hostname) {
        if (hostname == null) return null;
        String host = hostname;
        if (host.startsWith("[") && host.endsWith("]")) {
            host = host.substring(1, host.length() - 1);
        }
        if (host.endsWith(".")) host = host.substring(0, host.length() - 1);
        if (host.indexOf(':') >= 0) {
            if (host.indexOf('%') >= 0) {
                throw new AudioSourcePolicyException("Scoped IPv6 audio hosts are not allowed");
            }
            return host.toLowerCase(Locale.ROOT);
        }
        try {
            return IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES);
        } catch (IllegalArgumentException exception) {
            throw new AudioSourcePolicyException("Audio URL host is invalid", exception);
        }
    }

    @FunctionalInterface
    interface HostResolver {
        InetAddress[] resolve(String hostname) throws UnknownHostException;
    }
}
