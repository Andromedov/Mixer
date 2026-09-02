package me.andromedov.mixer.core.security;

import org.apache.http.conn.DnsResolver;

import java.net.InetAddress;
import java.net.UnknownHostException;

/** Revalidates every Apache HTTP connection, including redirected requests. */
public final class PublicAddressDnsResolver implements DnsResolver {
    @Override
    public InetAddress[] resolve(String host) throws UnknownHostException {
        try {
            return AudioSourcePolicy.resolvePublicAddresses(host);
        } catch (AudioSourcePolicyException exception) {
            UnknownHostException blocked = new UnknownHostException(exception.getMessage());
            blocked.initCause(exception);
            throw blocked;
        }
    }
}
