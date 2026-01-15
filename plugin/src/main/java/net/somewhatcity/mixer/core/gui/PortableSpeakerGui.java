package net.somewhatcity.mixer.core.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
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
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class PortableSpeakerGui implements Listener {

    private final Map<UUID, UUID> openSpeakers = new HashMap<>();

    private Component getTitle() {
        String title = MixerPlugin.getPlugin().getLocalizationManager().getMessage("portableSpeaker.portable_speaker_gui_name");
        return MiniMessage.miniMessage().deserialize(title);
    }

    public void open(Player player, UUID speakerId) {
        openSpeakers.put(player.getUniqueId(), speakerId);
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
        String startName = MixerPlugin.getPlugin().getLocalizationManager().getMessage("portableSpeaker.portable_speaker_start_button");
        startMeta.displayName(MiniMessage.miniMessage().deserialize(startName).decoration(TextDecoration.ITALIC, false));
        start.setItemMeta(startMeta);
        inv.setItem(0, start);

        // DSP / Effects Button
        ItemStack dsp = new ItemStack(Material.AMETHYST_SHARD);
        ItemMeta dspMeta = dsp.getItemMeta();
        String dspName = MixerPlugin.getPlugin().getLocalizationManager().getMessage("dsp.gui_title");
        dspMeta.displayName(MiniMessage.miniMessage().deserialize(dspName).decoration(TextDecoration.ITALIC, false));
        dspMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        dsp.setItemMeta(dspMeta);
        inv.setItem(2, dsp);

        // Stop button
        ItemStack stop = new ItemStack(Material.RED_CONCRETE);
        ItemMeta stopMeta = stop.getItemMeta();
        String stopName = MixerPlugin.getPlugin().getLocalizationManager().getMessage("portableSpeaker.portable_speaker_stop_button");
        stopMeta.displayName(MiniMessage.miniMessage().deserialize(stopName).decoration(TextDecoration.ITALIC, false));
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
                    MessageUtil.sendActionBarMsg(player, "no_disc_inserted");
                    return;
                }

                NamespacedKey mixerData = new NamespacedKey(MixerPlugin.getPlugin(), "mixer_data");
                if (!disc.hasItemMeta() || !disc.getItemMeta().getPersistentDataContainer().has(mixerData, PersistentDataType.STRING)) {
                    MessageUtil.sendActionBarMsg(player, "not_a_valid_disc");
                    return;
                }

                String url = disc.getItemMeta().getPersistentDataContainer().get(mixerData, PersistentDataType.STRING);

                if (MixerPlugin.getPlugin().getPortablePlayerMap().containsKey(player.getUniqueId())) {
                    MixerPlugin.getPlugin().getPortablePlayerMap().get(player.getUniqueId()).stop();
                }

                EntityMixerAudioPlayer portablePlayer = new EntityMixerAudioPlayer(player);

                UUID speakerId = openSpeakers.get(player.getUniqueId());
                if (speakerId != null) {
                    portablePlayer.setSourceItemId(speakerId);
                } else {
                    MixerPlugin.getPlugin().logDebug(Level.WARNING, "Speaker UUID missing for player " + player.getName(), null);
                }

                portablePlayer.load(url);
                MixerPlugin.getPlugin().getPortablePlayerMap().put(player.getUniqueId(), portablePlayer);
                MessageUtil.sendActionBarMsg(player, "playback_start");

                player.closeInventory();
            }
            // "Audio Effects" button logic
            else if (e.getSlot() == 2) {
                if (MixerPlugin.getPlugin().getPortablePlayerMap().containsKey(player.getUniqueId())) {
                    MixerPlugin.getPlugin().getDspGui().open(player, player.getUniqueId());
                } else {
                    MessageUtil.sendActionBarMsg(player, "failed_to_stop");
                }
            }
            // "Stop" button logic
            else if (e.getSlot() == 8) {
                if (MixerPlugin.getPlugin().getPortablePlayerMap().containsKey(player.getUniqueId())) {
                    MixerPlugin.getPlugin().getPortablePlayerMap().get(player.getUniqueId()).stop();
                    MessageUtil.sendActionBarMsg(player, "playback_stop");
                } else {
                    MessageUtil.sendActionBarMsg(player, "failed_to_stop");
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
        openSpeakers.remove(e.getPlayer().getUniqueId());

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