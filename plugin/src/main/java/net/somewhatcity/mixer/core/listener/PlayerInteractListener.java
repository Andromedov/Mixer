/*
 * Copyright (c) 2023 mrmrmystery
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice (including the next paragraph) shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package net.somewhatcity.mixer.core.listener;

import com.destroystokyo.paper.event.block.BlockDestroyEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.somewhatcity.mixer.core.MixerPlugin;
import net.somewhatcity.mixer.core.audio.IMixerAudioPlayer;
import net.somewhatcity.mixer.core.util.MessageUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerInteractListener implements Listener {

    private final Map<Location, Long> lastInteractTime = new ConcurrentHashMap<>();
    private static final long INTERACT_COOLDOWN = 1000; // 1 second

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getClickedBlock() == null) return;
        if (!e.getClickedBlock().getType().equals(Material.JUKEBOX)) return;

        Location location = e.getClickedBlock().getLocation();
        long currentTime = System.currentTimeMillis();

        Long lastTime = lastInteractTime.get(location);
        if (lastTime != null && (currentTime - lastTime) < INTERACT_COOLDOWN) {
            e.setCancelled(true);
            return;
        }

        lastInteractTime.put(location, currentTime);

        if (e.getAction().equals(Action.LEFT_CLICK_BLOCK)) {
            Location loc = e.getClickedBlock().getLocation();
            if (MixerPlugin.getPlugin().playerHashMap().containsKey(loc)) {
                IMixerAudioPlayer audioPlayer = MixerPlugin.getPlugin().playerHashMap().get(loc);
                audioPlayer.stop();
            }
        } else if (e.getAction().equals(Action.RIGHT_CLICK_BLOCK)) {
            Location loc = e.getClickedBlock().getLocation();
            if (MixerPlugin.getPlugin().playerHashMap().containsKey(loc)) {
                IMixerAudioPlayer audioPlayer = MixerPlugin.getPlugin().playerHashMap().get(loc);
                if (e.getPlayer().isSneaking()) {
                    int boost = e.getPlayer().getInventory().getHeldItemSlot() * 100;
                    if (boost == 0) {
                        // oldPlayer.resetFilters();
                        MessageUtil.sendActionBarMsg(e.getPlayer(), "bassboost_disabled");
                        return;
                    }
                    // oldPlayer.bassBoost(boost);
                    MessageUtil.sendActionBarMsg(e.getPlayer(), "bassboost_set_to", boost);
                    return;
                }
                MessageUtil.sendActionBarMsg(e.getPlayer(), "playback_stop");
                audioPlayer.stop();
                e.setCancelled(true);
                return;
            }
            if (e.getItem() == null) return;
            NamespacedKey mixerData = new NamespacedKey(MixerPlugin.getPlugin(), "mixer_data");
            if (!e.getItem().getPersistentDataContainer().getKeys().contains(mixerData)) return;
            String url = e.getItem().getPersistentDataContainer().get(mixerData, PersistentDataType.STRING);
            e.setCancelled(true);

            try {
                IMixerAudioPlayer audioPlayer = new IMixerAudioPlayer(location);
                audioPlayer.load(url);
            } catch (Exception ex) {
                MixerPlugin.getPlugin().getLogger().warning("Failed to create audio player: " + ex.getMessage());
                MessageUtil.sendActionBarMsg(e.getPlayer(), "failed_to_start");
            }
        }
    }

    @EventHandler
    public void onBlockBreak(BlockDestroyEvent e) {
        if (e.getBlock().getType().equals(Material.JUKEBOX)) {
            Location loc = e.getBlock().getLocation();
            if (MixerPlugin.getPlugin().playerHashMap().containsKey(loc)) {
                IMixerAudioPlayer audioPlayer = MixerPlugin.getPlugin().playerHashMap().get(loc);
                audioPlayer.stop();
            }
            lastInteractTime.remove(loc);
        }
    }
}
