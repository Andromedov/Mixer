package net.somewhatcity.mixer.core.audio;

import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.audiochannel.EntityAudioChannel;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.somewhatcity.mixer.api.MixerSpeaker;
import net.somewhatcity.mixer.core.MixerPlugin;
import net.somewhatcity.mixer.core.MixerVoicechatPlugin;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public class EntityMixerAudioPlayer extends AbstractMixerAudioPlayer {
    private final Player owner;
    private EntityAudioChannel channel;
    private UUID sourceItemId;
    private BukkitTask particleTask;

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
    }

    public UUID getSourceItemId() {
        return sourceItemId;
    }

    @Override
    protected void start() {
        super.start();
        startParticles();
    }

    private void startParticles() {
        if (particleTask != null && !particleTask.isCancelled()) return;

        particleTask = org.bukkit.Bukkit.getScheduler().runTaskTimer(MixerPlugin.getPlugin(), () -> {
            if (!owner.isOnline() || !running) {
                if (particleTask != null) particleTask.cancel();
                return;
            }
            Location loc = owner.getLocation().add(0, 2.2, 0);
            owner.getWorld().spawnParticle(Particle.NOTE, loc, 1, 0.3, 0.2, 0.3, 0.5);
        }, 0L, 10L); // Every 0.5 seconds
    }

    @Override
    public Location location() {
        return owner.getLocation();
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
        if (owner.isOnline()) {
            owner.sendMessage(MiniMessage.miniMessage().deserialize(message));
        }
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