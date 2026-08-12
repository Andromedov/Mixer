package me.andromedov.mixer.core.audio;

import com.google.gson.JsonObject;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.audiochannel.EntityAudioChannel;
import net.kyori.adventure.text.minimessage.MiniMessage;
import me.andromedov.mixer.api.MixerSpeaker;
import me.andromedov.mixer.core.MixerPlugin;
import me.andromedov.mixer.core.MixerVoicechatPlugin;
import me.andromedov.mixer.core.util.MixerScheduler;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public class EntityMixerAudioPlayer extends AbstractMixerAudioPlayer {
    private final Player owner;
    private EntityAudioChannel channel;
    private UUID sourceItemId;
    private ScheduledTask particleTask;

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
            if (owner.isOnline()) {
                owner.sendMessage(MiniMessage.miniMessage().deserialize(message));
            }
        }, this::stop);
    }

    @Override
    protected void requireOwnedThread(String action) {
        MixerScheduler.requireOwned(owner, action);
    }

    @Override
    public void stop() {
        super.stop();
        if (particleTask != null) {
            particleTask.cancel();
            particleTask = null;
        }
        MixerPlugin.getPlugin().getPortablePlayerMap().remove(owner.getUniqueId());
    }
}
