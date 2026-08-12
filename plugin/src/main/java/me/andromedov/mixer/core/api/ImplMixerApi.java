package me.andromedov.mixer.core.api;

import me.andromedov.mixer.api.MixerApi;
import me.andromedov.mixer.api.MixerAudioPlayer;
import me.andromedov.mixer.api.addon.MixerAddonManager;
import me.andromedov.mixer.api.disc.MixerDiscService;
import me.andromedov.mixer.api.playback.MixerPlaybackPolicyRegistry;
import me.andromedov.mixer.api.playlist.MixerPlaylistService;
import me.andromedov.mixer.api.source.MixerAudioSourceRegistry;
import me.andromedov.mixer.core.MixerPlugin;
import me.andromedov.mixer.core.audio.EntityMixerAudioPlayer;
import me.andromedov.mixer.core.audio.IMixerAudioPlayer;
import me.andromedov.mixer.core.util.MixerScheduler;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public final class ImplMixerApi implements MixerApi {
    private final MixerPlugin plugin;
    private final ImplMixerAudioSourceRegistry sources;
    private final ImplMixerDiscService discs;
    private final ImplMixerPlaybackPolicyRegistry playbackPolicies;
    private final ImplMixerAddonManager addons;

    public ImplMixerApi(MixerPlugin plugin) {
        this.plugin = plugin;
        this.sources = new ImplMixerAudioSourceRegistry();
        this.discs = new ImplMixerDiscService(plugin, sources);
        this.playbackPolicies = new ImplMixerPlaybackPolicyRegistry(plugin);
        this.addons = new ImplMixerAddonManager(plugin, this, sources, playbackPolicies);
    }

    @Override
    public String version() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public IMixerAudioPlayer createPlayer(Location location) {
        Location blockLocation = blockLocation(location);
        MixerScheduler.requireOwned(blockLocation, "create a locational audio player");
        if (plugin.playerHashMap().containsKey(blockLocation)) {
            throw new IllegalStateException("Player at this location already exists");
        }
        return new IMixerAudioPlayer(blockLocation);
    }

    @Override
    public MixerAudioPlayer getOrCreatePlayer(Location location) {
        Location blockLocation = blockLocation(location);
        MixerScheduler.requireOwned(blockLocation, "create a locational audio player");
        IMixerAudioPlayer existing = plugin.playerHashMap().get(blockLocation);
        return existing != null ? existing : createPlayer(blockLocation);
    }

    @Override
    public Optional<MixerAudioPlayer> findPlayer(Location location) {
        return Optional.ofNullable(plugin.playerHashMap().get(blockLocation(location)));
    }

    @Override
    public Collection<MixerAudioPlayer> players() {
        List<MixerAudioPlayer> players = new java.util.ArrayList<>(plugin.playerHashMap().values());
        players.addAll(plugin.getPortablePlayerMap().values());
        return List.copyOf(players);
    }

    @Override
    public boolean stopPlayer(Location location) {
        Location blockLocation = blockLocation(location);
        MixerScheduler.requireOwned(blockLocation, "stop a locational audio player");
        IMixerAudioPlayer player = plugin.playerHashMap().get(blockLocation);
        if (player == null) return false;
        player.stop();
        return true;
    }

    @Override
    public MixerAudioPlayer createPortablePlayer(Player owner) {
        if (owner == null) throw new IllegalArgumentException("Owner must not be null");
        MixerScheduler.requireOwned(owner, "create a portable audio player");
        EntityMixerAudioPlayer existing = plugin.getPortablePlayerMap().remove(owner.getUniqueId());
        if (existing != null) existing.stop();
        EntityMixerAudioPlayer created = new EntityMixerAudioPlayer(owner);
        plugin.getPortablePlayerMap().put(owner.getUniqueId(), created);
        return created;
    }

    @Override
    public Optional<MixerAudioPlayer> findPortablePlayer(Player owner) {
        if (owner == null) return Optional.empty();
        return Optional.ofNullable(plugin.getPortablePlayerMap().get(owner.getUniqueId()));
    }

    @Override
    public boolean stopPortablePlayer(Player owner) {
        if (owner == null) return false;
        MixerScheduler.requireOwned(owner, "stop a portable audio player");
        EntityMixerAudioPlayer player = plugin.getPortablePlayerMap().get(owner.getUniqueId());
        if (player == null) return false;
        player.stop();
        return true;
    }

    @Override
    public MixerAddonManager addons() {
        return addons;
    }

    @Override
    public MixerAudioSourceRegistry sources() {
        return sources;
    }

    @Override
    public MixerDiscService discs() {
        return discs;
    }

    @Override
    public MixerPlaylistService playlists() {
        return plugin.getPlaylistCartridges();
    }

    @Override
    public MixerPlaybackPolicyRegistry playbackPolicies() {
        return playbackPolicies;
    }

    @Override
    public IMixerAudioPlayer getMixerAudioPlayer(Location location) {
        return plugin.playerHashMap().get(blockLocation(location));
    }

    public ImplMixerAddonManager addonManager() {
        return addons;
    }

    public void shutdown() {
        addons.shutdown();
        discs.shutdown();
    }

    private static Location blockLocation(Location location) {
        if (location == null || location.getWorld() == null) {
            throw new IllegalArgumentException("Location and its world must not be null");
        }
        return new Location(location.getWorld(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }
}
