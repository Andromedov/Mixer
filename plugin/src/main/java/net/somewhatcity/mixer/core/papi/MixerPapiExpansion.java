package net.somewhatcity.mixer.core.papi;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.somewhatcity.mixer.core.MixerPlugin;
import net.somewhatcity.mixer.core.audio.EntityMixerAudioPlayer;
import net.somewhatcity.mixer.core.audio.IMixerAudioPlayer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class MixerPapiExpansion extends PlaceholderExpansion {
    private final MixerPlugin plugin;

    public MixerPapiExpansion(MixerPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "mixer";
    }

    @Override
    public @NotNull String getAuthor() {
        return String.join(", ", plugin.getPluginMeta().getAuthors());
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) return "";

        AudioTrack track = resolveTrack(player);

        // %mixer_status%
        if (params.equalsIgnoreCase("status")) {
            return track != null ? "playing" : "stopped";
        }

        if (track == null) return "";

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

        // %mixer_source_type%
        // @return "speaker", "target_block", or "nearby"
        if (params.equalsIgnoreCase("source_type")) {
            return resolveSourceType(player);
        }

        return null;
    }

    /**
     * Smart resolution of the audio track relevant to the player.
     * Priority:
     * 1. Portable Speaker (Self)
     * 2. Targeted Jukebox (Looking at)
     * 3. Nearest Active Jukebox (Within 32 blocks)
     */
    private AudioTrack resolveTrack(Player player) {
        // Check Portable Speaker
        EntityMixerAudioPlayer playerAudio = plugin.getPortablePlayerMap().get(player.getUniqueId());
        if (playerAudio != null) {
            AudioTrack track = playerAudio.getPlayingTrack();
            if (track != null) return track;
        }

        // Check Target Block
        Block targetBlock = player.getTargetBlockExact(6);
        if (targetBlock != null && targetBlock.getType() == Material.JUKEBOX) {
            IMixerAudioPlayer jukeboxPlayer = plugin.playerHashMap().get(targetBlock.getLocation());
            if (jukeboxPlayer != null) {
                AudioTrack track = jukeboxPlayer.getPlayingTrack();
                if (track != null) return track;
            }
        }

        // Check Nearest Jukebox (within 32 blocks)
        return getNearestJukeboxTrack(player);
    }

    private String resolveSourceType(Player player) {
        if (plugin.getPortablePlayerMap().containsKey(player.getUniqueId())) return "portable";

        Block targetBlock = player.getTargetBlockExact(6);
        if (targetBlock != null && targetBlock.getType() == Material.JUKEBOX) {
            if (plugin.playerHashMap().containsKey(targetBlock.getLocation())) return "target_block";
        }

        if (getNearestJukeboxTrack(player) != null) return "nearby";

        return "none";
    }

    private AudioTrack getNearestJukeboxTrack(Player player) {
        Location pLoc = player.getLocation();
        double minDistanceSq = 32 * 32; // 32 blocks radius
        IMixerAudioPlayer nearest = null;

        for (IMixerAudioPlayer audioPlayer : plugin.playerHashMap().values()) {
            if (!audioPlayer.location().getWorld().equals(pLoc.getWorld())) continue;

            double distSq = audioPlayer.location().distanceSquared(pLoc);
            if (distSq < minDistanceSq) {
                if (audioPlayer.getPlayingTrack() != null) {
                    minDistanceSq = distSq;
                    nearest = audioPlayer;
                }
            }
        }

        return nearest != null ? nearest.getPlayingTrack() : null;
    }
}