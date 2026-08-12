package me.andromedov.mixer.core.gui;

import me.andromedov.mixer.api.disc.MixerDisc;
import me.andromedov.mixer.api.playback.MixerPlaybackOrigin;
import me.andromedov.mixer.core.MixerPlugin;
import me.andromedov.mixer.core.audio.EntityMixerAudioPlayer;
import me.andromedov.mixer.core.playlist.PlaylistCartridge;
import me.andromedov.mixer.core.util.MessageUtil;
import me.andromedov.mixer.core.util.PlaybackAuthorization;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public class PortableSpeakerGui implements Listener {
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private Component getTitle() {
        String title = MixerPlugin.getPlugin().getLocalizationManager()
                .getMessage("portableSpeaker.portable_speaker_gui_name");
        return MM.deserialize(title);
    }

    public void open(Player player, UUID speakerId) {
        PortableSpeakerHolder holder = new PortableSpeakerHolder(speakerId);
        Inventory inv = Bukkit.createInventory(holder, 9, getTitle());
        holder.inventory = inv;

        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        fillerMeta.displayName(Component.empty());
        filler.setItemMeta(fillerMeta);
        for (int i = 0; i < 9; i++) if (i != 4) inv.setItem(i, filler);

        ItemStack start = new ItemStack(Material.LIME_CONCRETE);
        start.editMeta(meta -> meta.displayName(MM.deserialize(MixerPlugin.getPlugin().getLocalizationManager()
                .getMessage("portableSpeaker.portable_speaker_start_button"))
                .decoration(TextDecoration.ITALIC, false)));
        inv.setItem(0, start);

        ItemStack dsp = new ItemStack(Material.AMETHYST_SHARD);
        dsp.editMeta(meta -> {
            meta.displayName(MM.deserialize(MixerPlugin.getPlugin().getLocalizationManager()
                    .getMessage("dsp.gui_title")).decoration(TextDecoration.ITALIC, false));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        });
        inv.setItem(2, dsp);

        ItemStack stop = new ItemStack(Material.RED_CONCRETE);
        stop.editMeta(meta -> meta.displayName(MM.deserialize(MixerPlugin.getPlugin().getLocalizationManager()
                .getMessage("portableSpeaker.portable_speaker_stop_button"))
                .decoration(TextDecoration.ITALIC, false)));
        inv.setItem(8, stop);
        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof PortableSpeakerHolder holder)) return;

        if (event.getClickedInventory() == event.getView().getTopInventory()) {
            if (event.getSlot() != 4) event.setCancelled(true);
        } else if (event.getClickedInventory() == event.getView().getBottomInventory()) {
            if (event.isShiftClick()) event.setCancelled(true);
            return;
        }

        Player player = (Player) event.getWhoClicked();
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;

        if (event.getSlot() == 0) {
            play(player, holder, event.getView().getTopInventory().getItem(4));
        } else if (event.getSlot() == 2) {
            MixerPlugin.getPlugin().getDspGui().open(player, holder.speakerId);
        } else if (event.getSlot() == 8) {
            EntityMixerAudioPlayer active = MixerPlugin.getPlugin().getPortablePlayerMap().get(player.getUniqueId());
            if (active != null) {
                active.stop();
                MessageUtil.sendActionBarMsg(player, "playback_stop");
            } else {
                MessageUtil.sendActionBarMsg(player, "failed_to_stop");
            }
            player.closeInventory();
        }
    }

    private void play(Player player, PortableSpeakerHolder holder, ItemStack media) {
        if (media == null || media.getType().isAir()) {
            MessageUtil.sendActionBarMsg(player, "no_disc_inserted");
            return;
        }

        MixerPlugin plugin = MixerPlugin.getPlugin();
        List<String> sources = new ArrayList<>();
        java.util.Optional<MixerDisc> disc = plugin.api().discs().readDisc(media);
        if (disc.isPresent()) {
            String source = disc.orElseThrow().source();
            if (PlaybackAuthorization.allow(MixerPlaybackOrigin.PORTABLE_SPEAKER, source,
                    media, player, player.getLocation())) sources.add(source);
        } else if (plugin.arePlaylistCartridgesEnabled() && plugin.getPlaylistCartridges().isCartridge(media)) {
            PlaylistCartridge cartridge = plugin.getPlaylistCartridges().read(media).orElse(null);
            if (cartridge == null) {
                MessageUtil.sendActionBarMsg(player, "invalid_cartridge");
                return;
            }
            for (var track : cartridge.tracks()) {
                if (PlaybackAuthorization.allow(MixerPlaybackOrigin.PORTABLE_SPEAKER, track.source(),
                        media, player, player.getLocation())) sources.add(track.source());
            }
            if (cartridge.tracks().isEmpty()) {
                MessageUtil.sendActionBarMsg(player, "empty_cartridge");
                return;
            }
        } else {
            MessageUtil.sendActionBarMsg(player, "not_valid_media");
            return;
        }

        if (sources.isEmpty()) return;
        EntityMixerAudioPlayer existing = plugin.getPortablePlayerMap().get(player.getUniqueId());
        if (existing != null) existing.stop();

        EntityMixerAudioPlayer portablePlayer = new EntityMixerAudioPlayer(player);
        portablePlayer.setSourceItemId(holder.speakerId);
        portablePlayer.clearAndPlay(sources.toArray(String[]::new));
        plugin.getPortablePlayerMap().put(player.getUniqueId(), portablePlayer);
        MessageUtil.sendActionBarMsg(player, "playback_start");
        player.closeInventory();
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof PortableSpeakerHolder)) return;
        if (event.getRawSlots().stream().anyMatch(slot -> slot < 9 && slot != 4)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof PortableSpeakerHolder)) return;
        ItemStack media = event.getInventory().getItem(4);
        if (media == null || media.getType().isAir()) return;
        HashMap<Integer, ItemStack> leftover = event.getPlayer().getInventory().addItem(media);
        leftover.values().forEach(item -> event.getPlayer().getWorld()
                .dropItem(event.getPlayer().getLocation(), item));
    }

    private static final class PortableSpeakerHolder implements InventoryHolder {
        private final UUID speakerId;
        private Inventory inventory;

        private PortableSpeakerHolder(UUID speakerId) {
            this.speakerId = speakerId;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
