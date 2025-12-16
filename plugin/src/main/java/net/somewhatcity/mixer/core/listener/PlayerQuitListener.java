package net.somewhatcity.mixer.core.listener;

import net.somewhatcity.mixer.core.MixerPlugin;
import net.somewhatcity.mixer.core.audio.EntityMixerAudioPlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerQuitListener implements Listener {
    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        stopPlayer(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        stopPlayer(e.getEntity().getUniqueId());
    }

    private void stopPlayer(java.util.UUID uuid) {
        EntityMixerAudioPlayer player = MixerPlugin.getPlugin().getPortablePlayerMap().remove(uuid);
        if (player != null) {
            player.stop();
        }
    }
}