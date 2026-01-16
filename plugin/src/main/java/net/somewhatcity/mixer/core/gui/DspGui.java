package net.somewhatcity.mixer.core.gui;

import com.google.gson.JsonObject;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.somewhatcity.mixer.core.MixerPlugin;
import net.somewhatcity.mixer.core.audio.EntityMixerAudioPlayer;
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

    private final Map<UUID, Object> editingSession = new HashMap<>();
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

    public void open(Player player, UUID targetPlayerId) {
        editingSession.put(player.getUniqueId(), targetPlayerId);
        Inventory inv = Bukkit.createInventory(null, 27, getTitle());
        updateInventory(inv, targetPlayerId);
        player.openInventory(inv);
    }

    private void updateInventory(Inventory inv, Object target) {
        JsonObject dspData = null;

        if (target instanceof Location loc) {
            dspData = Utils.loadNbtData(loc, "mixer_dsp");
        } else if (target instanceof UUID uid) {
            EntityMixerAudioPlayer player = MixerPlugin.getPlugin().getPortablePlayerMap().get(uid);
            if (player != null) {
                dspData = player.getDspSettings();
            }
        }

        if (dspData == null) dspData = new JsonObject();

        // --- Gain (Volume) ---
        double gain = 1.0;
        if (dspData.has("gain")) {
            gain = dspData.getAsJsonObject("gain").get("gain").getAsDouble();
        }
        ItemStack gainItem = createItem(Material.GOAT_HORN, "dsp.gain_name");
        List<String> gainLore = MixerPlugin.getPlugin().getLocalizationManager().getMessageList("dsp.gain_lore");
        replacePlaceholder(gainLore, "%gain%", String.valueOf(Math.round(gain * 100)));
        addLore(gainItem, gainLore);
        inv.setItem(10, gainItem);

        // --- HighPass Filter (Bass Cut) ---
        float hpFreq = 0;
        if (dspData.has("highPassFilter")) {
            hpFreq = dspData.getAsJsonObject("highPassFilter").get("frequency").getAsFloat();
        }
        ItemStack hpItem = createItem(Material.IRON_BARS, "dsp.highpass_name");
        List<String> hpLore = MixerPlugin.getPlugin().getLocalizationManager().getMessageList("dsp.highpass_lore");
        replacePlaceholder(hpLore, "%freq%", String.valueOf(hpFreq));
        replacePlaceholder(hpLore, "%status%", (hpFreq > 0 ? "<green>ON" : "<red>OFF"));
        addLore(hpItem, hpLore);
        inv.setItem(12, hpItem);

        // --- LowPass Filter (Treble Cut) ---
        float lpFreq = 20000;
        if (dspData.has("lowPassFilter")) {
            lpFreq = dspData.getAsJsonObject("lowPassFilter").get("frequency").getAsFloat();
        }
        ItemStack lpItem = createItem(Material.SOUL_SOIL, "dsp.lowpass_name");
        String lpStatus = lpFreq < 20000 ? "<green>ON" : "<red>OFF";
        List<String> lpLore = MixerPlugin.getPlugin().getLocalizationManager().getMessageList("dsp.lowpass_lore");
        replacePlaceholder(lpLore, "%freq%", String.valueOf(lpFreq));
        replacePlaceholder(lpLore, "%status%", lpStatus);
        addLore(lpItem, lpLore);
        inv.setItem(14, lpItem);

        // --- Flanger ---
        boolean flangerOn = dspData.has("flangerEffect");
        ItemStack flangerItem = createItem(Material.AMETHYST_BLOCK, "dsp.flanger_name");
        List<String> flangerLore = MixerPlugin.getPlugin().getLocalizationManager().getMessageList("dsp.flanger_lore");
        replacePlaceholder(flangerLore, "%status%", (flangerOn ? "<green>ON" : "<red>OFF"));
        addLore(flangerItem, flangerLore);
        inv.setItem(16, flangerItem);

        // --- Reset ---
        ItemStack resetItem = createItem(Material.BARRIER, "dsp.reset_name");
        List<String> resetLore = MixerPlugin.getPlugin().getLocalizationManager().getMessageList("dsp.reset_lore");
        addLore(resetItem, resetLore);
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

    private void replacePlaceholder(List<String> list, String target, String replacement) {
        list.replaceAll(s -> s.replace(target, replacement));
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

    private void addLore(ItemStack item, List<String> lines) {
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
        Object target = editingSession.get(player.getUniqueId());

        if (target == null) {
            player.closeInventory();
            return;
        }

        if (target instanceof Location loc) {
            if (loc.getBlock().getType() != Material.JUKEBOX) {
                player.closeInventory();
                return;
            }
        }

        JsonObject dspData = null;
        if (target instanceof Location loc) {
            dspData = Utils.loadNbtData(loc, "mixer_dsp");
        } else if (target instanceof UUID uid) {
            EntityMixerAudioPlayer emp = MixerPlugin.getPlugin().getPortablePlayerMap().get(uid);
            if (emp != null) dspData = emp.getDspSettings();
        }

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
            if (target instanceof Location loc) {
                Utils.saveNbtData(loc, "mixer_dsp", dspData);
                IMixerAudioPlayer audioPlayer = MixerPlugin.getPlugin().playerHashMap().get(loc);
                if (audioPlayer != null) {
                    audioPlayer.reloadDspSettings();
                    if (heavyUpdate) {
                        audioPlayer.loadDsp();
                    } else {
                        audioPlayer.updateVolume();
                    }
                }
            } else if (target instanceof UUID uid) {
                EntityMixerAudioPlayer emp = MixerPlugin.getPlugin().getPortablePlayerMap().get(uid);
                if (emp != null) {
                    emp.setDspSettings(dspData);
                    if (heavyUpdate) {
                        emp.loadDsp();
                    } else {
                        emp.updateVolume();
                    }
                }
            }
            updateInventory(e.getInventory(), target);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getView().title().equals(getTitle())) {
            editingSession.remove(e.getPlayer().getUniqueId());
        }
    }
}