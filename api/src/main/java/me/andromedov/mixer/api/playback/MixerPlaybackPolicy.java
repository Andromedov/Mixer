package me.andromedov.mixer.api.playback;

/**
 * Synchronous authorization hook for physical Mixer disc playback. Policies run on
 * the Bukkit main thread and must not perform network, database, or Discord I/O.
 */
public interface MixerPlaybackPolicy {
    String id();

    default int priority() {
        return 0;
    }

    MixerPlaybackDecision evaluate(MixerPlaybackRequest request);
}
