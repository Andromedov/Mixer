package me.andromedov.mixer.core.gui;

import com.google.gson.JsonObject;
import me.andromedov.mixer.api.gui.DspMenuContext;
import me.andromedov.mixer.api.gui.DspMenuElement;
import me.andromedov.mixer.api.gui.DspMenuItemContext;
import me.andromedov.mixer.api.gui.DspMenuTargetType;
import me.andromedov.mixer.core.MixerPlugin;
import me.andromedov.mixer.core.audio.EntityMixerAudioPlayer;
import me.andromedov.mixer.core.audio.IMixerAudioPlayer;
import me.andromedov.mixer.core.util.MixerScheduler;
import me.andromedov.mixer.core.util.Utils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DspGui implements Listener {
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private Component getTitle() {
        String title = MixerPlugin.getPlugin().getLocalizationManager().getMessage("dsp.gui_title");
        return MM.deserialize(title);
    }

    public void open(Player player, Location location) {
        MixerScheduler.requireOwned(location, "open the DSP editor");
        open(player, (Object) location.clone());
    }

    /** Opens the DSP GUI for a portable speaker. */
    public void open(Player player, UUID speakerId) {
        MixerScheduler.requireOwned(player, "open the portable speaker DSP editor");
        open(player, (Object) speakerId);
    }

    private void open(Player player, Object target) {
        JsonObject dspData = loadDspData(target);
        DspMenuContext context = menuContext(player, target, dspData);
        Component title = MixerPlugin.getPlugin().api().dspMenus().renderTitle(context, getTitle());
        DspHolder holder = new DspHolder(player.getUniqueId(), target, title);
        updateInventory(holder.getInventory(), holder, player, dspData);
        player.openInventory(holder.getInventory());
    }

    private void updateInventory(Inventory inventory, DspHolder holder, Player player) {
        updateInventory(inventory, holder, player, loadDspData(holder.target));
    }

    private void updateInventory(Inventory inventory, DspHolder holder, Player player,
                                 JsonObject dspData) {
        DspMenuContext context = menuContext(player, holder.target, dspData);

        ItemStack gainItem = createItem(Material.GOAT_HORN, "dsp.gain_name");
        List<String> gainLore = MixerPlugin.getPlugin().getLocalizationManager()
                .getMessageList("dsp.gain_lore");
        replacePlaceholder(gainLore, "%gain%", String.valueOf(Math.round(context.gain() * 100)));
        addLore(gainItem, gainLore);
        inventory.setItem(10, menuItem(context, DspMenuElement.GAIN, 10, gainItem));

        ItemStack hpItem = createItem(Material.IRON_BARS, "dsp.highpass_name");
        List<String> hpLore = MixerPlugin.getPlugin().getLocalizationManager()
                .getMessageList("dsp.highpass_lore");
        replacePlaceholder(hpLore, "%freq%", String.valueOf(context.highPassFrequency()));
        replacePlaceholder(hpLore, "%status%",
                context.highPassFrequency() > 0 ? "<green>ON" : "<red>OFF");
        addLore(hpItem, hpLore);
        inventory.setItem(12, menuItem(context, DspMenuElement.HIGH_PASS_FILTER, 12, hpItem));

        ItemStack lpItem = createItem(Material.SOUL_SOIL, "dsp.lowpass_name");
        List<String> lpLore = MixerPlugin.getPlugin().getLocalizationManager()
                .getMessageList("dsp.lowpass_lore");
        replacePlaceholder(lpLore, "%freq%", String.valueOf(context.lowPassFrequency()));
        replacePlaceholder(lpLore, "%status%",
                context.lowPassFrequency() < 20000 ? "<green>ON" : "<red>OFF");
        addLore(lpItem, lpLore);
        inventory.setItem(14, menuItem(context, DspMenuElement.LOW_PASS_FILTER, 14, lpItem));

        ItemStack flangerItem = createItem(Material.AMETHYST_BLOCK, "dsp.flanger_name");
        List<String> flangerLore = MixerPlugin.getPlugin().getLocalizationManager()
                .getMessageList("dsp.flanger_lore");
        replacePlaceholder(flangerLore, "%status%",
                context.flangerEnabled() ? "<green>ON" : "<red>OFF");
        addLore(flangerItem, flangerLore);
        DspMenuElement flangerElement = context.flangerEnabled()
                ? DspMenuElement.FLANGER_ENABLED : DspMenuElement.FLANGER_DISABLED;
        inventory.setItem(16, menuItem(context, flangerElement, 16, flangerItem));

        ItemStack resetItem = createItem(Material.BARRIER, "dsp.reset_name");
        addLore(resetItem, MixerPlugin.getPlugin().getLocalizationManager()
                .getMessageList("dsp.reset_lore"));
        inventory.setItem(22, menuItem(context, DspMenuElement.RESET, 22, resetItem));

        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (inventory.getItem(slot) != null) continue;
            ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            filler.editMeta(meta -> meta.displayName(Component.empty()));
            inventory.setItem(slot, menuItem(context, DspMenuElement.FILLER, slot, filler));
        }
    }

    private ItemStack menuItem(DspMenuContext menu, DspMenuElement element,
                               int slot, ItemStack defaultItem) {
        return MixerPlugin.getPlugin().api().dspMenus().renderItem(
                new DspMenuItemContext(menu, element, slot), defaultItem);
    }

    private DspMenuContext menuContext(Player player, Object target, JsonObject dspData) {
        double gain = dspData.has("gain")
                ? dspData.getAsJsonObject("gain").get("gain").getAsDouble() : 1.0;
        float highPass = dspData.has("highPassFilter")
                ? dspData.getAsJsonObject("highPassFilter").get("frequency").getAsFloat() : 0;
        float lowPass = dspData.has("lowPassFilter")
                ? dspData.getAsJsonObject("lowPassFilter").get("frequency").getAsFloat() : 20000;
        boolean flanger = dspData.has("flangerEffect");
        if (target instanceof Location location) {
            return new DspMenuContext(player, DspMenuTargetType.JUKEBOX, location, null,
                    gain, highPass, lowPass, flanger);
        }
        return new DspMenuContext(player, DspMenuTargetType.PORTABLE_SPEAKER, null,
                (UUID) target, gain, highPass, lowPass, flanger);
    }

    private JsonObject loadDspData(Object target) {
        JsonObject data;
        if (target instanceof Location location) {
            data = Utils.loadNbtData(location, "mixer_dsp");
        } else {
            data = MixerPlugin.getPlugin().getDatabase().loadSpeakerDsp((UUID) target);
        }
        return data == null ? new JsonObject() : data;
    }

    private void replacePlaceholder(List<String> list, String target, String replacement) {
        list.replaceAll(line -> line.replace(target, replacement));
    }

    private ItemStack createItem(Material material, String langKey) {
        ItemStack item = new ItemStack(material);
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
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof DspHolder holder)) return;
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;

        Player player = (Player) event.getWhoClicked();
        if (!holder.viewerId.equals(player.getUniqueId())) {
            player.closeInventory();
            return;
        }

        Object target = holder.target;
        if (target instanceof Location location) {
            if (!Bukkit.isOwnedByCurrentRegion(location)
                    || location.getBlock().getType() != Material.JUKEBOX) {
                player.closeInventory();
                return;
            }
        }

        JsonObject dspData = loadDspData(target);
        boolean updateAudio = false;
        boolean heavyUpdate = false;

        int slot = event.getSlot();
        if (slot == 10) {
            JsonObject gainObject = dspData.has("gain")
                    ? dspData.getAsJsonObject("gain") : new JsonObject();
            double gain = gainObject.has("gain") ? gainObject.get("gain").getAsDouble() : 1.0;
            double change = event.isShiftClick() ? 0.01 : 0.1;
            if (event.isLeftClick()) gain += change;
            else if (event.isRightClick()) gain -= change;
            gainObject.addProperty("gain", Math.max(0.0, Math.min(3.0, gain)));
            dspData.add("gain", gainObject);
            updateAudio = true;
        } else if (slot == 12) {
            JsonObject filter = dspData.has("highPassFilter")
                    ? dspData.getAsJsonObject("highPassFilter") : new JsonObject();
            float frequency = filter.has("frequency") ? filter.get("frequency").getAsFloat() : 0;
            if (event.isLeftClick()) frequency += 50;
            else if (event.isRightClick()) frequency -= 50;
            frequency = Math.max(0, Math.min(5000, frequency));
            if (frequency <= 0) dspData.remove("highPassFilter");
            else {
                filter.addProperty("frequency", frequency);
                dspData.add("highPassFilter", filter);
            }
            heavyUpdate = true;
        } else if (slot == 14) {
            JsonObject filter = dspData.has("lowPassFilter")
                    ? dspData.getAsJsonObject("lowPassFilter") : new JsonObject();
            float frequency = filter.has("frequency")
                    ? filter.get("frequency").getAsFloat() : 20000;
            if (event.isLeftClick()) frequency += 500;
            else if (event.isRightClick()) frequency -= 500;
            frequency = Math.max(500, Math.min(20000, frequency));
            if (frequency >= 20000) dspData.remove("lowPassFilter");
            else {
                filter.addProperty("frequency", frequency);
                dspData.add("lowPassFilter", filter);
            }
            heavyUpdate = true;
        } else if (slot == 16) {
            if (dspData.has("flangerEffect")) dspData.remove("flangerEffect");
            else {
                JsonObject flanger = new JsonObject();
                flanger.addProperty("maxFlangerLength", 0.01);
                flanger.addProperty("wet", 0.5);
                flanger.addProperty("lfoFrequency", 0.2);
                dspData.add("flangerEffect", flanger);
            }
            heavyUpdate = true;
        } else if (slot == 22) {
            dspData = new JsonObject();
            heavyUpdate = true;
        }

        if (!updateAudio && !heavyUpdate) return;
        if (target instanceof Location location) {
            Utils.saveNbtData(location, "mixer_dsp", dspData);
            IMixerAudioPlayer audioPlayer = MixerPlugin.getPlugin().playerHashMap().get(location);
            if (audioPlayer != null) {
                audioPlayer.reloadDspSettings();
                if (heavyUpdate) audioPlayer.loadDsp();
                else audioPlayer.updateVolume();
            }
        } else {
            UUID speakerId = (UUID) target;
            MixerPlugin.getPlugin().getDatabase().saveSpeakerDsp(speakerId, dspData);
            EntityMixerAudioPlayer audioPlayer = MixerPlugin.getPlugin()
                    .getPortablePlayerMap().get(player.getUniqueId());
            if (audioPlayer != null && speakerId.equals(audioPlayer.getSourceItemId())) {
                audioPlayer.setDspSettings(dspData);
                if (heavyUpdate) audioPlayer.loadDsp();
                else audioPlayer.updateVolume();
            }
        }
        updateInventory(holder.getInventory(), holder, player);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof DspHolder)) return;
        if (event.getRawSlots().stream().anyMatch(slot -> slot < 27)) event.setCancelled(true);
    }

    private static final class DspHolder implements InventoryHolder {
        private final UUID viewerId;
        private final Object target;
        private final Inventory inventory;

        private DspHolder(UUID viewerId, Object target, Component title) {
            this.viewerId = viewerId;
            this.target = target instanceof Location location ? location.clone() : target;
            this.inventory = Bukkit.createInventory(this, 27, title);
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }
}
