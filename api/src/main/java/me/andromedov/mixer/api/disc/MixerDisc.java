package me.andromedov.mixer.api.disc;

import me.andromedov.mixer.api.MixerTrack;

import java.util.Objects;
import java.util.Optional;

/**
 * Stable source stored on a Mixer disc and, when available, the probed track metadata.
 */
public record MixerDisc(String source, Optional<MixerTrack> track) {
    public MixerDisc {
        Objects.requireNonNull(source, "source");
        if (source.isBlank()) throw new IllegalArgumentException("source must not be blank");
        track = Objects.requireNonNull(track, "track");
    }

    public MixerDisc(String source, MixerTrack track) {
        this(source, Optional.of(Objects.requireNonNull(track, "track")));
    }

    public static MixerDisc sourceOnly(String source) {
        return new MixerDisc(source, Optional.empty());
    }
}
