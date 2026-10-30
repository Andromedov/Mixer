package me.andromedov.mixer.core.gui;

import com.google.gson.JsonObject;
import me.andromedov.mixer.api.gui.DspMenuContext;
import me.andromedov.mixer.api.gui.DspMenuElement;
import me.andromedov.mixer.api.gui.DspMenuItemContext;
import me.andromedov.mixer.api.gui.DspMenuTargetType;
import me.andromedov.mixer.core.MixerPlugin;
import me.andromedov.mixer.core.audio.EntityMixerAudioPlayer;
import me.andromedov.mixer.core.audio.IMixerAudioPlayer;
import me.andromedov.mixer.core.dsp.DspSettings;
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
    private static final int INVENTORY_SIZE = 27;
    private static final int GAIN_SLOT = 10;
    private static final int HIGH_PASS_SLOT = 12;
    private static final int LOW_PASS_SLOT = 14;
    private static final int FLANGER_SLOT = 16;
    private static final int RESET_SLOT = 22;

    private Component getTitle() {
        String title = MixerPlugin.getPlugin().getLocalizationManager().getMessage("dsp.gui_title");
        return MM.deserialize(title);
    }

    public void open(Player player, Location location) {
        MixerScheduler.requireOwned(location, "open the DSP editor");
        open(player, new JukeboxTarget(location));
    }

    /** Opens the DSP GUI for a portable speaker. */
    public void open(Player player, UUID speakerId) {
        MixerScheduler.requireOwned(player, "open the portable speaker DSP editor");
        open(player, new SpeakerTarget(speakerId));
    }

    private void open(Player player, DspTarget target) {
        DspSettings settings = loadSettings(target);
        DspMenuContext context = menuContext(player, target, settings);
        Component title = MixerPlugin.getPlugin().api().dspMenus().renderTitle(context, getTitle());
        DspHolder holder = new DspHolder(player.getUniqueId(), target, title);
        updateInventory(holder.getInventory(), holder, player, settings);
        player.openInventory(holder.getInventory());
    }

    private void updateInventory(Inventory inventory, DspHolder holder, Player player) {
        updateInventory(inventory, holder, player, loadSettings(holder.target));
    }

    private void updateInventory(Inventory inventory, DspHolder holder, Player player,
                                 DspSettings settings) {
        DspMenuContext context = menuContext(player, holder.target, settings);

        ItemStack gainItem = createItem(Material.GOAT_HORN, "dsp.gain_name");
        List<String> gainLore = MixerPlugin.getPlugin().getLocalizationManager()
                .getMessageList("dsp.gain_lore");
        replacePlaceholder(gainLore, "%gain%", String.valueOf(Math.round(context.gain() * 100)));
        addLore(gainItem, gainLore);
        inventory.setItem(GAIN_SLOT,
                menuItem(context, DspMenuElement.GAIN, GAIN_SLOT, gainItem));

        ItemStack hpItem = createItem(Material.IRON_BARS, "dsp.highpass_name");
        List<String> hpLore = MixerPlugin.getPlugin().getLocalizationManager()
                .getMessageList("dsp.highpass_lore");
        replacePlaceholder(hpLore, "%freq%", String.valueOf(context.highPassFrequency()));
        replacePlaceholder(hpLore, "%status%",
                context.highPassFrequency() > 0 ? "<green>ON" : "<red>OFF");
        addLore(hpItem, hpLore);
        inventory.setItem(HIGH_PASS_SLOT,
                menuItem(context, DspMenuElement.HIGH_PASS_FILTER, HIGH_PASS_SLOT, hpItem));

        ItemStack lpItem = createItem(Material.SOUL_SOIL, "dsp.lowpass_name");
        List<String> lpLore = MixerPlugin.getPlugin().getLocalizationManager()
                .getMessageList("dsp.lowpass_lore");
        replacePlaceholder(lpLore, "%freq%", String.valueOf(context.lowPassFrequency()));
        replacePlaceholder(lpLore, "%status%",
                context.lowPassFrequency() < 20000 ? "<green>ON" : "<red>OFF");
        addLore(lpItem, lpLore);
        inventory.setItem(LOW_PASS_SLOT,
                menuItem(context, DspMenuElement.LOW_PASS_FILTER, LOW_PASS_SLOT, lpItem));

        ItemStack flangerItem = createItem(Material.AMETHYST_BLOCK, "dsp.flanger_name");
        List<String> flangerLore = MixerPlugin.getPlugin().getLocalizationManager()
                .getMessageList("dsp.flanger_lore");
        replacePlaceholder(flangerLore, "%status%",
                context.flangerEnabled() ? "<green>ON" : "<red>OFF");
        addLore(flangerItem, flangerLore);
        DspMenuElement flangerElement = context.flangerEnabled()
                ? DspMenuElement.FLANGER_ENABLED : DspMenuElement.FLANGER_DISABLED;
        inventory.setItem(FLANGER_SLOT,
                menuItem(context, flangerElement, FLANGER_SLOT, flangerItem));

        ItemStack resetItem = createItem(Material.BARRIER, "dsp.reset_name");
        addLore(resetItem, MixerPlugin.getPlugin().getLocalizationManager()
                .getMessageList("dsp.reset_lore"));
        inventory.setItem(RESET_SLOT,
                menuItem(context, DspMenuElement.RESET, RESET_SLOT, resetItem));

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

    private DspMenuContext menuContext(Player player, DspTarget target, DspSettings settings) {
        if (target instanceof JukeboxTarget jukebox) {
            return new DspMenuContext(player, DspMenuTargetType.JUKEBOX, jukebox.location(), null,
                    settings.gain(), settings.highPassFrequency(), settings.lowPassFrequency(),
                    settings.flangerEnabled());
        }
        SpeakerTarget speaker = (SpeakerTarget) target;
        return new DspMenuContext(player, DspMenuTargetType.PORTABLE_SPEAKER, null,
                speaker.id(), settings.gain(), settings.highPassFrequency(),
                settings.lowPassFrequency(), settings.flangerEnabled());
    }

    private DspSettings loadSettings(DspTarget target) {
        JsonObject data;
        if (target instanceof JukeboxTarget jukebox) {
            data = Utils.loadNbtData(jukebox.location(), "mixer_dsp");
        } else {
            data = MixerPlugin.getPlugin().getDatabase().loadSpeakerDsp(((SpeakerTarget) target).id());
        }
        return DspSettings.fromJson(data);
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

        DspTarget target = holder.target;
        if (target instanceof JukeboxTarget jukebox) {
            Location location = jukebox.location();
            if (!Bukkit.isOwnedByCurrentRegion(location)
                    || location.getBlock().getType() != Material.JUKEBOX) {
                player.closeInventory();
                return;
            }
        }

        DspSettings settings = loadSettings(target);
        DspUpdateType updateType = updateSettings(settings, event);
        if (updateType == DspUpdateType.NONE) return;
        JsonObject dspData = settings.toJson();
        if (target instanceof JukeboxTarget jukebox) {
            Location location = jukebox.location();
            Utils.saveNbtData(location, "mixer_dsp", dspData);
            IMixerAudioPlayer audioPlayer = MixerPlugin.getPlugin().playerHashMap().get(location);
            if (audioPlayer != null) {
                audioPlayer.reloadDspSettings();
                if (updateType == DspUpdateType.FULL) audioPlayer.loadDsp();
                else audioPlayer.updateVolume();
            }
        } else {
            UUID speakerId = ((SpeakerTarget) target).id();
            MixerPlugin.getPlugin().getDatabase().saveSpeakerDsp(speakerId, dspData);
            EntityMixerAudioPlayer audioPlayer = MixerPlugin.getPlugin()
                    .getPortablePlayerMap().get(player.getUniqueId());
            if (audioPlayer != null && speakerId.equals(audioPlayer.getSourceItemId())) {
                audioPlayer.setDspSettings(dspData);
                if (updateType == DspUpdateType.FULL) audioPlayer.loadDsp();
                else audioPlayer.updateVolume();
            }
        }
        updateInventory(holder.getInventory(), holder, player);
    }

    private DspUpdateType updateSettings(DspSettings settings, InventoryClickEvent event) {
        int slot = event.getSlot();
        if (slot == GAIN_SLOT && (event.isLeftClick() || event.isRightClick())) {
            double step = event.isShiftClick() ? 0.01 : 0.1;
            settings.adjustGain(event.isLeftClick() ? step : -step);
            return DspUpdateType.VOLUME;
        }
        if (slot == HIGH_PASS_SLOT && (event.isLeftClick() || event.isRightClick())) {
            settings.adjustHighPass(event.isLeftClick() ? 50 : -50);
            return DspUpdateType.FULL;
        }
        if (slot == LOW_PASS_SLOT && (event.isLeftClick() || event.isRightClick())) {
            settings.adjustLowPass(event.isLeftClick() ? 500 : -500);
            return DspUpdateType.FULL;
        }
        if (slot == FLANGER_SLOT) {
            settings.toggleFlanger();
            return DspUpdateType.FULL;
        }
        if (slot == RESET_SLOT) {
            settings.reset();
            return DspUpdateType.FULL;
        }
        return DspUpdateType.NONE;
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof DspHolder)) return;
        if (event.getRawSlots().stream().anyMatch(slot -> slot < INVENTORY_SIZE)) {
            event.setCancelled(true);
        }
    }

    private static final class DspHolder implements InventoryHolder {
        private final UUID viewerId;
        private final DspTarget target;
        private final Inventory inventory;

        private DspHolder(UUID viewerId, DspTarget target, Component title) {
            this.viewerId = viewerId;
            this.target = target;
            this.inventory = Bukkit.createInventory(this, INVENTORY_SIZE, title);
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }

    private sealed interface DspTarget permits JukeboxTarget, SpeakerTarget {}

    private record JukeboxTarget(Location location) implements DspTarget {
        private JukeboxTarget {
            location = location.clone();
        }

        @Override
        public Location location() {
            return location.clone();
        }
    }

    private record SpeakerTarget(UUID id) implements DspTarget {}

    private enum DspUpdateType {
        NONE,
        VOLUME,
        FULL
    }
}
