package me.andromedov.mixer.api.source;

import org.bukkit.plugin.Plugin;

import java.util.Collection;

public interface MixerAudioSourceRegistry {
    MixerAudioSourceResolverRegistration register(Plugin owner, MixerAudioSourceResolver resolver);
    Collection<MixerAudioSourceResolverRegistration> registrations();

    /**
     * Resolves one source through the first supporting resolver. This method may block
     * and must only be called off the Bukkit main thread.
     */
    String resolve(String source) throws MixerAudioSourceResolutionException;
}
