package me.andromedov.mixer.api.playlist;

import me.andromedov.mixer.api.MixerTrack;
import me.andromedov.mixer.api.disc.MixerDisc;

import java.util.Objects;

/** A stable audio source and its display metadata as stored in a Mixer playlist. */
public record MixerPlaylistTrack(
        String source,
        String title,
        String author,
        String uri,
        long durationMillis,
        boolean stream
) {
    public MixerPlaylistTrack {
        source = Objects.requireNonNull(source, "source");
        if (source.isBlank()) throw new IllegalArgumentException("source must not be blank");
        title = Objects.requireNonNullElse(title, "Unknown track");
        author = Objects.requireNonNullElse(author, "Unknown artist");
        uri = Objects.requireNonNullElse(uri, source);
    }

    public static MixerPlaylistTrack fromDisc(MixerDisc disc) {
        Objects.requireNonNull(disc, "disc");
        return disc.track()
                .map(track -> fromTrack(disc.source(), track))
                .orElseGet(() -> new MixerPlaylistTrack(
                        disc.source(), "Unknown track", "Unknown artist", disc.source(), 0L, false));
    }

    private static MixerPlaylistTrack fromTrack(String source, MixerTrack track) {
        return new MixerPlaylistTrack(source, track.title(), track.author(), track.uri(),
                track.durationMillis(), track.stream());
    }
}
