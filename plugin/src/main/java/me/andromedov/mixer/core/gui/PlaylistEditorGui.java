package me.andromedov.mixer.core.gui;

import me.andromedov.mixer.api.disc.MixerDisc;
import me.andromedov.mixer.api.playlist.MixerPlaylist;
import me.andromedov.mixer.api.playlist.MixerPlaylistTrack;
import me.andromedov.mixer.core.MixerPlugin;
import me.andromedov.mixer.core.playlist.PlaylistCartridgeService;
import me.andromedov.mixer.core.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class PlaylistEditorGui implements Listener {
    private static final int INVENTORY_SIZE = 27;
    private static final int TRACK_SLOTS = 18;
    private static final int CLEAR_SLOT = 22;
    private static final int CLOSE_SLOT = 26;
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final MixerPlugin plugin;
    private final PlaylistCartridgeService cartridges;

    public PlaylistEditorGui(MixerPlugin plugin, PlaylistCartridgeService cartridges) {
        this.plugin = plugin;
        this.cartridges = cartridges;
    }

    public void open(Player player, int inventorySlot, ItemStack item) {
        UUID cartridgeId = cartridges.id(item).orElse(null);
        MixerPlaylist cartridge = cartridges.read(item).orElse(null);
        if (cartridgeId == null || cartridge == null) {
            MessageUtil.sendActionBarMsg(player, "invalid_cartridge");
            return;
        }

        EditorHolder holder = new EditorHolder(player.getUniqueId(), inventorySlot, cartridgeId);
        Inventory inventory = Bukkit.createInventory(holder, INVENTORY_SIZE,
                MM.deserialize(plugin.getLocalizationManager().getMessage("playlist.editor_title")));
        holder.inventory = inventory;
        render(inventory, cartridge);
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof EditorHolder holder)) return;
        if (!(event.getWhoClicked() instanceof Player player) || !holder.playerId.equals(player.getUniqueId())) return;

        if (event.getClickedInventory() == event.getView().getTopInventory()) {
            event.setCancelled(true);
            if (event.getSlot() < TRACK_SLOTS) {
                removeTrack(player, holder, event.getSlot());
            } else if (event.getSlot() == CLEAR_SLOT) {
                update(player, holder, cartridge -> cartridge.withTracks(List.of()));
            } else if (event.getSlot() == CLOSE_SLOT) {
                player.closeInventory();
            }
            return;
        }

        if (event.getClickedInventory() != player.getInventory()) return;
        if (event.getSlot() == holder.inventorySlot) {
            event.setCancelled(true);
            return;
        }
        if (!event.isShiftClick()) return;

        ItemStack clicked = event.getCurrentItem();
        java.util.Optional<MixerDisc> disc = plugin.api().discs().readDisc(clicked);
        if (disc.isEmpty()) return;
        event.setCancelled(true);
        addTrack(player, holder, MixerPlaylistTrack.fromDisc(disc.orElseThrow()));
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof EditorHolder)) return;
        if (event.getRawSlots().stream().anyMatch(slot -> slot < INVENTORY_SIZE)) {
            event.setCancelled(true);
        }
    }

    private void addTrack(Player player, EditorHolder holder, MixerPlaylistTrack track) {
        update(player, holder, cartridge -> {
            if (cartridge.tracks().size() >= plugin.getPlaylistCartridgeMaxTracks()) {
                MessageUtil.sendActionBarMsg(player, "cartridge_full");
                return cartridge;
            }
            List<MixerPlaylistTrack> tracks = new ArrayList<>(cartridge.tracks());
            tracks.add(track);
            return cartridge.withTracks(tracks);
        });
    }

    private void removeTrack(Player player, EditorHolder holder, int index) {
        update(player, holder, cartridge -> {
            if (index >= cartridge.tracks().size()) return cartridge;
            List<MixerPlaylistTrack> tracks = new ArrayList<>(cartridge.tracks());
            tracks.remove(index);
            return cartridge.withTracks(tracks);
        });
    }

    private void update(Player player, EditorHolder holder,
                        java.util.function.UnaryOperator<MixerPlaylist> operation) {
        ItemStack item = player.getInventory().getItem(holder.inventorySlot);
        if (!holder.cartridgeId.equals(cartridges.id(item).orElse(null))) {
            MessageUtil.sendActionBarMsg(player, "cartridge_moved");
            player.closeInventory();
            return;
        }
        MixerPlaylist current = cartridges.read(item).orElse(null);
        if (current == null) {
            MessageUtil.sendActionBarMsg(player, "invalid_cartridge");
            player.closeInventory();
            return;
        }
        MixerPlaylist updated = operation.apply(current);
        if (!cartridges.write(item, holder.cartridgeId, updated)) {
            MessageUtil.sendActionBarMsg(player, "cartridge_save_failed");
            return;
        }
        render(holder.inventory, updated);
    }

    private void render(Inventory inventory, MixerPlaylist cartridge) {
        inventory.clear();
        for (int slot = 0; slot < TRACK_SLOTS; slot++) {
            if (slot < cartridge.tracks().size()) {
                MixerPlaylistTrack track = cartridge.tracks().get(slot);
                ItemStack icon = new ItemStack(Material.MUSIC_DISC_13);
                int number = slot + 1;
                icon.editMeta(meta -> {
                    meta.displayName(Component.text(number + ". " + track.title(), NamedTextColor.AQUA)
                            .decoration(TextDecoration.ITALIC, false));
                    meta.lore(List.of(
                            Component.text(track.author(), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                            MM.deserialize(plugin.getLocalizationManager().getMessage("playlist.remove_track"))
                                    .decoration(TextDecoration.ITALIC, false)
                    ));
                });
                inventory.setItem(slot, icon);
            } else {
                ItemStack empty = new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
                empty.editMeta(meta -> meta.displayName(MM.deserialize(plugin.getLocalizationManager()
                        .getMessage("playlist.empty_slot")).decoration(TextDecoration.ITALIC, false)));
                inventory.setItem(slot, empty);
            }
        }

        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        filler.editMeta(meta -> meta.displayName(Component.empty()));
        for (int slot = TRACK_SLOTS; slot < INVENTORY_SIZE; slot++) inventory.setItem(slot, filler);

        ItemStack clear = new ItemStack(Material.BARRIER);
        clear.editMeta(meta -> meta.displayName(MM.deserialize(plugin.getLocalizationManager()
                .getMessage("playlist.clear_button")).decoration(TextDecoration.ITALIC, false)));
        inventory.setItem(CLEAR_SLOT, clear);

        ItemStack close = new ItemStack(Material.IRON_DOOR);
        close.editMeta(meta -> meta.displayName(MM.deserialize(plugin.getLocalizationManager()
                .getMessage("playlist.close_button")).decoration(TextDecoration.ITALIC, false)));
        inventory.setItem(CLOSE_SLOT, close);
    }

    private static final class EditorHolder implements InventoryHolder {
        private final UUID playerId;
        private final int inventorySlot;
        private final UUID cartridgeId;
        private Inventory inventory;

        private EditorHolder(UUID playerId, int inventorySlot, UUID cartridgeId) {
            this.playerId = playerId;
            this.inventorySlot = inventorySlot;
            this.cartridgeId = cartridgeId;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
