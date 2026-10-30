package me.andromedov.mixer.api.gui;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Read-only DSP state available while the DSP editor is rendered. */
public record DspMenuContext(
        Player player,
        DspMenuTargetType targetType,
        @Nullable Location location,
        @Nullable UUID speakerId,
        double gain,
        float highPassFrequency,
        float lowPassFrequency,
        boolean flangerEnabled
) {
    public DspMenuContext {
        player = Objects.requireNonNull(player, "player");
        targetType = Objects.requireNonNull(targetType, "targetType");
        if (targetType == DspMenuTargetType.JUKEBOX) {
            location = Objects.requireNonNull(location, "location").clone();
            if (speakerId != null) throw new IllegalArgumentException("Jukebox context cannot have a speaker id");
        } else {
            speakerId = Objects.requireNonNull(speakerId, "speakerId");
            if (location != null) throw new IllegalArgumentException("Portable speaker context cannot have a location");
        }
    }

    @Override
    public @Nullable Location location() {
        return location == null ? null : location.clone();
    }

    public Optional<Location> optionalLocation() {
        return Optional.ofNullable(location());
    }

    public Optional<UUID> optionalSpeakerId() {
        return Optional.ofNullable(speakerId);
    }
}
