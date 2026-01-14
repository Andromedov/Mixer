package net.somewhatcity.mixer.core.gui;

import com.google.gson.JsonObject;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.somewhatcity.mixer.core.MixerPlugin;
import net.somewhatcity.mixer.core.audio.IMixerAudioPlayer;
import net.somewhatcity.mixer.core.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class DspGui implements Listener {

    private final Map<UUID, Location> editingSession = new HashMap<>();
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private Component getTitle() {
        String title = MixerPlugin.getPlugin().getLocalizationManager().getMessage("dsp.gui_title");
        return MM.deserialize(title);
    }

    public void open(Player player, Location location) {
        editingSession.put(player.getUniqueId(), location);
        Inventory inv = Bukkit.createInventory(null, 27, getTitle());
        updateInventory(inv, location);
        player.openInventory(inv);
    }

    private void updateInventory(Inventory inv, Location location) {
        JsonObject dspData = Utils.loadNbtData(location, "mixer_dsp");
        if (dspData == null) dspData = new JsonObject();

        // --- Gain (Volume) ---
        double gain = 1.0;
        if (dspData.has("gain")) {
            gain = dspData.getAsJsonObject("gain").get("gain").getAsDouble();
        }
        ItemStack gainItem = createItem(Material.GOAT_HORN, "dsp.gain_name");
        addLore(gainItem,
                "<gray>Current: <yellow>" + Math.round(gain * 100) + "%",
                "",
                "<green>LMB: +10% <gray>| <green>Shift+LMB: +1%",
                "<red>RMB: -10% <gray>| <red>Shift+RMB: -1%"
        );
        inv.setItem(10, gainItem);

        // --- HighPass Filter (Bass Cut) ---
        float hpFreq = 0;
        if (dspData.has("highPassFilter")) {
            hpFreq = dspData.getAsJsonObject("highPassFilter").get("frequency").getAsFloat();
        }
        ItemStack hpItem = createItem(Material.IRON_BARS, "dsp.highpass_name");
        addLore(hpItem,
                "<gray>Frequency: <aqua>" + hpFreq + " Hz",
                "<gray>Status: " + (hpFreq > 0 ? "<green>ON" : "<red>OFF"),
                "",
                "<green>LMB: +50 Hz",
                "<red>RMB: -50 Hz"
        );
        inv.setItem(12, hpItem);

        // --- LowPass Filter (Treble Cut) ---
        float lpFreq = 20000;
        if (dspData.has("lowPassFilter")) {
            lpFreq = dspData.getAsJsonObject("lowPassFilter").get("frequency").getAsFloat();
        }
        ItemStack lpItem = createItem(Material.SOUL_SOIL, "dsp.lowpass_name");
        String lpStatus = lpFreq < 20000 ? "<green>ON" : "<red>OFF";
        addLore(lpItem,
                "<gray>Frequency: <aqua>" + lpFreq + " Hz",
                "<gray>Status: " + lpStatus,
                "",
                "<green>LMB: +500 Hz (Less Muffled)",
                "<red>RMB: -500 Hz (More Muffled)"
        );
        inv.setItem(14, lpItem);

        // --- Flanger ---
        boolean flangerOn = dspData.has("flangerEffect");
        ItemStack flangerItem = createItem(Material.AMETHYST_BLOCK, "dsp.flanger_name");
        addLore(flangerItem,
                "<gray>Status: " + (flangerOn ? "<green>ON" : "<red>OFF"),
                "",
                "<green>Click to Toggle On/Off"
        );
        inv.setItem(16, flangerItem);

        // --- Reset ---
        ItemStack resetItem = createItem(Material.BARRIER, "dsp.reset_name");
        addLore(resetItem, "<red>Click to reset all effects");
        inv.setItem(22, resetItem);

        // Fillers
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = filler.getItemMeta();
        meta.displayName(Component.empty());
        filler.setItemMeta(meta);
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, filler);
            }
        }
    }

    private ItemStack createItem(Material mat, String langKey) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        String name = MixerPlugin.getPlugin().getLocalizationManager().getMessage(langKey);
        meta.displayName(MM.deserialize(name).decoration(TextDecoration.ITALIC, false));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    private void addLore(ItemStack item, String... lines) {
        ItemMeta meta = item.getItemMeta();
        List<Component> lore = new ArrayList<>();
        for (String line : lines) {
            lore.add(MM.deserialize(line).decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);
        item.setItemMeta(meta);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!e.getView().title().equals(getTitle())) return;
        e.setCancelled(true);

        if (e.getClickedInventory() != e.getView().getTopInventory()) return;
        Player player = (Player) e.getWhoClicked();
        Location location = editingSession.get(player.getUniqueId());

        if (location == null || location.getBlock().getType() != Material.JUKEBOX) {
            player.closeInventory();
            return;
        }

        JsonObject dspData = Utils.loadNbtData(location, "mixer_dsp");
        if (dspData == null) dspData = new JsonObject();

        boolean updateAudio = false;
        boolean heavyUpdate = false;

        int slot = e.getSlot();

        // --- GAIN CONTROL ---
        if (slot == 10) {
            JsonObject gainObj = dspData.has("gain") ? dspData.getAsJsonObject("gain") : new JsonObject();
            double currentGain = gainObj.has("gain") ? gainObj.get("gain").getAsDouble() : 1.0;

            double change = 0.1;
            if (e.isShiftClick()) change = 0.01;

            if (e.isLeftClick()) currentGain += change;
            else if (e.isRightClick()) currentGain -= change;

            // Clamp 0.0 to 3.0
            currentGain = Math.max(0.0, Math.min(3.0, currentGain));

            gainObj.addProperty("gain", currentGain);
            dspData.add("gain", gainObj);
            updateAudio = true;
        }

        // --- HIGH PASS ---
        else if (slot == 12) {
            JsonObject hpObj = dspData.has("highPassFilter") ? dspData.getAsJsonObject("highPassFilter") : new JsonObject();
            float freq = hpObj.has("frequency") ? hpObj.get("frequency").getAsFloat() : 0;

            if (e.isLeftClick()) freq += 50;
            else if (e.isRightClick()) freq -= 50;

            freq = Math.max(0, Math.min(5000, freq)); // Max 5000Hz

            if (freq <= 0) {
                dspData.remove("highPassFilter");
            } else {
                hpObj.addProperty("frequency", freq);
                dspData.add("highPassFilter", hpObj);
            }
            heavyUpdate = true;
        }

        // --- LOW PASS ---
        else if (slot == 14) {
            JsonObject lpObj = dspData.has("lowPassFilter") ? dspData.getAsJsonObject("lowPassFilter") : new JsonObject();
            float freq = lpObj.has("frequency") ? lpObj.get("frequency").getAsFloat() : 20000;

            if (e.isLeftClick()) freq += 500;
            else if (e.isRightClick()) freq -= 500;

            freq = Math.max(500, Math.min(20000, freq));

            if (freq >= 20000) {
                dspData.remove("lowPassFilter");
            } else {
                lpObj.addProperty("frequency", freq);
                dspData.add("lowPassFilter", lpObj);
            }
            heavyUpdate = true;
        }

        // --- FLANGER ---
        else if (slot == 16) {
            if (dspData.has("flangerEffect")) {
                dspData.remove("flangerEffect");
            } else {
                JsonObject flanger = new JsonObject();
                flanger.addProperty("maxFlangerLength", 0.01);
                flanger.addProperty("wet", 0.5);
                flanger.addProperty("lfoFrequency", 0.2);
                dspData.add("flangerEffect", flanger);
            }
            heavyUpdate = true;
        }

        // --- RESET ---
        else if (slot == 22) {
            dspData = new JsonObject();
            heavyUpdate = true;
        }

        if (updateAudio || heavyUpdate) {
            Utils.saveNbtData(location, "mixer_dsp", dspData);
            updateInventory(e.getInventory(), location);

            IMixerAudioPlayer audioPlayer = MixerPlugin.getPlugin().playerHashMap().get(location);
            if (audioPlayer != null) {
                if (heavyUpdate) {
                    audioPlayer.reloadDspSettings();
                    audioPlayer.loadDsp();
                } else {
                    audioPlayer.reloadDspSettings();
                    audioPlayer.updateVolume();
                }
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        editingSession.remove(e.getPlayer().getUniqueId());
    }
}