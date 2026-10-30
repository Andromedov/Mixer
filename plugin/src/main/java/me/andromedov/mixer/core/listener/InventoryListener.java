package me.andromedov.mixer.core.listener;

import me.andromedov.mixer.core.MixerPlugin;
import me.andromedov.mixer.core.audio.EntityMixerAudioPlayer;
import me.andromedov.mixer.core.util.MessageUtil;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class InventoryListener implements Listener {

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!MixerPlugin.getPlugin().isPortableSpeakerEnabled()) return;

        if (e.getClickedInventory() == null) {
            checkAndStop((org.bukkit.entity.Player) e.getWhoClicked(), e.getCursor(), null);
            return;
        }

        ItemStack currentItem = e.getCurrentItem();
        ItemStack cursorItem = e.getCursor();

        InventoryType type = e.getClickedInventory().getType();
        if (e.isShiftClick()) {
            if (e.getClickedInventory().equals(e.getView().getBottomInventory())) {
                InventoryType topType = e.getView().getTopInventory().getType();
                if (topType != InventoryType.CRAFTING && topType != InventoryType.PLAYER && topType != InventoryType.CREATIVE) {
                    type = topType;
                }
            }
        }

        checkAndStop((org.bukkit.entity.Player) e.getWhoClicked(), currentItem, type);
        checkAndStop((org.bukkit.entity.Player) e.getWhoClicked(), cursorItem, type);

        if (e.getClick() == ClickType.NUMBER_KEY) {
            int hotbarButton = e.getHotbarButton();
            if (hotbarButton >= 0 && hotbarButton <= 8) {
                ItemStack hotbarItem = e.getWhoClicked().getInventory().getItem(hotbarButton);
                checkAndStop((org.bukkit.entity.Player) e.getWhoClicked(), hotbarItem, e.getClickedInventory().getType());
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent e) {
        if (!MixerPlugin.getPlugin().isPortableSpeakerEnabled()) return;

        ItemStack draggedItem = e.getOldCursor();
        InventoryType type = e.getInventory().getType();

        if (type != InventoryType.CRAFTING && type != InventoryType.PLAYER && type != InventoryType.CREATIVE) {
            boolean affectsTop = false;
            int topSize = e.getView().getTopInventory().getSize();
            for (int slot : e.getRawSlots()) {
                if (slot < topSize) {
                    affectsTop = true;
                    break;
                }
            }

            if (!affectsTop) {
                type = InventoryType.PLAYER;
            }
        }

        checkAndStop((org.bukkit.entity.Player) e.getWhoClicked(), draggedItem, type);
    }

    private void checkAndStop(org.bukkit.entity.Player owner, ItemStack item, InventoryType inventoryType) {
        if (item == null || item.getType() == Material.AIR) return;

        if (inventoryType == InventoryType.PLAYER ||
                inventoryType == InventoryType.CRAFTING ||
                inventoryType == InventoryType.CREATIVE) {
            return;
        }

        MixerPlugin plugin = MixerPlugin.getPlugin();
        UUID itemId = plugin.getPortableSpeakers().id(item).orElse(null);
        EntityMixerAudioPlayer player = plugin.getPortablePlayerMap().get(owner.getUniqueId());
        if (player == null || itemId == null || !itemId.equals(player.getSourceItemId())) return;
        plugin.getPortableSpeakers().eject(owner, item);
        player.stop();
        MessageUtil.sendActionBarMsg(owner, "playback_stop");
    }
}
