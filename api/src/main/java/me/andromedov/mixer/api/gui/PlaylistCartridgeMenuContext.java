package me.andromedov.mixer.api.gui;

import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.UUID;

/** Read-only cartridge state available while its editor is rendered. */
public record PlaylistCartridgeMenuContext(
        Player player,
        UUID cartridgeId,
        String name,
        int trackCount,
        int maxTracks
) {
    public PlaylistCartridgeMenuContext {
        player = Objects.requireNonNull(player, "player");
        cartridgeId = Objects.requireNonNull(cartridgeId, "cartridgeId");
        name = Objects.requireNonNull(name, "name");
        if (trackCount < 0 || maxTracks < 1 || trackCount > maxTracks) {
            throw new IllegalArgumentException("Invalid cartridge track count");
        }
    }
}
