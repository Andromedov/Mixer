package me.andromedov.mixer.core.playlist;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import me.andromedov.mixer.api.playlist.MixerPlaylist;
import me.andromedov.mixer.api.playlist.MixerPlaylistService;
import me.andromedov.mixer.api.playlist.MixerPlaylistTrack;
import me.andromedov.mixer.core.MixerPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

public final class PlaylistCartridgeService implements MixerPlaylistService {
    private static final int MAX_SERIALIZED_LENGTH = 64 * 1024;
    private static final int MAX_SOURCE_LENGTH = 4096;
    private static final Gson GSON = new Gson();
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final MixerPlugin plugin;
    private final NamespacedKey markerKey;
    private final NamespacedKey idKey;
    private final NamespacedKey dataKey;

    public PlaylistCartridgeService(MixerPlugin plugin) {
        this.plugin = plugin;
        this.markerKey = new NamespacedKey(plugin, "playlist_cartridge");
        this.idKey = new NamespacedKey(plugin, "playlist_cartridge_id");
        this.dataKey = new NamespacedKey(plugin, "playlist_cartridge_data");
    }

    @Override
    public ItemStack createCartridge() {
        requireMainThread("create a playlist cartridge");
        Material material = Material.getMaterial(plugin.getPlaylistCartridgeMaterial());
        if (material == null || material.isAir()) {
            material = Material.MUSIC_DISC_11;
            plugin.logDebug(Level.WARNING, "Invalid playlist cartridge material. Using MUSIC_DISC_11.", null);
        }

        ItemStack item = new ItemStack(material);
        UUID id = UUID.randomUUID();
        writeData(item, id, MixerPlaylist.empty(defaultName()));
        return item;
    }

    @Override
    public boolean isCartridge(ItemStack item) {
        requireMainThread("inspect a playlist cartridge");
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(markerKey, PersistentDataType.BYTE);
    }

    @Override
    public Optional<UUID> id(ItemStack item) {
        requireMainThread("read a playlist cartridge ID");
        if (!isCartridge(item)) return Optional.empty();
        String value = item.getItemMeta().getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
        try {
            return Optional.of(UUID.fromString(value));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<MixerPlaylist> read(ItemStack item) {
        requireMainThread("read a playlist cartridge");
        if (!isCartridge(item)) return Optional.empty();
        String json = item.getItemMeta().getPersistentDataContainer().get(dataKey, PersistentDataType.STRING);
        if (json == null || json.isBlank() || json.length() > MAX_SERIALIZED_LENGTH) return Optional.empty();
        try {
            MixerPlaylist cartridge = GSON.fromJson(json, MixerPlaylist.class);
            if (cartridge == null || cartridge.tracks().size() > plugin.getPlaylistCartridgeMaxTracks()) {
                return Optional.empty();
            }
            for (MixerPlaylistTrack track : cartridge.tracks()) {
                if (track.source().length() > MAX_SOURCE_LENGTH) return Optional.empty();
            }
            return Optional.of(cartridge);
        } catch (JsonParseException | IllegalArgumentException | NullPointerException exception) {
            plugin.logDebug(Level.WARNING, "Failed to read playlist cartridge data", exception);
            return Optional.empty();
        }
    }

    @Override
    public boolean write(ItemStack item, UUID id, MixerPlaylist cartridge) {
        requireMainThread("write a playlist cartridge");
        if (id == null || !isCartridge(item) || !id.equals(id(item).orElse(null))) return false;
        return writeData(item, id, cartridge);
    }

    private boolean writeData(ItemStack item, UUID id, MixerPlaylist cartridge) {
        if (item == null || id == null || cartridge == null
                || cartridge.tracks().size() > plugin.getPlaylistCartridgeMaxTracks()) return false;
        String json = GSON.toJson(cartridge);
        if (json.length() > MAX_SERIALIZED_LENGTH) return false;

        item.editMeta(meta -> {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(markerKey, PersistentDataType.BYTE, (byte) 1);
            pdc.set(idKey, PersistentDataType.STRING, id.toString());
            pdc.set(dataKey, PersistentDataType.STRING, json);
            meta.displayName(MM.deserialize(plugin.getLocalizationManager()
                    .getMessage("playlist.cartridge_item_name"))
                    .decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(MM.deserialize(plugin.getLocalizationManager().getMessage(
                    "playlist.cartridge_tracks", cartridge.tracks().size(), plugin.getPlaylistCartridgeMaxTracks()))
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("ID: " + id, NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
        });
        return true;
    }

    @Override
    public int maxTracks() {
        return plugin.getPlaylistCartridgeMaxTracks();
    }

    private String defaultName() {
        return MM.stripTags(plugin.getLocalizationManager().getMessage("playlist.cartridge_item_name"));
    }

    private static void requireMainThread(String action) {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Must " + action + " on the Bukkit main thread");
        }
    }
}
