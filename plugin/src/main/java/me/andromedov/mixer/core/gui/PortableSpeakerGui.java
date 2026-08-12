package me.andromedov.mixer.core.gui;

import me.andromedov.mixer.api.disc.MixerDisc;
import me.andromedov.mixer.api.playback.MixerPlaybackOrigin;
import me.andromedov.mixer.api.playlist.MixerPlaylist;
import me.andromedov.mixer.api.playlist.MixerPlaylistTrack;
import me.andromedov.mixer.core.MixerPlugin;
import me.andromedov.mixer.core.audio.EntityMixerAudioPlayer;
import me.andromedov.mixer.core.playlist.PortablePlaylistSession;
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
    private static final int INVENTORY_SIZE = 18;
    private static final int MEDIA_SLOT = 4;
    private static final int START_SLOT = 0;
    private static final int DSP_SLOT = 2;
    private static final int STOP_SLOT = 8;
    private static final int PREVIOUS_SLOT = 10;
    private static final int SHUFFLE_SLOT = 11;
    private static final int REPEAT_SLOT = 13;
    private static final int PAUSE_SLOT = 15;
    private static final int NEXT_SLOT = 16;
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private Component getTitle() {
        String title = MixerPlugin.getPlugin().getLocalizationManager()
                .getMessage("portableSpeaker.portable_speaker_gui_name");
        return MM.deserialize(title);
    }

    public void open(Player player, UUID speakerId) {
        PortableSpeakerHolder holder = new PortableSpeakerHolder(speakerId);
        Inventory inv = Bukkit.createInventory(holder, INVENTORY_SIZE, getTitle());
        holder.inventory = inv;

        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        fillerMeta.displayName(Component.empty());
        filler.setItemMeta(fillerMeta);
        for (int i = 0; i < INVENTORY_SIZE; i++) if (i != MEDIA_SLOT) inv.setItem(i, filler);

        ItemStack start = new ItemStack(Material.LIME_CONCRETE);
        start.editMeta(meta -> meta.displayName(MM.deserialize(MixerPlugin.getPlugin().getLocalizationManager()
                .getMessage("portableSpeaker.portable_speaker_start_button"))
                .decoration(TextDecoration.ITALIC, false)));
        inv.setItem(START_SLOT, start);

        ItemStack dsp = new ItemStack(Material.AMETHYST_SHARD);
        dsp.editMeta(meta -> {
            meta.displayName(MM.deserialize(MixerPlugin.getPlugin().getLocalizationManager()
                    .getMessage("dsp.gui_title")).decoration(TextDecoration.ITALIC, false));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        });
        inv.setItem(DSP_SLOT, dsp);

        ItemStack stop = new ItemStack(Material.RED_CONCRETE);
        stop.editMeta(meta -> meta.displayName(MM.deserialize(MixerPlugin.getPlugin().getLocalizationManager()
                .getMessage("portableSpeaker.portable_speaker_stop_button"))
                .decoration(TextDecoration.ITALIC, false)));
        inv.setItem(STOP_SLOT, stop);
        updateControls(inv, holder, player);
        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof PortableSpeakerHolder holder)) return;

        if (event.getClickedInventory() == event.getView().getTopInventory()) {
            if (event.getSlot() != MEDIA_SLOT) event.setCancelled(true);
        } else if (event.getClickedInventory() == event.getView().getBottomInventory()) {
            if (event.isShiftClick()) event.setCancelled(true);
            return;
        }

        Player player = (Player) event.getWhoClicked();
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;

        if (event.getSlot() == START_SLOT) {
            play(player, holder, event.getView().getTopInventory().getItem(MEDIA_SLOT));
        } else if (event.getSlot() == PREVIOUS_SLOT) {
            control(player, holder, PortablePlaylistSession::previous);
            updateControls(event.getView().getTopInventory(), holder, player);
        } else if (event.getSlot() == DSP_SLOT) {
            MixerPlugin.getPlugin().getDspGui().open(player, holder.speakerId);
        } else if (event.getSlot() == SHUFFLE_SLOT) {
            PortablePlaylistSession session = session(player, holder);
            if (session != null) session.toggleShuffle();
            updateControls(event.getView().getTopInventory(), holder, player);
        } else if (event.getSlot() == REPEAT_SLOT) {
            PortablePlaylistSession session = session(player, holder);
            if (session != null) session.cycleRepeatMode();
            updateControls(event.getView().getTopInventory(), holder, player);
        } else if (event.getSlot() == PAUSE_SLOT) {
            PortablePlaylistSession session = session(player, holder);
            if (session != null) session.togglePause();
            updateControls(event.getView().getTopInventory(), holder, player);
        } else if (event.getSlot() == NEXT_SLOT) {
            control(player, holder, PortablePlaylistSession::next);
            updateControls(event.getView().getTopInventory(), holder, player);
        } else if (event.getSlot() == STOP_SLOT) {
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
        List<MixerPlaylistTrack> tracks = new ArrayList<>();
        java.util.Optional<MixerDisc> disc = plugin.api().discs().readDisc(media);
        if (disc.isPresent()) {
            String source = disc.orElseThrow().source();
            if (PlaybackAuthorization.allow(MixerPlaybackOrigin.PORTABLE_SPEAKER, source,
                    media, player, player.getLocation())) tracks.add(MixerPlaylistTrack.fromDisc(disc.orElseThrow()));
        } else if (plugin.arePlaylistCartridgesEnabled() && plugin.getPlaylistCartridges().isCartridge(media)) {
            MixerPlaylist cartridge = plugin.getPlaylistCartridges().read(media).orElse(null);
            if (cartridge == null) {
                MessageUtil.sendActionBarMsg(player, "invalid_cartridge");
                return;
            }
            for (var track : cartridge.tracks()) {
                if (PlaybackAuthorization.allow(MixerPlaybackOrigin.PORTABLE_SPEAKER, track.source(),
                        media, player, player.getLocation())) tracks.add(track);
            }
            if (cartridge.tracks().isEmpty()) {
                MessageUtil.sendActionBarMsg(player, "empty_cartridge");
                return;
            }
        } else {
            MessageUtil.sendActionBarMsg(player, "not_valid_media");
            return;
        }

        if (tracks.isEmpty()) return;
        EntityMixerAudioPlayer existing = plugin.getPortablePlayerMap().get(player.getUniqueId());
        if (existing != null) existing.stop();

        EntityMixerAudioPlayer portablePlayer = new EntityMixerAudioPlayer(player);
        portablePlayer.setSourceItemId(holder.speakerId);
        PortablePlaylistSession session = new PortablePlaylistSession(
                plugin, player, holder.speakerId, portablePlayer, tracks);
        plugin.getPortablePlayerMap().put(player.getUniqueId(), portablePlayer);
        plugin.getPortablePlaylistSessions().put(player.getUniqueId(), session);
        MessageUtil.sendActionBarMsg(player, "playback_start");
        session.start();
        player.closeInventory();
    }

    private void control(Player player, PortableSpeakerHolder holder,
                         java.util.function.Predicate<PortablePlaylistSession> operation) {
        PortablePlaylistSession session = session(player, holder);
        if (session == null || !operation.test(session)) {
            MessageUtil.sendActionBarMsg(player, "playlist_control_unavailable");
        }
    }

    private PortablePlaylistSession session(Player player, PortableSpeakerHolder holder) {
        PortablePlaylistSession session = MixerPlugin.getPlugin().getPortablePlaylistSessions()
                .get(player.getUniqueId());
        if (session == null || !holder.speakerId.equals(session.speakerId())) return null;
        return session;
    }

    private void updateControls(Inventory inventory, PortableSpeakerHolder holder, Player player) {
        PortablePlaylistSession session = session(player, holder);
        inventory.setItem(PREVIOUS_SLOT, button(Material.ARROW, "playlist.previous_button"));
        inventory.setItem(SHUFFLE_SLOT, button(session != null && session.shuffle() ? Material.LIME_DYE : Material.GRAY_DYE,
                "playlist.shuffle_button", session != null && session.shuffle() ? "ON" : "OFF"));
        inventory.setItem(REPEAT_SLOT, button(Material.REPEATER, "playlist.repeat_button",
                session == null ? "OFF" : session.repeatMode().name()));
        inventory.setItem(PAUSE_SLOT, button(session != null && session.paused() ? Material.LIME_CONCRETE : Material.YELLOW_CONCRETE,
                session != null && session.paused() ? "playlist.resume_button" : "playlist.pause_button"));
        inventory.setItem(NEXT_SLOT, button(Material.ARROW, "playlist.next_button"));
    }

    private ItemStack button(Material material, String messageKey, Object... args) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> meta.displayName(MM.deserialize(MixerPlugin.getPlugin().getLocalizationManager()
                .getMessage(messageKey, args)).decoration(TextDecoration.ITALIC, false)));
        return item;
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof PortableSpeakerHolder)) return;
        if (event.getRawSlots().stream().anyMatch(slot -> slot < INVENTORY_SIZE && slot != MEDIA_SLOT)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof PortableSpeakerHolder)) return;
        ItemStack media = event.getInventory().getItem(MEDIA_SLOT);
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
