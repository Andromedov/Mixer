package me.andromedov.mixer.core.api;

import me.andromedov.mixer.api.MixerApi;
import me.andromedov.mixer.api.MixerAudioPlayer;
import me.andromedov.mixer.api.addon.MixerAddonManager;
import me.andromedov.mixer.api.source.MixerAudioSourceRegistry;
import me.andromedov.mixer.core.MixerPlugin;
import me.andromedov.mixer.core.audio.EntityMixerAudioPlayer;
import me.andromedov.mixer.core.audio.IMixerAudioPlayer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public final class ImplMixerApi implements MixerApi {
    private final MixerPlugin plugin;
    private final ImplMixerAudioSourceRegistry sources;
    private final ImplMixerAddonManager addons;

    public ImplMixerApi(MixerPlugin plugin) {
        this.plugin = plugin;
        this.sources = new ImplMixerAudioSourceRegistry();
        this.addons = new ImplMixerAddonManager(plugin, this, sources);
    }

    @Override
    public String version() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public IMixerAudioPlayer createPlayer(Location location) {
        requireMainThread("create a locational audio player");
        Location blockLocation = blockLocation(location);
        if (plugin.playerHashMap().containsKey(blockLocation)) {
            throw new IllegalStateException("Player at this location already exists");
        }
        return new IMixerAudioPlayer(blockLocation);
    }

    @Override
    public MixerAudioPlayer getOrCreatePlayer(Location location) {
        requireMainThread("create a locational audio player");
        Location blockLocation = blockLocation(location);
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
        requireMainThread("stop a locational audio player");
        IMixerAudioPlayer player = plugin.playerHashMap().get(blockLocation(location));
        if (player == null) return false;
        player.stop();
        return true;
    }

    @Override
    public MixerAudioPlayer createPortablePlayer(Player owner) {
        requireMainThread("create a portable audio player");
        if (owner == null) throw new IllegalArgumentException("Owner must not be null");
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
        requireMainThread("stop a portable audio player");
        if (owner == null) return false;
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
    public IMixerAudioPlayer getMixerAudioPlayer(Location location) {
        return plugin.playerHashMap().get(blockLocation(location));
    }

    public ImplMixerAddonManager addonManager() {
        return addons;
    }

    public void shutdown() {
        addons.shutdown();
    }

    private static Location blockLocation(Location location) {
        if (location == null || location.getWorld() == null) {
            throw new IllegalArgumentException("Location and its world must not be null");
        }
        return location.getBlock().getLocation();
    }

    private static void requireMainThread(String action) {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Must " + action + " on the Bukkit main thread");
        }
    }
}
