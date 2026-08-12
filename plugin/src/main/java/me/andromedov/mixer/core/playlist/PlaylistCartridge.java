package me.andromedov.mixer.core.playlist;

import java.util.List;
import java.util.Objects;

public record PlaylistCartridge(int version, String name, List<PlaylistTrack> tracks) {
    public static final int CURRENT_VERSION = 1;

    public PlaylistCartridge {
        if (version != CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported playlist cartridge version: " + version);
        }
        name = Objects.requireNonNullElse(name, "Mixtape");
        tracks = List.copyOf(Objects.requireNonNull(tracks, "tracks"));
    }

    public static PlaylistCartridge empty(String name) {
        return new PlaylistCartridge(CURRENT_VERSION, name, List.of());
    }

    public PlaylistCartridge withTracks(List<PlaylistTrack> tracks) {
        return new PlaylistCartridge(version, name, tracks);
    }
}
