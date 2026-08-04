package me.andromedov.mixer.api.playback;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;
import java.util.Optional;

/** Immutable snapshot supplied to playback policies on the Bukkit main thread. */
public final class MixerPlaybackRequest {
    private final MixerPlaybackOrigin origin;
    private final String source;
    private final ItemStack disc;
    private final Player actor;
    private final Location location;

    public MixerPlaybackRequest(
            MixerPlaybackOrigin origin,
            String source,
            Optional<ItemStack> disc,
            Optional<Player> actor,
            Optional<Location> location
    ) {
        this.origin = Objects.requireNonNull(origin, "origin");
        this.source = Objects.requireNonNull(source, "source");
        if (source.isBlank()) throw new IllegalArgumentException("source must not be blank");
        this.disc = Objects.requireNonNull(disc, "disc").map(ItemStack::clone).orElse(null);
        this.actor = Objects.requireNonNull(actor, "actor").orElse(null);
        this.location = Objects.requireNonNull(location, "location").map(Location::clone).orElse(null);
    }

    public MixerPlaybackOrigin origin() {
        return origin;
    }

    public String source() {
        return source;
    }

    public Optional<ItemStack> disc() {
        return Optional.ofNullable(disc).map(ItemStack::clone);
    }

    public Optional<Player> actor() {
        return Optional.ofNullable(actor);
    }

    public Optional<Location> location() {
        return Optional.ofNullable(location).map(Location::clone);
    }
}
