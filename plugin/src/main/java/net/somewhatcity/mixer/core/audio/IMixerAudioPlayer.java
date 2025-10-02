/*
 * Copyright (c) 2024 mrmrmystery
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice (including the next paragraph) shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package net.somewhatcity.mixer.core.audio;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.GainProcessor;
import be.tarsos.dsp.effects.FlangerEffect;
import be.tarsos.dsp.filters.HighPass;
import be.tarsos.dsp.filters.LowPassFS;
import be.tarsos.dsp.io.TarsosDSPAudioFormat;
import be.tarsos.dsp.io.jvm.JVMAudioInputStream;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.source.bandcamp.BandcampAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.beam.BeamAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.getyarn.GetyarnAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.http.HttpAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.local.LocalAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.nico.NicoAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.soundcloud.SoundCloudAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.twitch.TwitchStreamAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.vimeo.VimeoAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrame;
import de.maxhenkel.opus4j.OpusDecoder;
import de.maxhenkel.opus4j.OpusEncoder;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.audiochannel.LocationalAudioChannel;
import dev.lavalink.youtube.YoutubeAudioSourceManager;
import dev.lavalink.youtube.clients.*;
import dev.lavalink.youtube.clients.skeleton.Client;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.somewhatcity.mixer.api.MixerAudioPlayer;
import net.somewhatcity.mixer.api.MixerDsp;
import net.somewhatcity.mixer.api.MixerSpeaker;
import net.somewhatcity.mixer.core.MixerPlugin;
import net.somewhatcity.mixer.core.MixerVoicechatPlugin;
import net.somewhatcity.mixer.core.util.Utils;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Jukebox;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.persistence.PersistentDataType;

import javax.sound.sampled.AudioFormat;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class IMixerAudioPlayer implements MixerAudioPlayer {
    private static final AudioFormat AUDIO_FORMAT = new AudioFormat(48000, 16, 1, true, true);
    private static final VoicechatServerApi API = (VoicechatServerApi) MixerVoicechatPlugin.api;
    public static final AudioPlayerManager APM = new DefaultAudioPlayerManager();
    private static final ExecutorService EXECUTOR_SERVICE = Executors.newSingleThreadExecutor();

    private final Object initializationLock = new Object();
    private volatile boolean isInitialized = false;

    private Location location;
    private Block block;
    private Set<MixerSpeaker> speakers;
    private MixerDsp dsp;
    private Timer audioTimer;
    private AudioPlayer lavaplayer;
    private OpusDecoder decoder;
    private OpusEncoder encoder;
    private LavaplayerAudioStream audioStream;
    private volatile boolean running = true;
    private boolean playbackStarted = false;
    private AudioDispatcher dispatcher;
    private JVMAudioInputStream jvmAudioInputStream;
    private List<LocationalAudioChannel> channels = new ArrayList<>();
    private Queue<AudioTrack> playlist = new ArrayDeque<>();
    private Queue<String> loadingQueue = new ArrayDeque<>();
    private Queue<byte[]> audioQueue = new ArrayDeque<>();
    private JsonObject dspSettings;

    static {
        FileConfiguration config = MixerPlugin.getPlugin().getConfig();
        if (config.getBoolean("mixer.youtube.enabled")) {
            YoutubeAudioSourceManager youtube = new YoutubeAudioSourceManager(true, new Client[]{
                    new Music(), new Web(), new MusicWithThumbnail(), new WebWithThumbnail(),
                    new TvHtml5Embedded(), new TvHtml5EmbeddedWithThumbnail(), new Android(), new AndroidMusic()
            });

            if (config.getBoolean("mixer.youtube.useOAuth")) {
                String refreshToken = config.getString("mixer.youtube.refreshToken");
                if (refreshToken != null && !refreshToken.isEmpty()) {
                    youtube.useOauth2(refreshToken, true);
                } else {
                    youtube.useOauth2(null, false);
                }
            }
            APM.registerSourceManager(youtube);
        }
        APM.registerSourceManager(SoundCloudAudioSourceManager.createDefault());
        APM.registerSourceManager(new BandcampAudioSourceManager());
        APM.registerSourceManager(new VimeoAudioSourceManager());
        APM.registerSourceManager(new TwitchStreamAudioSourceManager());
        APM.registerSourceManager(new BeamAudioSourceManager());
        APM.registerSourceManager(new GetyarnAudioSourceManager());
        APM.registerSourceManager(new NicoAudioSourceManager());
        APM.registerSourceManager(new HttpAudioSourceManager());
        APM.registerSourceManager(new LocalAudioSourceManager());
        APM.setFrameBufferDuration(100);
    }

    public IMixerAudioPlayer(Location location) {
        if (!location.getBlock().getType().equals(Material.JUKEBOX)) {
            throw new IllegalArgumentException("no jukebox at location");
        }
        if (MixerPlugin.getPlugin().playerHashMap().containsKey(location)) {
            MixerAudioPlayer oldPlayer = MixerPlugin.getPlugin().playerHashMap().get(location);
            oldPlayer.stop();
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

        dspSettings = Utils.loadNbtData(location, "mixerdsp");
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

        // Asynchronous initialization
        initializeAsync();
    }

    private void initializeAsync() {
        CompletableFuture.runAsync(() -> {
            synchronized(initializationLock) {
                try {
                    // Initialization of components
                    lavaplayer = APM.createPlayer();
                    lavaplayer.addListener(new AudioEventAdapter() {
                        @Override
                        public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason) {
                            new Thread(() -> {
                                while (!audioQueue.isEmpty() && running) {
                                    try {
                                        Thread.sleep(100);
                                    } catch (InterruptedException e) {
                                        Thread.currentThread().interrupt();
                                        break;
                                    }
                                }
                            }).start();
                        }
                    });

                    audioStream = new LavaplayerAudioStream(AUDIO_FORMAT);
                    decoder = new OpusDecoder((int) AUDIO_FORMAT.getSampleRate(), AUDIO_FORMAT.getChannels());
                    decoder.setFrameSize(960);
                    encoder = new OpusEncoder((int) AUDIO_FORMAT.getSampleRate(), AUDIO_FORMAT.getChannels(), OpusEncoder.Application.AUDIO);

                    audioTimer = new Timer();
                    audioTimer.scheduleAtFixedRate(new TimerTask() {
                        @Override
                        public void run() {
                            if (!running) {
                                cancel();
                                return;
                            }

                            try {
                                AudioFrame frame = lavaplayer.provide();
                                if (frame != null && running) {
                                    byte[] data = frame.getData();
                                    if (decoder != null && !decoder.isClosed()) {
                                        byte[] decoded = Utils.shortToByte(decoder.decode(data));

                                        if (audioStream != null) {
                                            try {
                                                audioStream.appendData(decoded);
                                            } catch (Exception ex) {
                                                if (running) {
                                                    MixerPlugin.getPlugin().getLogger().warning("Error appending audio data: " + ex.getMessage());
                                                }
                                            }
                                        }
                                    }
                                }

                                if (!audioQueue.isEmpty() && running) {
                                    byte[] data = audioQueue.poll();
                                    if (data != null) {
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
                                }
                            } catch (Exception e) {
                                if (running) {
                                    MixerPlugin.getPlugin().getLogger().severe("Critical error in audio timer: " + e.getMessage());
                                    stop();
                                }
                            }
                        }
                    }, 0, 20);

                    Thread.sleep(100);

                    isInitialized = true;
                    initializationLock.notifyAll();
                } catch (Exception e) {
                    MixerPlugin.getPlugin().getLogger().severe("Failed to initialize audio player: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        });
    }

    @Override
    public Location location() {
        return location;
    }

    @Override
    public Set<MixerSpeaker> speakers() {
        return speakers;
    }

    @Override
    public MixerDsp dsp() {
        return dsp;
    }

    @Override
    public void load(String... url) {
        // Initialization
        synchronized(initializationLock) {
            while (!isInitialized) {
                try {
                    initializationLock.wait(5000); // 5 seconds timeout
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }

        loadingQueue.addAll(List.of(url));
        loadSingle(loadingQueue.poll());
    }

    @Override
    public void stop() {
        running = false;

        if (audioTimer != null) {
            audioTimer.cancel();
            audioTimer = null;
        }

        if (dispatcher != null) {
            try {
                dispatcher.stop();
            } catch (Exception e) {
                MixerPlugin.getPlugin().getLogger().warning("Error stopping dispatcher: " + e.getMessage());
            }
            dispatcher = null;
        }

        if (encoder != null && !encoder.isClosed()) {
            try {
                encoder.close();
            } catch (Exception e) {
                MixerPlugin.getPlugin().getLogger().warning("Error closing encoder: " + e.getMessage());
            }
        }

        if (decoder != null && !decoder.isClosed()) {
            try {
                decoder.close();
            } catch (Exception e) {
                MixerPlugin.getPlugin().getLogger().warning("Error closing decoder: " + e.getMessage());
            }
        }

        if (audioStream != null) {
            try {
                audioStream.close();
            } catch (Exception e) {
                // Ignore
            }
        }

        MixerPlugin.getPlugin().playerHashMap().remove(location);

        // Clean up config
        FileConfiguration config = MixerPlugin.getPlugin().getConfig();
        String identifier = "mixers.mixer_%s%s%s".formatted(location.getBlockX(), location.getBlockY(), location.getBlockZ());
        if (config.contains(identifier)) {
            config.set(identifier, null);
            MixerPlugin.getPlugin().saveConfig();
        }

        try {
            if (jvmAudioInputStream != null) {
                jvmAudioInputStream.close();
            }
        } catch (IOException e) {
            // Ignore
        }
    }

    private void start() {
        if (!running) return;
        if (!playlist.isEmpty()) {
            lavaplayer.playTrack(playlist.poll());
        } else {
            stop();
        }
    }

    private void loadSingle(String audioUrl) {
        if (audioUrl == null || audioUrl.isEmpty()) return;
        final String url = audioUrl;
        EXECUTOR_SERVICE.submit(() -> {
            if (url.startsWith("cobalt://")) {
                String finalUrl = url.replace("cobalt://", "");
                finalUrl = Utils.requestCobaltMediaUrl(finalUrl);
                if (finalUrl == null || finalUrl.isEmpty()) {
                    location.getNearbyPlayers(10).forEach(p -> {
                        p.sendMessage(MiniMessage.miniMessage().deserialize("<red>Error playing cobalt media"));
                    });
                    return;
                }
            } else if (url.startsWith("https://www.youtube.com") || url.startsWith("https://music.youtube.com")) {
                String finalUrl = Utils.requestCobaltMediaUrl(url);
            }

            APM.loadItem(url, new AudioLoadResultHandler() {
                @Override
                public void trackLoaded(AudioTrack audioTrack) {
                    if (audioTrack.getInfo().isStream) {
                        // Stream logic
                    }
                    FileConfiguration config = MixerPlugin.getPlugin().getConfig();
                    String identifier = "mixers.mixer_%s%s%s".formatted(location.getBlockX(), location.getBlockY(), location.getBlockZ());
                    config.set(identifier + ".uri", audioTrack.getInfo().uri);
                    config.set(identifier + ".location", location);
                    MixerPlugin.getPlugin().saveConfig();

                    playlist.add(audioTrack);
                    if (!playbackStarted) {
                        loadDsp();
                        start();
                        playbackStarted = true;
                    }
                    if (!loadingQueue.isEmpty() && running) {
                        load(loadingQueue.poll());
                    }
                }

                @Override
                public void playlistLoaded(AudioPlaylist audioPlaylist) {
                    playlist.add(audioPlaylist.getSelectedTrack());
                }

                @Override
                public void noMatches() {
                    // No matches found
                }

                @Override
                public void loadFailed(FriendlyException e) {
                    e.printStackTrace();
                }
            });
        });
    }

    public void loadDsp() {
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                jvmAudioInputStream = new JVMAudioInputStream(audioStream);
                dispatcher = new AudioDispatcher(jvmAudioInputStream, 960, 0);
                TarsosDSPAudioFormat format = dispatcher.getFormat();

                JsonObject gainSettings = dspSettings.getAsJsonObject("gain");
                if (gainSettings != null) {
                    double gain = gainSettings.get("gain").getAsDouble();
                    GainProcessor gainProcessor = new GainProcessor(gain);
                    dispatcher.addAudioProcessor(gainProcessor);
                }

                JsonObject highPassSettings = dspSettings.getAsJsonObject("highPassFilter");
                if (highPassSettings != null) {
                    float frequency = highPassSettings.get("frequency").getAsFloat();
                    HighPass highPass = new HighPass(frequency, format.getSampleRate());
                    dispatcher.addAudioProcessor(highPass);
                }

                JsonObject flangerEffectSettings = dspSettings.getAsJsonObject("flangerEffect");
                if (flangerEffectSettings != null) {
                    double maxFlangerLength = flangerEffectSettings.get("maxFlangerLength").getAsDouble();
                    double wet = flangerEffectSettings.get("wet").getAsDouble();
                    double lfoFrequency = flangerEffectSettings.get("lfoFrequency").getAsDouble();
                    FlangerEffect flangerEffect = new FlangerEffect(maxFlangerLength, wet, format.getSampleRate(), lfoFrequency);
                    dispatcher.addAudioProcessor(flangerEffect);
                }

                JsonObject lowPassSettings = dspSettings.getAsJsonObject("lowPassFilter");
                if (lowPassSettings != null) {
                    float cutoffFrequency = lowPassSettings.get("frequency").getAsFloat();
                    LowPassFS lowPassFS = new LowPassFS(cutoffFrequency, format.getSampleRate());
                    dispatcher.addAudioProcessor(lowPassFS);
                }

                dispatcher.addAudioProcessor(new AudioOutputProcessor(data -> {
                    if (encoder.isClosed()) return;
                    byte[] encoded = encoder.encode(Utils.byteToShort(data));
                    audioQueue.add(encoded);
                }));

                dispatcher.run();
            }
        }, 500);
    }
}