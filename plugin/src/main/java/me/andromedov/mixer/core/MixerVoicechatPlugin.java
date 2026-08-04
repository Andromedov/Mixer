package me.andromedov.mixer.core;

import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.VolumeCategory;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import me.andromedov.mixer.core.audio.IMixerAudioPlayer;
import me.andromedov.mixer.core.util.Utils;
import me.andromedov.mixer.core.util.PlaybackAuthorization;
import me.andromedov.mixer.api.playback.MixerPlaybackOrigin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Jukebox;
import org.bukkit.inventory.ItemStack;

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

        Bukkit.getScheduler().runTaskAsynchronously(MixerPlugin.getPlugin(), () -> {
            Map<Location, String> activeMixers = MixerPlugin.getPlugin().getDatabase().loadMixers();
            Bukkit.getScheduler().runTask(MixerPlugin.getPlugin(), () -> restoreActiveMixers(activeMixers));
        });
    }

    private void restoreActiveMixers(Map<Location, String> activeMixers) {
        for (Map.Entry<Location, String> entry : activeMixers.entrySet()) {
            Location location = entry.getKey();
            String uri = entry.getValue();
            ItemStack disc = location.getBlock().getState() instanceof Jukebox jukebox && jukebox.hasRecord()
                    ? jukebox.getRecord()
                    : null;

            if (!PlaybackAuthorization.allow(MixerPlaybackOrigin.SERVER_RESTORE, uri,
                    disc, null, location)) continue;

            try {
                IMixerAudioPlayer audioPlayer = new IMixerAudioPlayer(location);
                audioPlayer.load(uri);
            } catch (Exception ignored) { }
        }
    }
}
