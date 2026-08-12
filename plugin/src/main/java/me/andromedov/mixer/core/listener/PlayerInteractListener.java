package me.andromedov.mixer.core.listener;

import com.destroystokyo.paper.event.block.BlockDestroyEvent;
import me.andromedov.mixer.core.MixerPlugin;
import me.andromedov.mixer.core.audio.IMixerAudioPlayer;
import me.andromedov.mixer.core.util.MessageUtil;
import me.andromedov.mixer.core.util.PlaybackAuthorization;
import me.andromedov.mixer.api.disc.MixerDisc;
import me.andromedov.mixer.api.playback.MixerPlaybackOrigin;
import io.papermc.paper.datacomponent.DataComponentTypes;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Jukebox;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class PlayerInteractListener implements Listener {

    private final Map<Location, Long> lastInteractTime = new ConcurrentHashMap<>();
    private static final long INTERACT_COOLDOWN = 500; // 0.5 second

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        // --- Portable Speaker Mechanic ---
        if (e.getHand() == EquipmentSlot.HAND && e.getAction().toString().contains("RIGHT_CLICK")) {
            ItemStack item = e.getItem();
            if (MixerPlugin.getPlugin().arePlaylistCartridgesEnabled()
                    && MixerPlugin.getPlugin().getPlaylistCartridges().isCartridge(item)) {
                e.setCancelled(true);
                MixerPlugin.getPlugin().getPlaylistEditorGui().open(
                        e.getPlayer(), e.getPlayer().getInventory().getHeldItemSlot(), item);
                return;
            }

            if (MixerPlugin.getPlugin().isPortableSpeakerEnabled()) {
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
                            meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, speakerId.toString());
                            item.setItemMeta(meta);
                        } else {
                            try {
                                speakerId = UUID.fromString(Objects.requireNonNull(item.getItemMeta().getPersistentDataContainer().get(idKey, PersistentDataType.STRING)));
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

        if (e.getClickedBlock() == null) return;
        if (!e.getClickedBlock().getType().equals(Material.JUKEBOX)) return;
        if (e.getHand() != EquipmentSlot.HAND) return;

        Location location = e.getClickedBlock().getLocation();
        long currentTime = System.currentTimeMillis();

        synchronized (lastInteractTime) {
            Long lastTime = lastInteractTime.get(location);
            if (lastTime != null && (currentTime - lastTime) < INTERACT_COOLDOWN) {
                e.setCancelled(true);
                return;
            }
            lastInteractTime.put(location, currentTime);
        }

        // --- DSP GUI Trigger ---
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && e.getPlayer().isSneaking()) {
            if (e.getItem() == null || e.getItem().getType() == Material.AIR) {
                e.setCancelled(true);
                MixerPlugin.getPlugin().getDspGui().open(e.getPlayer(), location);
                return;
            }
        }

        // --- Original Jukebox Logic ---
        if (e.getAction().equals(Action.LEFT_CLICK_BLOCK)) {
            if (MixerPlugin.getPlugin().playerHashMap().containsKey(location)) {
                IMixerAudioPlayer audioPlayer = MixerPlugin.getPlugin().playerHashMap().get(location);
                audioPlayer.stop();
            }
        } else if (e.getAction().equals(Action.RIGHT_CLICK_BLOCK)) {

            MixerPlugin plugin = MixerPlugin.getPlugin();
            java.util.Optional<MixerDisc> mixerDisc = plugin.api().discs().readDisc(e.getItem());
            boolean hasMixerDisc = mixerDisc.isPresent();

            if (plugin.getDiscInserted()) {
                Jukebox jukebox = (Jukebox) location.getBlock().getState();

                // If there's already a record inside, pop it out and stop playing.
                if (jukebox.hasRecord()) {
                    if (plugin.playerHashMap().containsKey(location)) {
                        plugin.playerHashMap().get(location).stop();
                        MessageUtil.sendActionBarMsg(e.getPlayer(), "playback_stop");
                    }
                    jukebox.eject(); // Pops out vanilla or custom record
                    e.setCancelled(true);
                    return;
                } else {
                    if (!hasMixerDisc) return; // If holding anything else, let vanilla handle it

                    String source = mixerDisc.orElseThrow().source();
                    if (!PlaybackAuthorization.allow(MixerPlaybackOrigin.JUKEBOX, source,
                            e.getItem(), e.getPlayer(), location)) {
                        e.setCancelled(true);
                        return;
                    }

                    // Own the interaction completely. Letting vanilla run after changing the
                    // jukebox can duplicate/eject the record during the client inventory sync.
                    e.setCancelled(true);
                    IMixerAudioPlayer audioPlayer = null;
                    boolean recordInserted = false;
                    try {
                        audioPlayer = new IMixerAudioPlayer(location);

                        ItemStack toInsert = e.getItem().clone();
                        toInsert.setAmount(1);
                        // Mixer owns playback. Keeping JUKEBOX_PLAYABLE on the stored
                        // item makes modern clients start its vanilla registry song too.
                        // This also normalizes discs created by older Mixer builds.
                        toInsert.unsetData(DataComponentTypes.JUKEBOX_PLAYABLE);
                        // JukeboxInventory is live; unlike a BlockState snapshot it does not
                        // require a second update that can replay a partially-applied change.
                        jukebox.getInventory().setRecord(toInsert);
                        recordInserted = true;
                        // The jukebox_playable component starts a vanilla song automatically.
                        // Mixer supplies the audio, so silence only the vanilla playback while
                        // keeping the physical record inside the block.
                        silenceVanillaPlayback(location);

                        audioPlayer.load(source);
                        consumeMainHandDisc(e.getPlayer());
                    } catch (Exception ex) {
                        if (audioPlayer != null) audioPlayer.stop();
                        // No item has been consumed before the player is ready. Remove a
                        // partially inserted record without ejecting a duplicate into the world.
                        if (recordInserted) jukebox.getInventory().setRecord(null);
                        plugin.logDebug(Level.WARNING, "Failed to create audio player", ex);
                        MessageUtil.sendActionBarMsg(e.getPlayer(), "failed_to_start");
                        return;
                    }
                    MessageUtil.sendActionBarMsg(e.getPlayer(), "playback_start");
                    return;
                }
            }

            // --- Old Behavior (Require Disc Inserted is FALSE) ---
            if (!hasMixerDisc) {
                if (plugin.playerHashMap().containsKey(location)) {
                    plugin.playerHashMap().get(location).stop();
                    e.setCancelled(true);
                    MessageUtil.sendActionBarMsg(e.getPlayer(), "playback_stop");
                }
                return;
            }

            String source = mixerDisc.orElseThrow().source();
            if (!PlaybackAuthorization.allow(MixerPlaybackOrigin.JUKEBOX, source,
                    e.getItem(), e.getPlayer(), location)) {
                e.setCancelled(true);
                return;
            }

            if (plugin.playerHashMap().containsKey(location)) {
                plugin.playerHashMap().get(location).stop();
            }
            e.setCancelled(true);

            try {
                IMixerAudioPlayer audioPlayer = new IMixerAudioPlayer(location);
                audioPlayer.load(source);
                MessageUtil.sendActionBarMsg(e.getPlayer(), "playback_start");
            } catch (Exception ex) {
                MixerPlugin.getPlugin().logDebug(Level.WARNING, "Failed to create audio player", ex);
                MessageUtil.sendActionBarMsg(e.getPlayer(), "failed_to_start");
            }
        }
    }

    private static void consumeMainHandDisc(org.bukkit.entity.Player player) {
        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE) return;

        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getAmount() <= 1) {
            player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        } else {
            held.setAmount(held.getAmount() - 1);
        }
    }

    private static void silenceVanillaPlayback(Location location) {
        MixerPlugin plugin = MixerPlugin.getPlugin();
        stopVanillaPlayback(location);

        // Paper may finish the vanilla jukebox interaction after listeners return.
        // Re-check next tick so its start packet cannot leave the vanilla song
        // playing alongside Mixer audio.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!plugin.playerHashMap().containsKey(location)) return;
            if (!(location.getBlock().getState() instanceof Jukebox jukebox)) return;
            if (!plugin.api().discs().isMixerDisc(jukebox.getRecord())) return;
            stopVanillaPlayback(location);
        });
    }

    private static void stopVanillaPlayback(Location location) {
        if (location.getBlock().getState() instanceof Jukebox jukebox) {
            jukebox.stopPlaying();
        }
    }

    private void stopMixerAt(Location location) {
        stopVanillaPlayback(location);
        IMixerAudioPlayer audioPlayer = MixerPlugin.getPlugin().playerHashMap().get(location);
        if (audioPlayer != null) audioPlayer.stop();
        lastInteractTime.remove(location);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerBreakJukebox(BlockBreakEvent e) {
        if (e.getBlock().getType() == Material.JUKEBOX) {
            stopMixerAt(e.getBlock().getLocation());
        }
    }

    @EventHandler
    public void onBlockBreak(BlockDestroyEvent e) {
        if (e.getBlock().getType().equals(Material.JUKEBOX)) {
            stopMixerAt(e.getBlock().getLocation());
        }
    }
}
