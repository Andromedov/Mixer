package me.andromedov.mixer.core.playlist;

import com.google.gson.Gson;
import me.andromedov.mixer.api.playlist.MixerPlaylist;
import me.andromedov.mixer.api.playlist.MixerPlaylistTrack;
import me.andromedov.mixer.core.MixerPlugin;
import me.andromedov.mixer.core.util.LocalizationManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlaylistCartridgeServiceTest {
    private static final Gson GSON = new Gson();
    private static final int MAX_TRACKS = 18;

    private MixerPlugin plugin;
    private PlaylistCartridgeService service;
    private NamespacedKey markerKey;
    private NamespacedKey idKey;
    private NamespacedKey dataKey;
    private NamespacedKey managedAppearanceKey;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        plugin = mock(MixerPlugin.class);
        LocalizationManager localization = mock(LocalizationManager.class);

        when(plugin.getName()).thenReturn("Mixer");
        when(plugin.getPlaylistCartridgeMaterial()).thenReturn("MUSIC_DISC_11");
        when(plugin.getPlaylistCartridgeMaxTracks()).thenReturn(MAX_TRACKS);
        when(plugin.getLocalizationManager()).thenReturn(localization);
        when(localization.getMessage("playlist.cartridge_item_name")).thenReturn("Music Cartridge");
        when(localization.getMessage(eq("playlist.cartridge_tracks"), any(), any()))
                .thenAnswer(invocation -> "Tracks: " + invocation.getArgument(1)
                        + "/" + invocation.getArgument(2));

        service = new PlaylistCartridgeService(plugin);
        markerKey = new NamespacedKey(plugin, "playlist_cartridge");
        idKey = new NamespacedKey(plugin, "playlist_cartridge_id");
        dataKey = new NamespacedKey(plugin, "playlist_cartridge_data");
        managedAppearanceKey = new NamespacedKey(plugin, "playlist_cartridge_managed_appearance");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void createsCartridgeWithMixerManagedAppearance() {
        ItemStack item = service.createCartridge();

        assertEquals(managedName("Music Cartridge"), item.getItemMeta().displayName());
        assertEquals(List.of(tracksLore(0)), item.getItemMeta().lore());
        assertTrue(item.getItemMeta().getPersistentDataContainer()
                .has(managedAppearanceKey, PersistentDataType.BYTE));
        assertTrue(service.id(item).isPresent());
        assertEquals(MixerPlaylist.empty("Music Cartridge"), service.read(item).orElseThrow());
    }

    @Test
    void preservesCustomAppearanceWhenInitializingAndWritingMarkerOnlyCartridge() {
        Component customName = Component.text("Custom cassette", NamedTextColor.GOLD);
        List<Component> customLore = List.of(
                Component.text("Custom lore"),
                Component.text("ID: this is user content"));
        ItemStack item = new ItemStack(Material.PAPER);
        item.editMeta(meta -> {
            meta.getPersistentDataContainer().set(markerKey, PersistentDataType.BYTE, (byte) 1);
            meta.displayName(customName);
            meta.lore(customLore);
            meta.setCustomModelData(42);
        });

        UUID id = service.ensureInitialized(item).orElseThrow();
        MixerPlaylist updated = new MixerPlaylist("Renamed", List.of(track()));

        assertTrue(service.write(item, id, updated));
        assertEquals(Material.PAPER, item.getType());
        assertEquals(customName, item.getItemMeta().displayName());
        assertEquals(customLore, item.getItemMeta().lore());
        assertEquals(42, item.getItemMeta().getCustomModelData());
        assertFalse(item.getItemMeta().getPersistentDataContainer()
                .has(managedAppearanceKey, PersistentDataType.BYTE));
        assertEquals(updated, service.read(item).orElseThrow());
    }

    @Test
    void migratesExactLegacyAppearanceAndKeepsItManaged() {
        UUID id = UUID.randomUUID();
        MixerPlaylist previous = MixerPlaylist.empty("Old name");
        ItemStack item = legacyCartridge(id, previous);
        MixerPlaylist updated = new MixerPlaylist("New name", List.of(track()));

        assertTrue(service.write(item, id, updated));

        assertEquals(managedName("New name"), item.getItemMeta().displayName());
        assertEquals(List.of(tracksLore(1)), item.getItemMeta().lore());
        assertTrue(item.getItemMeta().getPersistentDataContainer()
                .has(managedAppearanceKey, PersistentDataType.BYTE));
        assertEquals(updated, service.read(item).orElseThrow());
    }

    @Test
    void doesNotMigrateLegacyDataWhenAppearanceWasCustomized() {
        UUID id = UUID.randomUUID();
        MixerPlaylist previous = MixerPlaylist.empty("Old name");
        ItemStack item = legacyCartridge(id, previous);
        Component customName = Component.text("Resource-pack cassette", NamedTextColor.AQUA);
        item.editMeta(meta -> meta.displayName(customName));

        assertTrue(service.write(item, id, new MixerPlaylist("New name", List.of(track()))));

        assertEquals(customName, item.getItemMeta().displayName());
        assertEquals(List.of(tracksLore(0), legacyIdLore(id)), item.getItemMeta().lore());
        assertFalse(item.getItemMeta().getPersistentDataContainer()
                .has(managedAppearanceKey, PersistentDataType.BYTE));
    }

    private ItemStack legacyCartridge(UUID id, MixerPlaylist playlist) {
        ItemStack item = new ItemStack(Material.MUSIC_DISC_11);
        item.editMeta(meta -> {
            meta.getPersistentDataContainer().set(markerKey, PersistentDataType.BYTE, (byte) 1);
            meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, id.toString());
            meta.getPersistentDataContainer().set(dataKey, PersistentDataType.STRING, GSON.toJson(playlist));
            meta.displayName(managedName(playlist.name()));
            meta.lore(List.of(tracksLore(playlist.tracks().size()), legacyIdLore(id)));
        });
        return item;
    }

    private static MixerPlaylistTrack track() {
        return new MixerPlaylistTrack("https://example.test/audio", "Track", "Artist",
                "https://example.test/audio", 1_000L, false);
    }

    private static Component managedName(String name) {
        return Component.text(name, NamedTextColor.LIGHT_PURPLE)
                .decoration(TextDecoration.ITALIC, false);
    }

    private static Component tracksLore(int tracks) {
        return Component.text("Tracks: " + tracks + "/" + MAX_TRACKS)
                .decoration(TextDecoration.ITALIC, false);
    }

    private static Component legacyIdLore(UUID id) {
        return Component.text("ID: " + id, NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false);
    }
}
