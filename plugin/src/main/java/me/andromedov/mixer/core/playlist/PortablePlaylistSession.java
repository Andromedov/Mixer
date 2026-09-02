package me.andromedov.mixer.core.playlist;

import me.andromedov.mixer.api.playlist.MixerPlaylistTrack;
import me.andromedov.mixer.core.MixerPlugin;
import me.andromedov.mixer.core.audio.EntityMixerAudioPlayer;
import me.andromedov.mixer.core.util.MessageUtil;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class PortablePlaylistSession {
    public enum RepeatMode {
        OFF,
        ALL,
        ONE;

        public RepeatMode next() {
            return switch (this) {
                case OFF -> ALL;
                case ALL -> ONE;
                case ONE -> OFF;
            };
        }
    }

    private final MixerPlugin plugin;
    private final Player owner;
    private final UUID speakerId;
    private final EntityMixerAudioPlayer audioPlayer;
    private final List<MixerPlaylistTrack> tracks;
    private final List<Integer> order = new ArrayList<>();
    private int cursor;
    private boolean shuffle;
    private boolean paused;
    private volatile boolean active = true;
    private RepeatMode repeatMode = RepeatMode.OFF;

    public PortablePlaylistSession(MixerPlugin plugin, Player owner, UUID speakerId,
                                   EntityMixerAudioPlayer audioPlayer, List<MixerPlaylistTrack> tracks) {
        this.plugin = plugin;
        this.owner = owner;
        this.speakerId = speakerId;
        this.audioPlayer = audioPlayer;
        this.tracks = List.copyOf(tracks);
        for (int i = 0; i < tracks.size(); i++) order.add(i);
        this.cursor = 0;
        audioPlayer.setTrackFinishedHandler(this::scheduleTrackFinished);
    }

    public void start() {
        if (tracks.isEmpty()) throw new IllegalStateException("Playlist must contain at least one track");
        playCurrent();
    }

    public boolean next() {
        if (!active) return false;
        if (!advance(false)) return false;
        playCurrent();
        return true;
    }

    public boolean previous() {
        if (!active) return false;
        if (cursor > 0) {
            cursor--;
        } else if (repeatMode == RepeatMode.ALL) {
            cursor = order.size() - 1;
        }
        playCurrent();
        return true;
    }

    public boolean togglePause() {
        if (!active || !audioPlayer.isRunning()) return false;
        paused = !paused;
        audioPlayer.setPlaybackPaused(paused);
        return paused;
    }

    public boolean toggleShuffle() {
        if (!active) return shuffle;
        shuffle = !shuffle;
        if (shuffle) {
            Collections.shuffle(order.subList(Math.min(cursor + 1, order.size()), order.size()));
        } else {
            restoreNaturalRemainingOrder();
        }
        return shuffle;
    }

    public RepeatMode cycleRepeatMode() {
        repeatMode = repeatMode.next();
        return repeatMode;
    }

    public UUID speakerId() {
        return speakerId;
    }

    public boolean shuffle() {
        return shuffle;
    }

    public boolean paused() {
        return paused;
    }

    public RepeatMode repeatMode() {
        return repeatMode;
    }

    public MixerPlaylistTrack currentTrack() {
        return tracks.get(order.get(cursor));
    }

    public void cancel() {
        active = false;
    }

    private void scheduleTrackFinished() {
        plugin.scheduler().runFor(owner, this::onTrackFinished, this::cancel);
    }

    private void onTrackFinished() {
        if (!active || !owner.isOnline()) return;
        if (!advance(true)) {
            active = false;
            plugin.getPortablePlaylistSessions().remove(owner.getUniqueId(), this);
            audioPlayer.stop();
            MessageUtil.sendActionBarMsg(owner, "playlist_finished");
            return;
        }
        playCurrent();
    }

    private boolean advance(boolean automatic) {
        if (automatic && repeatMode == RepeatMode.ONE) return true;
        if (cursor + 1 < order.size()) {
            cursor++;
            return true;
        }
        if (repeatMode != RepeatMode.ALL) return false;
        resetOrder();
        cursor = 0;
        return true;
    }

    private void resetOrder() {
        order.clear();
        for (int i = 0; i < tracks.size(); i++) order.add(i);
        if (shuffle) Collections.shuffle(order);
    }

    private void restoreNaturalRemainingOrder() {
        Set<Integer> alreadyPlayed = new HashSet<>(order.subList(0, Math.min(cursor + 1, order.size())));
        List<Integer> prefix = new ArrayList<>(order.subList(0, Math.min(cursor + 1, order.size())));
        order.clear();
        order.addAll(prefix);
        for (int i = 0; i < tracks.size(); i++) {
            if (!alreadyPlayed.contains(i)) order.add(i);
        }
    }

    private void playCurrent() {
        if (!active) return;
        paused = false;
        MixerPlaylistTrack track = currentTrack();
        audioPlayer.setPlaybackPaused(false);
        audioPlayer.clearAndPlay(track.source());
        MessageUtil.sendActionBarMsg(owner, "now_playing", track.title());
    }
}
