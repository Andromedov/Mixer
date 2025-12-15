package net.somewhatcity.mixer.core.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.somewhatcity.mixer.core.MixerPlugin;
import net.somewhatcity.mixer.core.audio.EntityMixerAudioPlayer;
import net.somewhatcity.mixer.core.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;

public class PortableSpeakerGui implements Listener {

    private Component getTitle() {
        String title = MixerPlugin.getPlugin().getLocalizationManager().getMessage("portableSpeaker.portable_speaker_gui_name");
        return MiniMessage.miniMessage().deserialize(title);
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 9, getTitle());

        // Fillers
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        fillerMeta.displayName(Component.empty());
        filler.setItemMeta(fillerMeta);

        for (int i = 0; i < 9; i++) {
            if (i != 4) inv.setItem(i, filler);
        }

        // Start button
        ItemStack start = new ItemStack(Material.LIME_CONCRETE);
        ItemMeta startMeta = start.getItemMeta();
        String startName = "<reset>" + MixerPlugin.getPlugin().getLocalizationManager().getMessage("portableSpeaker.portable_speaker_start_button");
        startMeta.displayName(MiniMessage.miniMessage().deserialize(startName));
        start.setItemMeta(startMeta);
        inv.setItem(0, start);

        // Stop button
        ItemStack stop = new ItemStack(Material.RED_CONCRETE);
        ItemMeta stopMeta = stop.getItemMeta();
        String stopName = "<reset>" + MixerPlugin.getPlugin().getLocalizationManager().getMessage("portableSpeaker.portable_speaker_stop_button");
        stopMeta.displayName(MiniMessage.miniMessage().deserialize(stopName));
        stop.setItemMeta(stopMeta);
        inv.setItem(8, stop);

        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!e.getView().title().equals(getTitle())) return;

        if (e.getClickedInventory() == e.getView().getTopInventory()) {
            if (e.getSlot() != 4) {
                e.setCancelled(true);
            }
        } else if (e.getClickedInventory() == e.getView().getBottomInventory()) {
            if (e.isShiftClick()) {
                e.setCancelled(true);
            }
            return;
        }

        Player player = (Player) e.getWhoClicked();

        if (e.getClickedInventory() == e.getView().getTopInventory()) {
            // "Play" button logic
            if (e.getSlot() == 0) {
                ItemStack disc = e.getView().getTopInventory().getItem(4);
                if (disc == null || disc.getType() == Material.AIR) {
                    // TODO: add message to language file
                    player.sendMessage(MiniMessage.miniMessage().deserialize("<red>No disc inserted!</red>"));
                    return;
                }

                NamespacedKey mixerData = new NamespacedKey(MixerPlugin.getPlugin(), "mixer_data");
                if (!disc.hasItemMeta() || !disc.getItemMeta().getPersistentDataContainer().has(mixerData, PersistentDataType.STRING)) {
                    // TODO: add message to language file
                    player.sendMessage(MiniMessage.miniMessage().deserialize("<red>Not a valid Mixer disc!</red>"));
                    return;
                }

                String url = disc.getItemMeta().getPersistentDataContainer().get(mixerData, PersistentDataType.STRING);

                if (MixerPlugin.getPlugin().getPortablePlayerMap().containsKey(player.getUniqueId())) {
                    MixerPlugin.getPlugin().getPortablePlayerMap().get(player.getUniqueId()).stop();
                }

                EntityMixerAudioPlayer portablePlayer = new EntityMixerAudioPlayer(player);
                portablePlayer.load(url);
                MixerPlugin.getPlugin().getPortablePlayerMap().put(player.getUniqueId(), portablePlayer);
                MessageUtil.sendActionBarMsg(player, "playback_start");

                player.closeInventory();
            }
            // "Stop" button logic
            else if (e.getSlot() == 8) {
                if (MixerPlugin.getPlugin().getPortablePlayerMap().containsKey(player.getUniqueId())) {
                    MixerPlugin.getPlugin().getPortablePlayerMap().get(player.getUniqueId()).stop();
                    MessageUtil.sendActionBarMsg(player, "playback_stop");
                } else {
                    // TODO: add message to language file
                    player.sendMessage(MiniMessage.miniMessage().deserialize("<red>Nothing is playing!</red>"));
                }
                player.closeInventory();
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().title().equals(getTitle())) {
            for (int slot : e.getRawSlots()) {
                if (slot < 9 && slot != 4) {
                    e.setCancelled(true);
                    return;
                }
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!e.getView().title().equals(getTitle())) return;

        Inventory inv = e.getInventory();
        ItemStack disc = inv.getItem(4);
        if (disc != null && disc.getType() != Material.AIR) {
            HashMap<Integer, ItemStack> leftover = e.getPlayer().getInventory().addItem(disc);
            leftover.values().forEach(item ->
                    e.getPlayer().getWorld().dropItem(e.getPlayer().getLocation(), item)
            );
        }
    }
}