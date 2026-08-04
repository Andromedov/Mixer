package me.andromedov.mixer.api.source;

/**
 * Resolves addon-owned source identifiers to a Lavaplayer-compatible identifier.
 * Both methods are invoked off the Bukkit main thread and must not call Bukkit APIs
 * that require the server thread.
 */
public interface MixerAudioSourceResolver {
    String id();

    default int priority() {
        return 0;
    }

    boolean supports(String source);

    String resolve(String source) throws Exception;
}
