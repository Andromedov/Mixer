package me.andromedov.mixer.core.listener;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import me.andromedov.mixer.api.MixerAudioPlayer;
import me.andromedov.mixer.core.MixerPlugin;
import me.andromedov.mixer.core.audio.IMixerAudioPlayer;
import me.andromedov.mixer.core.util.PlaybackAuthorization;
import me.andromedov.mixer.api.disc.MixerDisc;
import me.andromedov.mixer.api.playback.MixerPlaybackOrigin;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.*;
import org.bukkit.block.data.Directional;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RedstoneListener implements Listener {
    private final Map<Location, Long> cooldowns = new HashMap<>();
    private static final long COOLDOWN_MS = 1000;

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRedstone(BlockRedstoneEvent e) {
        Block block = e.getBlock();
        if(!block.getType().equals(Material.REPEATER)) return;

        // Trigger once, on the rising edge. Reading Repeater#isPowered() here is
        // version-dependent because BlockRedstoneEvent may expose either the old
        // or already-updated block-data snapshot.
        if (e.getOldCurrent() != 0 || e.getNewCurrent() <= 0) return;

        Directional directional = (Directional) block.getBlockData();
        BlockFace facing = directional.getFacing().getOppositeFace();

        if(!block.getRelative(facing).getType().equals(Material.JUKEBOX)) return;
        Block jukebox = block.getRelative(facing);
        Location jukeLoc = jukebox.getLocation();

        long now = System.currentTimeMillis();
        if (cooldowns.containsKey(jukeLoc) && (now - cooldowns.get(jukeLoc)) < COOLDOWN_MS) {
            return;
        }
        cooldowns.put(jukeLoc, now);

        Block containerBlock = jukebox.getRelative(BlockFace.UP);
        BlockState containerState = containerBlock.getState();


        if (!(containerState instanceof Container container)) return;

        List<String> loadList = new ArrayList<>();

        for(ItemStack item : container.getInventory()) {
            if(item == null) continue;
            java.util.Optional<MixerDisc> mixerDisc = MixerPlugin.getPlugin().api().discs().readDisc(item);
            if(mixerDisc.isPresent()) {
                String source = mixerDisc.orElseThrow().source();
                if (PlaybackAuthorization.allow(MixerPlaybackOrigin.REDSTONE_PLAYLIST, source,
                        item, null, jukebox.getLocation())) {
                    loadList.add(source);
                }
            }
            else if(item.getType().equals(Material.WRITABLE_BOOK)) {
                BookMeta bookMeta = (BookMeta) item.getItemMeta();
                StringBuilder sb = new StringBuilder();
                for(Component component : bookMeta.pages()) {
                    sb.append(MiniMessage.miniMessage().serialize(component));
                }
                loadList.add(getTtsUrl(sb.toString()));
            }
        }

        if (loadList.isEmpty()) return;

        MixerAudioPlayer existingPlayer = MixerPlugin.getPlugin().api().getMixerAudioPlayer(jukebox.getLocation());
        if (existingPlayer != null) {
            existingPlayer.stop();
        }

        final IMixerAudioPlayer targetPlayer = (IMixerAudioPlayer) MixerPlugin.getPlugin().api().createPlayer(jukebox.getLocation());
        targetPlayer.clearAndPlay(loadList.toArray(String[]::new));
    }

    private static final String TTS_URL = "https://translate.google.com/translate_tts?ie=UTF-8&client=gtx&tl=uk&q=%s";
    public static String getTtsUrl(String text) {
        try {
            String encoded = URLEncoder.encode(text, StandardCharsets.UTF_8);
            return TTS_URL.formatted(encoded);
        } catch (Exception e) {
            return TTS_URL.formatted(text.replace(" ", "%20"));
        }
    }
}
