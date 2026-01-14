package net.somewhatcity.mixer.core;

import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.VolumeCategory;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import net.somewhatcity.mixer.core.audio.IMixerAudioPlayer;
import net.somewhatcity.mixer.core.util.Utils;
import org.bukkit.Location;

import javax.sound.sampled.AudioFormat;
import java.util.Map;

public class MixerVoicechatPlugin implements VoicechatPlugin {

    public static VoicechatApi api;

    @Override
    public String getPluginId() {
        return MixerPlugin.getPluginId();
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(VoicechatServerStartedEvent.class, this::onServerStarted);
    }

    @Override
    public void initialize(VoicechatApi api) {
        MixerVoicechatPlugin.api = api;
    }

    public static AudioFormat getConfiguredAudioFormat() {
        return Utils.createConfiguredAudioFormat();
    }

    private void onServerStarted(VoicechatServerStartedEvent event) {
        VoicechatServerApi api = event.getVoicechat();

        VolumeCategory mixer = api.volumeCategoryBuilder()
                .setId("mixer")
                .setName("Mixer")
                .setDescription("Mixer audio volume")
                .build();

        api.registerVolumeCategory(mixer);

        // Load active mixers from Database
        Map<Location, String> activeMixers = MixerPlugin.getPlugin().getDatabase().loadMixers();

        for (Map.Entry<Location, String> entry : activeMixers.entrySet()) {
            Location location = entry.getKey();
            String uri = entry.getValue();

            try {
                IMixerAudioPlayer audioPlayer = new IMixerAudioPlayer(location);
                audioPlayer.load(uri);
            } catch (Exception ignored) { }
        }
    }
}