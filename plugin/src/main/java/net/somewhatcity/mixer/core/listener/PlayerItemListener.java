package net.somewhatcity.mixer.core.listener;

import net.somewhatcity.mixer.core.MixerPlugin;
import net.somewhatcity.mixer.core.audio.EntityMixerAudioPlayer;
import net.somewhatcity.mixer.core.util.MessageUtil;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

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

                // Get ID of dropped item
                NamespacedKey idKey = new NamespacedKey(MixerPlugin.getPlugin(), "mixer_speaker_id");
                UUID droppedId = null;
                if (item.getItemMeta().getPersistentDataContainer().has(idKey, PersistentDataType.STRING)) {
                    try {
                        droppedId = UUID.fromString(item.getItemMeta().getPersistentDataContainer().get(idKey, PersistentDataType.STRING));
                    } catch (Exception ex) {
                        return; // Invalid ID, ignore
                    }
                }

                // Check if player is playing music
                if (MixerPlugin.getPlugin().getPortablePlayerMap().containsKey(e.getPlayer().getUniqueId())) {
                    EntityMixerAudioPlayer player = MixerPlugin.getPlugin().getPortablePlayerMap().get(e.getPlayer().getUniqueId());
                    if (player.getSourceItemId() != null && droppedId != null && player.getSourceItemId().equals(droppedId)) {
                        player.stop();
                        MessageUtil.sendActionBarMsg(e.getPlayer(), "playback_stop");
                    }
                }
            }
        }
    }
}