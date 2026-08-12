package me.andromedov.mixer.core.playlist;

import me.andromedov.mixer.api.MixerTrack;
import me.andromedov.mixer.api.disc.MixerDisc;

import java.util.Objects;

public record PlaylistTrack(
        String source,
        String title,
        String author,
        String uri,
        long durationMillis,
        boolean stream
) {
    public PlaylistTrack {
        source = Objects.requireNonNull(source, "source");
        if (source.isBlank()) throw new IllegalArgumentException("source must not be blank");
        title = Objects.requireNonNullElse(title, "Unknown track");
        author = Objects.requireNonNullElse(author, "Unknown artist");
        uri = Objects.requireNonNullElse(uri, source);
    }

    public static PlaylistTrack fromDisc(MixerDisc disc) {
        return disc.track()
                .map(track -> fromTrack(disc.source(), track))
                .orElseGet(() -> new PlaylistTrack(
                        disc.source(), "Unknown track", "Unknown artist", disc.source(), 0L, false));
    }

    private static PlaylistTrack fromTrack(String source, MixerTrack track) {
        return new PlaylistTrack(source, track.title(), track.author(), track.uri(),
                track.durationMillis(), track.stream());
    }
}
