package net.somewhatcity.mixer.core.audio;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.audiochannel.LocationalAudioChannel;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.somewhatcity.mixer.api.MixerSpeaker;
import net.somewhatcity.mixer.core.MixerPlugin;
import net.somewhatcity.mixer.core.MixerVoicechatPlugin;
import net.somewhatcity.mixer.core.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.Jukebox;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class IMixerAudioPlayer extends AbstractMixerAudioPlayer {
    private static final VoicechatServerApi API = (VoicechatServerApi) MixerVoicechatPlugin.api;

    private Location location;
    private Block block;
    private Set<MixerSpeaker> speakers;
    private List<LocationalAudioChannel> channels = new CopyOnWriteArrayList<>();

    public IMixerAudioPlayer(Location location) {
        super();
        if (!location.getBlock().getType().equals(Material.JUKEBOX)) {
            throw new IllegalArgumentException("no jukebox at location");
        }

        if (MixerPlugin.getPlugin().playerHashMap().containsKey(location)) {
            MixerPlugin.getPlugin().playerHashMap().get(location).stop();
        }

        MixerPlugin.getPlugin().playerHashMap().put(location, this);

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
            speaker.location().toCenterLocation().add(0, 1, 0);
            LocationalAudioChannel channel = API.createLocationalAudioChannel(
                    UUID.randomUUID(),
                    API.fromServerLevel(speaker.location().getWorld()),
                    API.createPosition(speaker.location().getX(), speaker.location().getY(), speaker.location().getZ())
            );
            channel.setCategory("mixer");
            channel.setDistance(100);
            channels.add(channel);
        });

        initializeAsync();
    }

    @Override
    public Location location() { return location; }

    @Override
    public Set<MixerSpeaker> speakers() { return speakers; }

    @Override
    protected void broadcastAudio(byte[] data) {
        channels.forEach(ch -> {
            try {
                ch.send(data);
            } catch (Exception e) {
                if (running) {
                    MixerPlugin.getPlugin().getLogger().warning("Error sending audio to channel: " + e.getMessage());
                }
            }
        });
    }

    @Override
    protected void notifyUser(String message) {
        Bukkit.getScheduler().runTask(MixerPlugin.getPlugin(), () -> {
            location.getNearbyPlayers(10).forEach(p -> {
                p.sendMessage(MiniMessage.miniMessage().deserialize(message));
            });
        });
    }

    @Override
    protected void configureAndPlay(AudioTrack track) {
        FileConfiguration config = MixerPlugin.getPlugin().getMixersConfig();
        String identifier = "mixers.mixer_%s%s%s".formatted(location.getBlockX(), location.getBlockY(), location.getBlockZ());

        Bukkit.getScheduler().runTaskAsynchronously(MixerPlugin.getPlugin(), () -> {
            config.set(identifier + ".uri", track.getInfo().uri);
            config.set(identifier + ".location", location);
            MixerPlugin.getPlugin().saveMixersConfig();
        });

        super.configureAndPlay(track);
    }

    @Override
    public void stop() {
        super.stop();
        MixerPlugin.getPlugin().playerHashMap().remove(location);

        FileConfiguration config = MixerPlugin.getPlugin().getMixersConfig();
        String identifier = "mixers.mixer_%s%s%s".formatted(location.getBlockX(), location.getBlockY(), location.getBlockZ());
        Bukkit.getScheduler().runTask(MixerPlugin.getPlugin(), () -> {
            if (config.contains(identifier)) {
                config.set(identifier, null);
                MixerPlugin.getPlugin().saveMixersConfig();
            }
        });
    }
}