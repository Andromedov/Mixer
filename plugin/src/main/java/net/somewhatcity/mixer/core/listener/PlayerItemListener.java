package net.somewhatcity.mixer.core.listener;

import net.somewhatcity.mixer.core.MixerPlugin;
import net.somewhatcity.mixer.core.util.MessageUtil;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public class PlayerItemListener implements Listener {

    @EventHandler
    public void onItemDrop(PlayerDropItemEvent e) {
        if (!MixerPlugin.getPlugin().isPortableSpeakerEnabled()) return;

        ItemStack item = e.getItemDrop().getItemStack();
        String matName = MixerPlugin.getPlugin().getPortableSpeakerItemMaterial();
        Material mat = Material.getMaterial(matName);
        if (mat == null) mat = Material.NOTE_BLOCK;

        if (item.getType() == mat) {
            NamespacedKey speakerKey = new NamespacedKey(MixerPlugin.getPlugin(), "mixer_speaker");
            if (item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(speakerKey, PersistentDataType.BYTE)) {
                if (MixerPlugin.getPlugin().getPortablePlayerMap().containsKey(e.getPlayer().getUniqueId())) {
                    MixerPlugin.getPlugin().getPortablePlayerMap().get(e.getPlayer().getUniqueId()).stop();
                    MessageUtil.sendActionBarMsg(e.getPlayer(), "playback_stop");
                }
            }
        }
    }
}