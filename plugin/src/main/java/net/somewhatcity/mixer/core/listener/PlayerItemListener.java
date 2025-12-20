package net.somewhatcity.mixer.core.listener;

import net.somewhatcity.mixer.core.MixerPlugin;
import net.somewhatcity.mixer.core.audio.EntityMixerAudioPlayer;
import net.somewhatcity.mixer.core.util.MessageUtil;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.ItemFrame;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.Objects;
import java.util.UUID;

public class PlayerItemListener implements Listener {

    @EventHandler
    public void onItemDrop(PlayerDropItemEvent e) {
        if (!MixerPlugin.getPlugin().isPortableSpeakerEnabled()) return;
        checkAndStop(e.getPlayer(), e.getItemDrop().getItemStack());
    }

    @EventHandler
    public void onEntityInteract(PlayerInteractEntityEvent e) {
        if (!MixerPlugin.getPlugin().isPortableSpeakerEnabled()) return;

        if (e.getRightClicked() instanceof ItemFrame || e.getRightClicked() instanceof ArmorStand) {
            ItemStack item = e.getPlayer().getInventory().getItemInMainHand();
            if (item.getType() == Material.AIR) {
                item = e.getPlayer().getInventory().getItemInOffHand();
            }

            checkAndStop(e.getPlayer(), item);
        }
    }

    private void checkAndStop(org.bukkit.entity.Player player, ItemStack item) {
        String matName = MixerPlugin.getPlugin().getPortableSpeakerItemMaterial();
        Material mat = Material.getMaterial(matName);
        if (mat == null) mat = Material.NOTE_BLOCK;

        if (item != null && item.getType() == mat) {
            NamespacedKey speakerKey = new NamespacedKey(MixerPlugin.getPlugin(), "mixer_speaker");
            if (item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(speakerKey, PersistentDataType.BYTE)) {

                // Get ID of item
                NamespacedKey idKey = new NamespacedKey(MixerPlugin.getPlugin(), "mixer_speaker_id");
                UUID itemId = null;
                if (item.getItemMeta().getPersistentDataContainer().has(idKey, PersistentDataType.STRING)) {
                    try {
                        itemId = UUID.fromString(Objects.requireNonNull(item.getItemMeta().getPersistentDataContainer().get(idKey, PersistentDataType.STRING)));
                    } catch (Exception ex) {
                        return;
                    }
                }

                // Check if the player is playing music
                if (MixerPlugin.getPlugin().getPortablePlayerMap().containsKey(player.getUniqueId())) {
                    EntityMixerAudioPlayer audioPlayer = MixerPlugin.getPlugin().getPortablePlayerMap().get(player.getUniqueId());
                    if (audioPlayer.getSourceItemId() != null && itemId != null && audioPlayer.getSourceItemId().equals(itemId)) {
                        audioPlayer.stop();
                        MessageUtil.sendActionBarMsg(player, "playback_stop");
                    }
                }
            }
        }
    }
}