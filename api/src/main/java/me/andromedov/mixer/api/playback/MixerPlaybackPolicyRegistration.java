package me.andromedov.mixer.api.playback;

import org.bukkit.plugin.Plugin;

public interface MixerPlaybackPolicyRegistration extends AutoCloseable {
    Plugin owner();
    MixerPlaybackPolicy policy();
    boolean active();

    @Override
    void close();
}
