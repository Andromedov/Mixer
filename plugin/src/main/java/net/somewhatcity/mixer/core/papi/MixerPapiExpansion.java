package net.somewhatcity.mixer.core.papi;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.somewhatcity.mixer.core.MixerPlugin;
import net.somewhatcity.mixer.core.audio.EntityMixerAudioPlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class MixerPapiExpansion extends PlaceholderExpansion {
    private final MixerPlugin plugin;

    public MixerPapiExpansion(MixerPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "";
    }

    @Override
    public @NotNull String getAuthor() {
        return String.join(", ", plugin.getDescription().getAuthors());
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) return "";

        // Check if a player is using a portable speaker
        EntityMixerAudioPlayer playerAudio = plugin.getPortablePlayerMap().get(player.getUniqueId());

        if (playerAudio != null) return "";

        AudioTrack track = playerAudio.getPlayingTrack();
        if (track == null) return "";

        // %mixer_status%
        if (params.equalsIgnoreCase("status")) {
            return track == null ? "stopped" : "playing";
        }

        // %mixer_current_song%
        if (params.equalsIgnoreCase("current_song")) {
            return track.getInfo().title;
        }

        // %mixer_current_artist%
        if (params.equalsIgnoreCase("current_artist")) {
            return track.getInfo().author;
        }

        // %mixer_current_url%
        if (params.equalsIgnoreCase("current_url")) {
            return track.getInfo().uri;
        }

        return null;
    }
}
