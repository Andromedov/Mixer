package me.andromedov.mixer.core.audio;

import com.google.gson.JsonObject;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.audiochannel.EntityAudioChannel;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.minimessage.MiniMessage;
import me.andromedov.mixer.api.MixerSpeaker;
import me.andromedov.mixer.core.MixerPlugin;
import me.andromedov.mixer.core.MixerVoicechatPlugin;
import me.andromedov.mixer.core.util.MixerScheduler;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public class EntityMixerAudioPlayer extends AbstractMixerAudioPlayer {
    private final Player owner;
    private EntityAudioChannel channel;
    private volatile UUID sourceItemId;
    private volatile ScheduledTask particleTask;
    private volatile Runnable trackFinishedHandler;

    public EntityMixerAudioPlayer(Player player) {
        super();
        this.owner = player;

        VoicechatServerApi api = (VoicechatServerApi) MixerVoicechatPlugin.api;
        if (api != null) {
            de.maxhenkel.voicechat.api.Entity vcEntity = api.fromEntity(player);

            if (vcEntity != null) {
                this.channel = api.createEntityAudioChannel(UUID.randomUUID(), vcEntity);
                this.channel.setCategory("mixer");
                this.channel.setDistance(100);
            } else {
                MixerPlugin.getPlugin().logDebug(Level.WARNING, "Failed to convert player to VoiceChat entity.", null);
            }
        } else {
            MixerPlugin.getPlugin().logDebug(Level.SEVERE, "VoiceChat API is not initialized!", null);
        }

        initializeAsync();
    }

    public void setSourceItemId(UUID sourceItemId) {
        this.sourceItemId = sourceItemId;
        loadSettingsFromDb();
    }

    public UUID getSourceItemId() {
        return sourceItemId;
    }

    public void setTrackFinishedHandler(Runnable trackFinishedHandler) {
        this.trackFinishedHandler = trackFinishedHandler;
    }

    public void setPlaybackPaused(boolean paused) {
        if (lavaplayer != null) lavaplayer.setPaused(paused);
    }

    @Override
    protected void onTrackEnded(AudioTrack track, AudioTrackEndReason endReason) {
        if (endReason == AudioTrackEndReason.FINISHED && trackFinishedHandler != null && running) {
            trackFinishedHandler.run();
        }
    }

    @Override
    protected void onTrackLoadFailed(String source) {
        if (trackFinishedHandler != null && running) trackFinishedHandler.run();
    }

    private void loadSettingsFromDb() {
        if (sourceItemId == null) return;
        MixerPlugin.getPlugin().scheduler().runAsync(() -> {
            JsonObject loadedSettings = MixerPlugin.getPlugin().getDatabase().loadSpeakerDsp(sourceItemId);
            if (loadedSettings != null) {
                this.setDspSettings(loadedSettings);
                if (running && playbackStarted) {
                    updateVolume();
                }
            }
        });
    }

    @Override
    protected void start() {
        super.start();
        startParticles();
    }

    private void startParticles() {
        if (particleTask != null && !particleTask.isCancelled()) return;

        particleTask = MixerPlugin.getPlugin().scheduler().runForAtFixedRate(owner, () -> {
            if (!owner.isOnline() || !running) {
                if (particleTask != null) particleTask.cancel();
                return;
            }
            Location loc = owner.getLocation().add(0, 2.2, 0);
            owner.getWorld().spawnParticle(Particle.NOTE, loc, 1, 0.3, 0.2, 0.3, 0.5);
        }, this::stop, 1L, 10L); // Every 0.5 seconds
    }

    @Override
    public Location location() {
        return owner.getLocation();
    }

    @Override
    protected void persistDspSettings() {
        if (sourceItemId == null) return;
        JsonObject snapshot = dspSettings.deepCopy();
        MixerPlugin.getPlugin().scheduler().runAsync(() ->
                MixerPlugin.getPlugin().getDatabase().saveSpeakerDsp(sourceItemId, snapshot));
    }

    @Override
    public Set<MixerSpeaker> speakers() {
        return Collections.emptySet();
    }

    @Override
    protected void broadcastAudio(byte[] data) {
        if (channel != null) {
            channel.send(data);
        }
    }

    @Override
    protected void notifyUser(String message) {
        MixerPlugin.getPlugin().scheduler().runFor(owner, () -> {
            if (owner.isOnline()) owner.sendMessage(MiniMessage.miniMessage().deserialize(message));
        }, this::stop);
    }

    @Override
    protected void requireOwnedThread(String action) {
        MixerScheduler.requireOwned(owner, action);
    }

    @Override
    public void stop() {
        stop(true);
    }

    public void stopWithoutEject() {
        stop(false);
    }

    private void stop(boolean ejectMedia) {
        super.stop();
        if (particleTask != null) {
            particleTask.cancel();
            particleTask = null;
        }
        if (ejectMedia) {
            MixerPlugin.getPlugin().getPortableSpeakers().eject(owner, sourceItemId);
        }
        var session = MixerPlugin.getPlugin().getPortablePlaylistSessions().remove(owner.getUniqueId());
        if (session != null) session.cancel();
        MixerPlugin.getPlugin().getPortablePlayerMap().remove(owner.getUniqueId());
    }
}
