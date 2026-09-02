package me.andromedov.mixer.api.gui;

import me.andromedov.mixer.api.playlist.MixerPlaylistTrack;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

/** Per-item render data. Track index is zero-based and is -1 for non-track controls. */
public record PlaylistCartridgeMenuItemContext(
        PlaylistCartridgeMenuContext menu,
        PlaylistCartridgeMenuElement element,
        int slot,
        int trackIndex,
        @Nullable MixerPlaylistTrack track
) {
    public PlaylistCartridgeMenuItemContext {
        menu = Objects.requireNonNull(menu, "menu");
        element = Objects.requireNonNull(element, "element");
        if (slot < 0) throw new IllegalArgumentException("slot must not be negative");
        if (trackIndex < -1) throw new IllegalArgumentException("trackIndex must be -1 or greater");
    }

    public Optional<MixerPlaylistTrack> optionalTrack() {
        return Optional.ofNullable(track);
    }
}
