package me.andromedov.mixer.api.playback;

import org.bukkit.plugin.Plugin;

import java.util.Collection;

public interface MixerPlaybackPolicyRegistry {
    MixerPlaybackPolicyRegistration register(Plugin owner, MixerPlaybackPolicy policy);
    Collection<MixerPlaybackPolicyRegistration> registrations();

    /** Evaluates every active policy by descending priority on the Bukkit main thread. */
    MixerPlaybackDecision evaluate(MixerPlaybackRequest request);
}
