package me.andromedov.mixer.core.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AudioSourcePolicyTest {
    private static final InetAddress PUBLIC_ADDRESS = address(1, 1, 1, 1);
    private static final InetAddress PRIVATE_ADDRESS = address(10, 0, 0, 1);

    @TempDir
    Path temporaryDirectory;

    @Test
    void acceptsHttpUrlOnlyWhenEveryResolvedAddressIsPublic() throws Exception {
        Path audioDirectory = Files.createDirectory(temporaryDirectory.resolve("audio"));
        AudioSourcePolicy publicPolicy = new AudioSourcePolicy(audioDirectory,
                hostname -> new InetAddress[]{PUBLIC_ADDRESS});
        AudioSourcePolicy mixedPolicy = new AudioSourcePolicy(audioDirectory,
                hostname -> new InetAddress[]{PUBLIC_ADDRESS, PRIVATE_ADDRESS});

        assertEquals("https://media.example.test/song.mp3?quality=high",
                publicPolicy.validateRemoteHttpUrl(
                        "https://media.example.test/song.mp3?quality=high"));
        assertThrows(AudioSourcePolicyException.class,
                () -> mixedPolicy.validateRemoteHttpUrl("https://media.example.test/song.mp3"));
    }

    @Test
    void rejectsCredentialsFragmentsAndNonHttpSchemes() throws Exception {
        Path audioDirectory = Files.createDirectory(temporaryDirectory.resolve("audio"));
        AudioSourcePolicy policy = new AudioSourcePolicy(audioDirectory,
                hostname -> new InetAddress[]{PUBLIC_ADDRESS});

        assertThrows(AudioSourcePolicyException.class,
                () -> policy.validateRemoteHttpUrl("https://user:secret@example.test/audio"));
        assertThrows(AudioSourcePolicyException.class,
                () -> policy.validateRemoteHttpUrl("https://example.test/audio#fragment"));
        assertThrows(AudioSourcePolicyException.class,
                () -> policy.validateRemoteHttpUrl("ftp://example.test/audio"));
    }

    @Test
    void classifiesPrivateReservedAndPublicAddresses() {
        assertFalse(AudioSourcePolicy.isPublicAddress(address(127, 0, 0, 1)));
        assertFalse(AudioSourcePolicy.isPublicAddress(address(169, 254, 169, 254)));
        assertFalse(AudioSourcePolicy.isPublicAddress(address(172, 16, 0, 1)));
        assertFalse(AudioSourcePolicy.isPublicAddress(address(192, 168, 1, 1)));
        assertFalse(AudioSourcePolicy.isPublicAddress(address(100, 64, 0, 1)));
        assertFalse(AudioSourcePolicy.isPublicAddress(address(192, 0, 2, 1)));
        assertFalse(AudioSourcePolicy.isPublicAddress(address(224, 0, 0, 1)));
        assertFalse(AudioSourcePolicy.isPublicAddress(ipv6("2001:db8::1")));
        assertFalse(AudioSourcePolicy.isPublicAddress(ipv6("2002:0a00:1::1")));
        assertTrue(AudioSourcePolicy.isPublicAddress(PUBLIC_ADDRESS));
        assertTrue(AudioSourcePolicy.isPublicAddress(ipv6("2606:4700:4700::1111")));
    }

    @Test
    void permitsOnlyCanonicalFilesInsideMixerAudioDirectory() throws Exception {
        Path audioDirectory = Files.createDirectory(temporaryDirectory.resolve("audio"));
        Path allowedFile = Files.writeString(audioDirectory.resolve("track.mp3"), "audio");
        Path outsideFile = Files.writeString(temporaryDirectory.resolve("secret.txt"), "secret");
        AudioSourcePolicy policy = new AudioSourcePolicy(audioDirectory,
                hostname -> new InetAddress[]{PUBLIC_ADDRESS});

        assertEquals(allowedFile.toRealPath().toString(),
                policy.validateForLoad(allowedFile.toString()));
        assertEquals(allowedFile.toRealPath().toString(),
                policy.validateForLoad(allowedFile.toUri().toString()));
        assertThrows(AudioSourcePolicyException.class,
                () -> policy.validateForLoad(outsideFile.toString()));
        assertThrows(AudioSourcePolicyException.class,
                () -> policy.validateLocalReference("relative-file.mp3"));
    }

    @Test
    void blocksLoopbackAtConnectionTime() {
        assertThrows(AudioSourcePolicyException.class,
                () -> AudioSourcePolicy.resolvePublicAddresses("127.0.0.1"));
        assertThrows(AudioSourcePolicyException.class,
                () -> AudioSourcePolicy.resolvePublicAddresses("[::1]"));
    }

    private static InetAddress address(int first, int second, int third, int fourth) {
        try {
            return InetAddress.getByAddress(new byte[]{
                    (byte) first, (byte) second, (byte) third, (byte) fourth});
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private static InetAddress ipv6(String value) {
        try {
            return InetAddress.getByName(value);
        } catch (UnknownHostException exception) {
            throw new AssertionError(exception);
        }
    }
}
