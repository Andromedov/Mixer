package me.andromedov.mixer.api.gui;

import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.UUID;

/** Read-only portable speaker state available while its menu is rendered. */
public record PortableSpeakerMenuContext(
        Player player,
        UUID speakerId,
        boolean playing,
        boolean paused,
        boolean shuffle,
        PortableSpeakerRepeatMode repeatMode,
        boolean mediaPresent
) {
    public PortableSpeakerMenuContext {
        player = Objects.requireNonNull(player, "player");
        speakerId = Objects.requireNonNull(speakerId, "speakerId");
        repeatMode = Objects.requireNonNull(repeatMode, "repeatMode");
    }
}
