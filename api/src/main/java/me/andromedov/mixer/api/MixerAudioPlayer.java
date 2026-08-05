package me.andromedov.mixer.api;

import org.bukkit.Location;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface MixerAudioPlayer {
    Location location();
    Set<MixerSpeaker> speakers();
    MixerDsp dsp();
    void load(String... url);

    default void clearAndPlay(String... urls) {
        throw new UnsupportedOperationException("clearAndPlay is not supported by this player");
    }

    default void clearQueue() {
        throw new UnsupportedOperationException("clearQueue is not supported by this player");
    }

    default List<String> queuedSources() {
        return List.of();
    }

    default Optional<MixerTrack> currentTrack() {
        return Optional.empty();
    }

    default boolean isRunning() {
        return true;
    }

    default boolean isPlaying() {
        return currentTrack().isPresent();
    }

    void stop();
}
