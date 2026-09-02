package me.andromedov.mixer.core.listener;

import me.andromedov.mixer.core.MixerPlugin;
import me.andromedov.mixer.core.audio.EntityMixerAudioPlayer;
import me.andromedov.mixer.core.util.MessageUtil;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.ItemFrame;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;

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
        MixerPlugin plugin = MixerPlugin.getPlugin();
        java.util.UUID itemId = plugin.getPortableSpeakers().id(item).orElse(null);
        EntityMixerAudioPlayer audioPlayer = plugin.getPortablePlayerMap().get(player.getUniqueId());
        if (audioPlayer == null || itemId == null || !itemId.equals(audioPlayer.getSourceItemId())) return;
        plugin.getPortableSpeakers().eject(player, item);
        audioPlayer.stop();
        MessageUtil.sendActionBarMsg(player, "playback_stop");
    }
}
