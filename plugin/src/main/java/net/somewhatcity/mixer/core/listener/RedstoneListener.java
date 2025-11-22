package net.somewhatcity.mixer.core.listener;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.somewhatcity.mixer.api.MixerAudioPlayer;
import net.somewhatcity.mixer.core.MixerPlugin;
import net.somewhatcity.mixer.core.audio.IMixerAudioPlayer;
import net.somewhatcity.mixer.core.util.Utils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.*;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.type.Repeater;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RedstoneListener implements Listener {
    private final Map<Location, Long> cooldowns = new HashMap<>();
    private static final long COOLDOWN_MS = 1000;

    @EventHandler
    public void onRedstone(BlockRedstoneEvent e) {
        Block block = e.getBlock();
        if(!block.getType().equals(Material.REPEATER)) return;

        Repeater repeater = (Repeater) block.getBlockData();
        if(repeater.isPowered()) return;

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


        if (!(containerState instanceof Barrel) && !(containerState instanceof ShulkerBox)) return;
        Container container = (Container) containerState;

        List<String> loadList = new ArrayList<>();

        for(ItemStack item : container.getInventory()) {
            if(item == null) continue;
            if(Utils.isDisc(item)) {
                NamespacedKey mixerData = new NamespacedKey(MixerPlugin.getPlugin(), "mixer_data");
                if(!item.getPersistentDataContainer().getKeys().contains(mixerData)) continue;
                String url = item.getPersistentDataContainer().get(mixerData, PersistentDataType.STRING);
                loadList.add(url);
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

        final String[] urls = loadList.toArray(String[]::new);
        org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(MixerPlugin.getPlugin(), () -> {
            targetPlayer.clearAndPlay(urls);
        });
    }

    private static final String TTS_URL = "https://translate.google.com/translate_tts?ie=UTF-8&client=gtx&tl=uk&q=%s";
    public static String getTtsUrl(String text) {
        text = text.replace(" ", "%20");
        return TTS_URL.formatted(text);
    }
}