package me.andromedov.mixer.core.util;

import me.andromedov.mixer.api.playback.MixerPlaybackDecision;
import me.andromedov.mixer.api.playback.MixerPlaybackOrigin;
import me.andromedov.mixer.api.playback.MixerPlaybackRequest;
import me.andromedov.mixer.core.MixerPlugin;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;

public final class PlaybackAuthorization {
    private PlaybackAuthorization() { }

    public static boolean allow(MixerPlaybackOrigin origin, String source, ItemStack disc,
                                Player actor, Location location) {
        MixerPlaybackRequest request = new MixerPlaybackRequest(
                origin,
                source,
                Optional.ofNullable(disc),
                Optional.ofNullable(actor),
                Optional.ofNullable(location)
        );
        MixerPlaybackDecision decision = MixerPlugin.getPlugin().api().playbackPolicies().evaluate(request);
        if (!decision.allowed() && actor != null) {
            decision.denialMessage().ifPresent(actor::sendActionBar);
        }
        return decision.allowed();
    }
}
