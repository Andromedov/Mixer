package net.somewhatcity.mixer.core.audio;

import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.audiochannel.EntityAudioChannel;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.somewhatcity.mixer.api.MixerSpeaker;
import net.somewhatcity.mixer.core.MixerPlugin;
import net.somewhatcity.mixer.core.MixerVoicechatPlugin;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;

public class EntityMixerAudioPlayer extends AbstractMixerAudioPlayer {
    private final Player owner;
    private EntityAudioChannel channel;

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
                MixerPlugin.getPlugin().getLogger().warning("Failed to convert player to VoiceChat entity.");
            }
        } else {
            MixerPlugin.getPlugin().getLogger().severe("VoiceChat API is not initialized!");
        }

        initializeAsync();
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
        // Remove self from the plugin's portable map
        MixerPlugin.getPlugin().getPortablePlayerMap().remove(owner.getUniqueId());
    }
}