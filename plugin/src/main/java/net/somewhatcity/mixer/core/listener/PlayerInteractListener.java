package net.somewhatcity.mixer.core.listener;

import com.destroystokyo.paper.event.block.BlockDestroyEvent;
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
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class PlayerInteractListener implements Listener {

    private final Map<Location, Long> lastInteractTime = new ConcurrentHashMap<>();
    private static final long INTERACT_COOLDOWN = 1000; // 1 second

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        // --- Portable Speaker Mechanic ---
        if (e.getHand() == EquipmentSlot.HAND && e.getAction().toString().contains("RIGHT_CLICK")) {
            if (MixerPlugin.getPlugin().isPortableSpeakerEnabled()) {
                ItemStack item = e.getItem();

                String matName = MixerPlugin.getPlugin().getPortableSpeakerItemMaterial();
                Material mat = Material.getMaterial(matName);
                if (mat == null) mat = Material.NOTE_BLOCK; // Fallback

                if (item != null && item.getType() == mat) {
                    NamespacedKey speakerKey = new NamespacedKey(MixerPlugin.getPlugin(), "mixer_speaker");
                    if (item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(speakerKey, PersistentDataType.BYTE)) {

                        // Ensure item has a unique ID
                        NamespacedKey idKey = new NamespacedKey(MixerPlugin.getPlugin(), "mixer_speaker_id");
                        UUID speakerId;

                        if (!item.getItemMeta().getPersistentDataContainer().has(idKey, PersistentDataType.STRING)) {
                            speakerId = UUID.randomUUID();
                            ItemMeta meta = item.getItemMeta();
                            meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, UUID.randomUUID().toString());
                            item.setItemMeta(meta);
                        } else {
                            try {
                                speakerId = UUID.fromString(item.getItemMeta().getPersistentDataContainer().get(idKey, PersistentDataType.STRING));
                            } catch (Exception ex) {
                                speakerId = UUID.randomUUID();
                            }
                        }

                        e.setCancelled(true);
                        MixerPlugin.getPlugin().getPortableSpeakerGui().open(e.getPlayer(), speakerId);
                        return;
                    }
                }
            }
        }

        // --- Original Jukebox Logic ---
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
            if (!e.getItem().hasItemMeta() || !e.getItem().getItemMeta().getPersistentDataContainer().getKeys().contains(mixerData)) return;
            String url = e.getItem().getItemMeta().getPersistentDataContainer().get(mixerData, PersistentDataType.STRING);
            e.setCancelled(true);

            try {
                IMixerAudioPlayer audioPlayer = new IMixerAudioPlayer(location);
                audioPlayer.load(url);
            } catch (Exception ex) {
                MixerPlugin.getPlugin().logDebug(Level.WARNING, "Failed to create audio player", ex);
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