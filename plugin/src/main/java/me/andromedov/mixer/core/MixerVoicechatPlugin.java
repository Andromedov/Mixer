package me.andromedov.mixer.core;

import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.VolumeCategory;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import me.andromedov.mixer.core.audio.IMixerAudioPlayer;
import me.andromedov.mixer.core.db.MixerDatabase.StoredMixer;
import me.andromedov.mixer.core.util.Utils;
import me.andromedov.mixer.core.util.PlaybackAuthorization;
import me.andromedov.mixer.api.playback.MixerPlaybackOrigin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Jukebox;
import org.bukkit.inventory.ItemStack;

import javax.sound.sampled.AudioFormat;
import java.util.List;

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

        MixerPlugin plugin = MixerPlugin.getPlugin();
        plugin.scheduler().runAsync(() -> {
            List<StoredMixer> activeMixers = plugin.getDatabase().loadMixers();
            plugin.scheduler().runGlobal(() -> restoreActiveMixers(activeMixers));
        });
    }

    private void restoreActiveMixers(List<StoredMixer> activeMixers) {
        for (StoredMixer stored : activeMixers) {
            World world = Bukkit.getWorld(stored.worldName());
            if (world == null) continue;
            Location location = new Location(world, stored.x(), stored.y(), stored.z());
            MixerPlugin.getPlugin().scheduler().runAt(location,
                    () -> restoreActiveMixer(location, stored.uri()));
        }
    }

    private void restoreActiveMixer(Location location, String uri) {
        ItemStack disc = location.getBlock().getState() instanceof Jukebox jukebox && jukebox.hasRecord()
                ? jukebox.getRecord()
                : null;

        if (!PlaybackAuthorization.allow(MixerPlaybackOrigin.SERVER_RESTORE, uri,
                disc, null, location)) return;

        try {
            IMixerAudioPlayer audioPlayer = new IMixerAudioPlayer(location);
            audioPlayer.load(uri);
        } catch (Exception ignored) { }
    }
}
