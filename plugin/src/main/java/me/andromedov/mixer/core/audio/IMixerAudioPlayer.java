package me.andromedov.mixer.core.audio;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.audiochannel.LocationalAudioChannel;
import net.kyori.adventure.text.minimessage.MiniMessage;
import me.andromedov.mixer.api.MixerSpeaker;
import me.andromedov.mixer.core.MixerPlugin;
import me.andromedov.mixer.core.MixerVoicechatPlugin;
import me.andromedov.mixer.core.util.MessageUtil;
import me.andromedov.mixer.core.util.MixerScheduler;
import me.andromedov.mixer.core.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.Jukebox;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

public class IMixerAudioPlayer extends AbstractMixerAudioPlayer {
    private Location location;
    private Block block;
    private Set<MixerSpeaker> speakers;
    private List<LocationalAudioChannel> channels = new CopyOnWriteArrayList<>();
    private final AtomicBoolean playbackFailureHandled = new AtomicBoolean();

    public IMixerAudioPlayer(Location location) {
        super();
        if (!location.getBlock().getType().equals(Material.JUKEBOX)) {
            throw new IllegalArgumentException("no jukebox at location");
        }

        VoicechatServerApi api = (VoicechatServerApi) MixerVoicechatPlugin.api;
        if (api == null) {
            throw new IllegalStateException("VoiceChat API is not initialized");
        }

        this.location = location;
        this.block = location.getBlock();
        this.speakers = new HashSet<>();

        Jukebox jukebox = (Jukebox) block.getState();

        NamespacedKey mixerLinks = new NamespacedKey(MixerPlugin.getPlugin(), "mixer_links");
        String speakerData = jukebox.getPersistentDataContainer().get(mixerLinks, PersistentDataType.STRING);
        if (speakerData == null || speakerData.isEmpty()) {
            speakers.add(new IMixerSpeaker(location));
        } else {
            JsonArray links = (JsonArray) JsonParser.parseString(speakerData);
            links.forEach(link -> {
                JsonObject obj = link.getAsJsonObject();
                Location speakerLocation = new Location(
                        Bukkit.getWorld(obj.get("world").getAsString()),
                        obj.get("x").getAsDouble(),
                        obj.get("y").getAsDouble(),
                        obj.get("z").getAsDouble()
                );
                speakers.add(new IMixerSpeaker(speakerLocation));
            });
        }

        this.dspSettings = Utils.loadNbtData(location, "mixer_dsp");
        if (this.dspSettings == null) this.dspSettings = new JsonObject();

        speakers.forEach(speaker -> {
            Location speakerLocation = speaker.location().toCenterLocation().add(0, 1, 0);
            LocationalAudioChannel channel = api.createLocationalAudioChannel(
                    UUID.randomUUID(),
                    api.fromServerLevel(speakerLocation.getWorld()),
                    api.createPosition(speakerLocation.getX(), speakerLocation.getY(), speakerLocation.getZ())
            );
            channel.setCategory("mixer");
            channel.setDistance(100);
            channels.add(channel);
        });

        // Publish the player only after every synchronous initialization step has
        // succeeded. A failed constructor must not leave a broken map entry behind.
        IMixerAudioPlayer previous = MixerPlugin.getPlugin().playerHashMap().get(location);
        if (previous != null) previous.stop();
        MixerPlugin.getPlugin().playerHashMap().put(location, this);

        initializeAsync();
    }

    @Override
    public Location location() { return location.clone(); }

    @Override
    public Set<MixerSpeaker> speakers() { return Set.copyOf(speakers); }

    @Override
    protected void persistDspSettings() {
        Utils.saveNbtData(location, "mixer_dsp", dspSettings);
    }

    @Override
    protected void broadcastAudio(byte[] data) {
        channels.forEach(ch -> {
            try {
                ch.send(data);
            } catch (Exception e) {
                if (running) {
                    MixerPlugin.getPlugin().logDebug(Level.WARNING, "Error sending audio to channel", e);
                }
            }
        });
    }

    @Override
    protected void notifyUser(String message) {
        MixerPlugin.getPlugin().scheduler().runAt(location, () -> {
            location.getNearbyPlayers(10).forEach(p -> {
                p.sendMessage(MiniMessage.miniMessage().deserialize(message));
            });
        });
    }

    @Override
    protected void requireOwnedThread(String action) {
        MixerScheduler.requireOwned(location, action);
    }

    @Override
    protected void handlePlaybackException(FriendlyException exception) {
        if (!playbackFailureHandled.compareAndSet(false, true)) return;

        MixerPlugin plugin = MixerPlugin.getPlugin();
        plugin.logDebug(Level.WARNING, "FriendlyException in track playback", exception);

        // Lavaplayer invokes this callback from its own executor. Jukebox state,
        // nearby players and the active-player map must only be touched on the
        // owning region thread.
        plugin.scheduler().runAt(location, () -> {
            stop();

            if (location.getBlock().getState() instanceof Jukebox jukebox) {
                jukebox.stopPlaying();
                if (jukebox.hasRecord()) jukebox.eject();
            }

            location.getNearbyPlayers(10).forEach(player ->
                    MessageUtil.sendActionBarMsg(player, "playback_failed_ejected"));
        });
    }

    @Override
    protected void configureAndPlay(AudioTrack track) {
        // Save to Database
        MixerPlugin.getPlugin().scheduler().runAsync(() -> {
            MixerPlugin.getPlugin().getDatabase().saveMixer(location, track.getInfo().uri);
        });

        super.configureAndPlay(track);
    }

    @Override
    public void stop() {
        super.stop();
        MixerPlugin.getPlugin().playerHashMap().remove(location);

        // Remove from Database
        MixerPlugin.getPlugin().scheduler().runAsync(() -> {
            MixerPlugin.getPlugin().getDatabase().removeMixer(location);
        });
    }
}
