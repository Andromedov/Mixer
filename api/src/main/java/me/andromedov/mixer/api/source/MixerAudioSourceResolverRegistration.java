package me.andromedov.mixer.api.source;

import org.bukkit.plugin.Plugin;

public interface MixerAudioSourceResolverRegistration extends AutoCloseable {
    Plugin owner();
    MixerAudioSourceResolver resolver();
    boolean active();

    @Override
    void close();
}
