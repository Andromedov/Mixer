package net.somewhatcity.mixer.core.listener;

import net.somewhatcity.mixer.core.MixerPlugin;
import net.somewhatcity.mixer.core.audio.EntityMixerAudioPlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerQuitListener implements Listener {
    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        EntityMixerAudioPlayer player = MixerPlugin.getPlugin().getPortablePlayerMap().remove(e.getPlayer().getUniqueId());
        if (player != null) {
            player.stop();
        }
    }
}