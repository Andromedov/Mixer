package me.andromedov.mixer.api.playlist;

import java.util.List;
import java.util.Objects;

/** Immutable playlist stored on a Music Cartridge. */
public record MixerPlaylist(int version, String name, List<MixerPlaylistTrack> tracks) {
    public static final int CURRENT_VERSION = 1;

    public MixerPlaylist {
        if (version != CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported Mixer playlist version: " + version);
        }
        name = Objects.requireNonNullElse(name, "Mixtape");
        tracks = List.copyOf(Objects.requireNonNull(tracks, "tracks"));
    }

    public MixerPlaylist(String name, List<MixerPlaylistTrack> tracks) {
        this(CURRENT_VERSION, name, tracks);
    }

    public static MixerPlaylist empty(String name) {
        return new MixerPlaylist(name, List.of());
    }

    public MixerPlaylist withTracks(List<MixerPlaylistTrack> tracks) {
        return new MixerPlaylist(version, name, tracks);
    }
}
