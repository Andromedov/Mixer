package net.somewhatcity.mixer.core.listener;

import net.somewhatcity.mixer.core.MixerPlugin;
import net.somewhatcity.mixer.core.audio.EntityMixerAudioPlayer;
import net.somewhatcity.mixer.core.util.MessageUtil;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

public class InventoryListener implements Listener {

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!MixerPlugin.getPlugin().isPortableSpeakerEnabled()) return;

        if (e.getClickedInventory() == null) return;

        ItemStack currentItem = e.getCurrentItem();
        ItemStack cursorItem = e.getCursor();

        checkAndStop(e.getWhoClicked().getUniqueId(), currentItem, e.getClickedInventory().getType());
        checkAndStop(e.getWhoClicked().getUniqueId(), cursorItem, e.getClickedInventory().getType());

        if (e.getClick() == ClickType.NUMBER_KEY) {
            int hotbarButton = e.getHotbarButton();
            if (hotbarButton >= 0 && hotbarButton <= 8) {
                ItemStack hotbarItem = e.getWhoClicked().getInventory().getItem(hotbarButton);
                InventoryType type = e.getClickedInventory().getType();
                checkAndStop(e.getWhoClicked().getUniqueId(), hotbarItem, type);
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent e) {
        if (!MixerPlugin.getPlugin().isPortableSpeakerEnabled()) return;

        ItemStack draggedItem = e.getOldCursor();
        checkAndStop(e.getWhoClicked().getUniqueId(), draggedItem, e.getInventory().getType());
    }

    private void checkAndStop(UUID playerId, ItemStack item, InventoryType inventoryType) {
        if (item == null || item.getType() == Material.AIR) return;

        if (inventoryType == InventoryType.PLAYER ||
                inventoryType == InventoryType.CRAFTING ||
                inventoryType == InventoryType.CREATIVE) {
            return;
        }

        String matName = MixerPlugin.getPlugin().getPortableSpeakerItemMaterial();
        Material mat = Material.getMaterial(matName);
        if (mat == null) mat = Material.NOTE_BLOCK;

        if (item.getType() == mat) {
            NamespacedKey speakerKey = new NamespacedKey(MixerPlugin.getPlugin(), "mixer_speaker");
            if (item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(speakerKey, PersistentDataType.BYTE)) {

                NamespacedKey idKey = new NamespacedKey(MixerPlugin.getPlugin(), "mixer_speaker_id");
                UUID itemId = null;

                if (item.getItemMeta().getPersistentDataContainer().has(idKey, PersistentDataType.STRING)) {
                    try {
                        itemId = UUID.fromString(item.getItemMeta().getPersistentDataContainer().get(idKey, PersistentDataType.STRING));
                    } catch (Exception ex) {
                        return;
                    }
                }

                if (MixerPlugin.getPlugin().getPortablePlayerMap().containsKey(playerId)) {
                    EntityMixerAudioPlayer player = MixerPlugin.getPlugin().getPortablePlayerMap().get(playerId);

                    if (player.getSourceItemId() != null && itemId != null && player.getSourceItemId().equals(itemId)) {
                        player.stop();
                        if (org.bukkit.Bukkit.getPlayer(playerId) != null) {
                            MessageUtil.sendActionBarMsg(org.bukkit.Bukkit.getPlayer(playerId), "portable_stop");
                        }
                    }
                }
            }
        }
    }
}