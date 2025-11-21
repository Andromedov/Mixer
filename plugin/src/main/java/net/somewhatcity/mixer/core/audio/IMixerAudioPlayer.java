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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;

public class IMixerAudioPlayer implements MixerAudioPlayer {
    private static final VoicechatServerApi API = (VoicechatServerApi) MixerVoicechatPlugin.api;
    public static final AudioPlayerManager APM = new DefaultAudioPlayerManager();

    // Retry settings
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 3000L; // 3 seconds

    // Context class to store retry info within the AudioTrack
    private static class TrackContext {
        String originalUrl;
        int retries;

        public TrackContext(String originalUrl, int retries) {
            this.originalUrl = originalUrl;
            this.retries = retries;
        }
    }

    // Audio format configuration
    private final AudioFormat audioFormat;
    private final int frameSize;
    private final int frameBufferDuration;
    private GainProcessor gainProcessor;

    private final Map<String, Long> lastLogTimes = new ConcurrentHashMap<>();
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
    private List<LocationalAudioChannel> channels = new CopyOnWriteArrayList<>();
    private Deque<AudioTrack> playlist = new ConcurrentLinkedDeque<>();
    private Queue<String> loadingQueue = new ConcurrentLinkedDeque<>();
    private Queue<byte[]> audioQueue = new ConcurrentLinkedDeque<>();
    private JsonObject dspSettings;

    static {
        FileConfiguration config = MixerPlugin.getPlugin().getMixersConfig();

        if (config.getBoolean("mixer.youtube.enabled", false)) {
            YoutubeAudioSourceManager youtube = new YoutubeAudioSourceManager(true, new Client[]{
                    new Music(), new Web(), new MusicWithThumbnail(), new WebWithThumbnail(),
                    new TvHtml5Embedded(), new TvHtml5EmbeddedWithThumbnail(), new Android(), new AndroidMusic()
            });

            if (config.getBoolean("mixer.youtube.useOAuth", false)) {
                String refreshToken = config.getString("mixer.youtube.refreshToken", "");
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

        int frameBufferDuration = MixerPlugin.getPlugin().getAudioFrameBufferDuration();
        APM.setFrameBufferDuration(frameBufferDuration);
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

        MixerPlugin plugin = MixerPlugin.getPlugin();

        // Get audio configuration from config
        int sampleRate = plugin.getAudioSampleRate();
        this.frameSize = plugin.getAudioBufferSize();
        this.frameBufferDuration = plugin.getAudioFrameBufferDuration();

        // Create audio format matching configuration
        this.audioFormat = new AudioFormat(
                sampleRate,
                16,
                1,
                true,
                true
        );

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

        initializeAsync();
    }

    private void initializeAsync() {
        CompletableFuture.runAsync(() -> {
            synchronized(initializationLock) {
                try {
                    lavaplayer = APM.createPlayer();
                    lavaplayer.addListener(new AudioEventAdapter() {
                        @Override
                        public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason) {
                            if (endReason.mayStartNext) {
                                if (!playlist.isEmpty()) {
                                    start();
                                } else if (!loadingQueue.isEmpty()) {
                                    loadNextFromQueue();
                                } else {
                                    playbackStarted = false;
                                }
                            }
                        }

                        @Override
                        public void onTrackException(AudioPlayer player, AudioTrack track, FriendlyException exception) {
                            handlePlaybackException(track, exception);
                        }

                        @Override
                        public void onTrackStuck(AudioPlayer player, AudioTrack track, long thresholdMs) {
                            MixerPlugin.getPlugin().getLogger().warning(
                                    "Track stuck: " + track.getInfo().title + " (threshold: " + thresholdMs + "ms)"
                            );
                            location.getNearbyPlayers(10).forEach(p -> {
                                p.sendMessage(MiniMessage.miniMessage().deserialize(
                                        "<yellow>Track playback stuck, skipping...</yellow>"
                                ));
                            });
                            start();
                        }
                    });

                    audioStream = new LavaplayerAudioStream(audioFormat);
                    decoder = new OpusDecoder((int) audioFormat.getSampleRate(), audioFormat.getChannels());
                    decoder.setFrameSize(frameSize);
                    encoder = new OpusEncoder((int) audioFormat.getSampleRate(), audioFormat.getChannels(), OpusEncoder.Application.AUDIO);

                    // Timer for audio frame processing - CRITICAL for synchronization
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
        synchronized(initializationLock) {
            while (!isInitialized) {
                try {
                    initializationLock.wait(5000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }

        loadingQueue.addAll(List.of(url));
        if (!playbackStarted && playlist.isEmpty()) {
            loadNextFromQueue();
        }
    }

    public void clearAndPlay(String... urls) {
        loadingQueue.clear();
        playlist.clear();

        if (lavaplayer != null) {
            lavaplayer.stopTrack();
        }

        playbackStarted = false;
        load(urls);
    }

    private void loadNextFromQueue() {
        if (!loadingQueue.isEmpty() && running) {
            attemptLoad(loadingQueue.poll(), 0);
        }
    }

    private void attemptLoad(String audioUrl, int retryCount) {
        if (audioUrl == null || audioUrl.isEmpty()) {
            loadNextFromQueue();
            return;
        }

        String finalUrlToLoad = audioUrl;

        if (audioUrl.startsWith("cobalt://")) {
            String rawUrl = audioUrl.replace("cobalt://", "");
            String resolvedUrl = Utils.requestCobaltMediaUrl(rawUrl);

            if (resolvedUrl == null || resolvedUrl.isEmpty()) {
                if (retryCount < MAX_RETRIES) {
                    scheduleRetry(audioUrl, retryCount, "Failed to resolve Cobalt URL");
                } else {
                    notifyError("Error resolving Cobalt media after " + MAX_RETRIES + " attempts");
                    loadNextFromQueue();
                }
                return;
            }
            finalUrlToLoad = resolvedUrl;
        }
        else if (audioUrl.startsWith("https://youtube.com") || audioUrl.startsWith("https://www.youtube.com")) {
            String resolvedUrl = Utils.requestCobaltMediaUrl(audioUrl);
            if (resolvedUrl != null && !resolvedUrl.isEmpty()) finalUrlToLoad = resolvedUrl;
        }

        APM.loadItem(finalUrlToLoad, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack audioTrack) {
                audioTrack.setUserData(new TrackContext(audioUrl, 0));
                configureAndPlay(audioTrack);
            }

            @Override
            public void playlistLoaded(AudioPlaylist audioPlaylist) {
                AudioTrack track = audioPlaylist.getSelectedTrack();
                if (track == null && !audioPlaylist.getTracks().isEmpty()) {
                    track = audioPlaylist.getTracks().get(0);
                }

                if (track != null) {
                    track.setUserData(new TrackContext(audioUrl, 0));
                    configureAndPlay(track);
                } else {
                    if (retryCount < MAX_RETRIES) {
                        scheduleRetry(audioUrl, retryCount, "Empty playlist loaded");
                    } else {
                        loadNextFromQueue();
                    }
                }
            }

            @Override
            public void noMatches() {
                if (retryCount < MAX_RETRIES) {
                    scheduleRetry(audioUrl, retryCount, "No matches found");
                } else {
                    notifyError("No matches found for URL");
                    loadNextFromQueue();
                }
            }

            @Override
            public void loadFailed(FriendlyException e) {
                if (retryCount < MAX_RETRIES) {
                    scheduleRetry(audioUrl, retryCount, e.getMessage());
                } else {
                    notifyError("Error loading track: " + e.getMessage());
                    MixerPlugin.getPlugin().getLogger().warning("Final load failure: " + e.getMessage());
                    loadNextFromQueue();
                }
            }
        });
    }

    private void scheduleRetry(String url, int currentRetry, String reason) {
        int nextRetry = currentRetry + 1;
        MixerPlugin.getPlugin().getLogger().warning(
                "Load failed (" + reason + "). Retrying " + nextRetry + "/" + MAX_RETRIES + " in 3s..."
        );

        Bukkit.getScheduler().runTask(MixerPlugin.getPlugin(), () -> {
            location.getNearbyPlayers(10).forEach(p ->
                    p.sendMessage(MiniMessage.miniMessage().deserialize(
                            "<yellow>Retrying connection... (" + nextRetry + "/" + MAX_RETRIES + ")</yellow>"
                    ))
            );
        });

        Bukkit.getScheduler().runTaskLaterAsynchronously(MixerPlugin.getPlugin(), () -> {
            attemptLoad(url, nextRetry);
        }, 60L); // 3 seconds
    }

    private void configureAndPlay(AudioTrack track) {
        FileConfiguration config = MixerPlugin.getPlugin().getMixersConfig();
        String identifier = "mixers.mixer_%s%s%s".formatted(location.getBlockX(), location.getBlockY(), location.getBlockZ());

        Bukkit.getScheduler().runTask(MixerPlugin.getPlugin(), () -> {
            config.set(identifier + ".uri", track.getInfo().uri);
            config.set(identifier + ".location", location);
            MixerPlugin.getPlugin().saveMixersConfig();
        });

        playlist.add(track);
        if (!playbackStarted) {
            loadDsp();
            start();
            playbackStarted = true;
        }
    }

    private void notifyError(String msg) {
        Bukkit.getScheduler().runTask(MixerPlugin.getPlugin(), () -> {
            location.getNearbyPlayers(10).forEach(p -> {
                p.sendMessage(MiniMessage.miniMessage().deserialize("<red>" + msg + "</red>"));
            });
        });
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
                MixerPlugin.getPlugin().getLogger().warning("Error closing audioStream: " + e.getMessage());
            }
        }

        if (jvmAudioInputStream != null) {
            try {
                jvmAudioInputStream.close();
            } catch (IOException e) {
                MixerPlugin.getPlugin().getLogger().warning("Error closing jvmAudioInputStream: " + e.getMessage());
            }
        }

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

    public void updateVolume() {
        this.dspSettings = Utils.loadNbtData(location, "mixerdsp");

        if (this.gainProcessor == null) {
            return;
        }

        float currentVolumeMultiplier = MixerPlugin.getPlugin().getVolumeMultiplier();

        JsonObject gainSettings = dspSettings.getAsJsonObject("gain");
        double finalGain;
        if (gainSettings != null) {
            double configuredGain = gainSettings.get("gain").getAsDouble();
            finalGain = configuredGain * currentVolumeMultiplier;
        } else {
            finalGain = currentVolumeMultiplier;
        }
        this.gainProcessor.setGain(finalGain);
    }

    private void start() {
        if (!running) return;

        AudioTrack track = playlist.poll();
        if (track != null) { lavaplayer.playTrack(track); }
        else { if (!loadingQueue.isEmpty()) { loadNextFromQueue(); } }
    }

    /* private void loadSingle(String audioUrl) {
        if (audioUrl == null || audioUrl.isEmpty()) return;

        String urlToLoad = audioUrl;
        String originalUrl = audioUrl; // Save original for retry

        if (audioUrl.startsWith("cobalt://")) {
            String finalUrl = audioUrl.replace("cobalt://", "");
            finalUrl = Utils.requestCobaltMediaUrl(finalUrl);
            if (finalUrl == null || finalUrl.isEmpty()) {
                Bukkit.getScheduler().runTask(MixerPlugin.getPlugin(), () -> {
                    location.getNearbyPlayers(10).forEach(p -> {
                        p.sendMessage(MiniMessage.miniMessage().deserialize("<red>Error playing cobalt media</red>"));
                    });
                });
                if (!loadingQueue.isEmpty() && running) {
                    loadSingle(loadingQueue.poll());
                }
                return;
            }
            urlToLoad = finalUrl;
        } else if (audioUrl.startsWith("https://youtube.com") || audioUrl.startsWith("https://www.youtube.com") || audioUrl.startsWith("https://music.youtube.com")) {
            String finalUrl = Utils.requestCobaltMediaUrl(audioUrl);
            if (finalUrl != null && !finalUrl.isEmpty()) {
                urlToLoad = finalUrl;
            }
        }

        String finalUrlToLoad = urlToLoad;
        APM.loadItem(finalUrlToLoad, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack audioTrack) {
                audioTrack.setUserData(new TrackContext(originalUrl, 0));

                if (audioTrack.getInfo().isStream) {
                    // Stream logic if needed
                }

                FileConfiguration config = MixerPlugin.getPlugin().getMixersConfig();
                String identifier = "mixers.mixer_%s%s%s".formatted(location.getBlockX(), location.getBlockY(), location.getBlockZ());
                Bukkit.getScheduler().runTask(MixerPlugin.getPlugin(), () -> {
                    config.set(identifier + ".uri", audioTrack.getInfo().uri);
                    config.set(identifier + ".location", location);
                    MixerPlugin.getPlugin().saveMixersConfig();
                });

                playlist.add(audioTrack);
                if (!playbackStarted) {
                    loadDsp();
                    start();
                    playbackStarted = true;
                }
                if (!loadingQueue.isEmpty() && running) {
                    loadSingle(loadingQueue.poll());
                }
            }

            @Override
            public void playlistLoaded(AudioPlaylist audioPlaylist) {
                AudioTrack track = audioPlaylist.getSelectedTrack();
                if (track != null) {
                    track.setUserData(new TrackContext(originalUrl, 0));
                    playlist.add(track);
                } else if (!audioPlaylist.getTracks().isEmpty()) {
                    for (AudioTrack t : audioPlaylist.getTracks()) {
                        t.setUserData(new TrackContext(originalUrl, 0));
                        playlist.add(t);
                    }
                }

                if (!playbackStarted) {
                    loadDsp();
                    start();
                    playbackStarted = true;
                }
            }

            @Override
            public void noMatches() {
                Bukkit.getScheduler().runTask(MixerPlugin.getPlugin(), () -> {
                    location.getNearbyPlayers(10).forEach(p -> {
                        p.sendMessage(MiniMessage.miniMessage().deserialize(
                                "<red>No matches found for URL</red>"
                        ));
                    });
                });

                if (!loadingQueue.isEmpty() && running) {
                    loadSingle(loadingQueue.poll());
                }
            }

            @Override
            public void loadFailed(FriendlyException e) {
                Bukkit.getScheduler().runTask(MixerPlugin.getPlugin(), () -> {
                    location.getNearbyPlayers(10).forEach(p -> {
                        p.sendMessage(MiniMessage.miniMessage().deserialize(
                                "<red>Error loading track: " + e.getMessage() + "</red>"
                        ));
                    });
                });

                MixerPlugin.getPlugin().getLogger().warning("Failed to load audio: " + e.getMessage());
                if (!loadingQueue.isEmpty() && running) {
                    loadSingle(loadingQueue.poll());
                }
            }
        });
    } */

    // New method to handle retries (loads and pushes to front of queue)
    private void retryLoad(TrackContext context) {
        String audioUrl = context.originalUrl;
        String urlToLoad = audioUrl;

        // Logic similar to loadSingle but optimized for retry
        if (audioUrl.startsWith("cobalt://")) {
            String finalUrl = audioUrl.replace("cobalt://", "");
            finalUrl = Utils.requestCobaltMediaUrl(finalUrl);
            if (finalUrl == null || finalUrl.isEmpty()) {
                // If resolving fails on retry, we can't do much
                MixerPlugin.getPlugin().getLogger().warning("Retry failed: Could not resolve Cobalt URL");
                start(); // Skip
                return;
            }
            urlToLoad = finalUrl;
        } else if (audioUrl.startsWith("https://youtube.com") || audioUrl.startsWith("https://www.youtube.com")) {
            String finalUrl = Utils.requestCobaltMediaUrl(audioUrl);
            if (finalUrl != null && !finalUrl.isEmpty()) urlToLoad = finalUrl;
        }

        String finalUrlToLoad = urlToLoad;
        APM.loadItem(finalUrlToLoad, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack audioTrack) {
                audioTrack.setUserData(context); // Preserve retry count
                playlist.addFirst(audioTrack); // Push to front
                start(); // Play immediately
            }

            @Override
            public void playlistLoaded(AudioPlaylist audioPlaylist) {
                // Usually retries are for single tracks, but if playlist:
                AudioTrack track = audioPlaylist.getSelectedTrack();
                if (track == null && !audioPlaylist.getTracks().isEmpty()) track = audioPlaylist.getTracks().get(0);

                if (track != null) {
                    track.setUserData(context);
                    playlist.addFirst(track);
                    start();
                } else {
                    start(); // Fail
                }
            }

            @Override
            public void noMatches() {
                start(); // Skip
            }

            @Override
            public void loadFailed(FriendlyException e) {
                /*
                If retry load fails, maybe try again? Or strictly follow retry count in handlePlaybackException?
                Since this is a LOAD failure, not playback, handlePlaybackException won't trigger.
                We should probably just give up here or log.
                */
                MixerPlugin.getPlugin().getLogger().warning("Retry load failed: " + e.getMessage());
                start();
            }
        });
    }

    public void loadDsp() {
        CompletableFuture.runAsync(() -> {
            try { Thread.sleep(500); } catch (InterruptedException e) { return; }
            if (!running) return;

            try {
                jvmAudioInputStream = new JVMAudioInputStream(audioStream);
                dispatcher = new AudioDispatcher(jvmAudioInputStream, frameSize, 0);
                TarsosDSPAudioFormat format = dispatcher.getFormat();

                float currentVolumeMultiplier = MixerPlugin.getPlugin().getVolumeMultiplier();
                JsonObject gainSettings = dspSettings.getAsJsonObject("gain");
                if (gainSettings != null) {
                    double configuredGain = gainSettings.get("gain").getAsDouble();
                    double finalGain = configuredGain * currentVolumeMultiplier;
                    gainProcessor = new GainProcessor(finalGain);
                }
                else {
                    gainProcessor = new GainProcessor(currentVolumeMultiplier);
                }
                dispatcher.addAudioProcessor(gainProcessor);

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
                    if (encoder != null && !encoder.isClosed()) {
                        byte[] encoded = encoder.encode(Utils.byteToShort(data));
                        audioQueue.add(encoded);
                    }
                }));

                dispatcher.run();
                } catch (Exception e) {
                    MixerPlugin.getPlugin().getLogger().warning("Error in DSP processing: " + e.getMessage());
                }
            });
    }

    private void handlePlaybackException(AudioTrack track, FriendlyException exception) {
        String errorMessage;

        FileConfiguration config = MixerPlugin.getPlugin().getConfig();
        boolean shouldNotifyPlayer = config.getBoolean("mixer.errorsNotifiers.notifyPlayer", false);
        boolean shouldLog = config.getBoolean("mixer.errorsNotifiers.log", true);

        // --- RETRY LOGIC START ---
        TrackContext context = (TrackContext) track.getUserData();
        if (context == null) {
            context = new TrackContext(track.getInfo().uri, 0);
        }

        if (context.retries < MAX_RETRIES) {
            context.retries++;
            int attempt = context.retries;

            MixerPlugin.getPlugin().getLogger().warning(
                    "Playback failed for " + track.getInfo().title + " (Attempt " + attempt + "/" + MAX_RETRIES + "). Retrying in " + (RETRY_DELAY_MS/1000) + "s..."
            );

            if (shouldNotifyPlayer) {
                location.getNearbyPlayers(10).forEach(p -> p.sendMessage(MiniMessage.miniMessage().deserialize(
                        "<yellow>Connection lost. Retrying... (" + attempt + "/" + MAX_RETRIES + ")</yellow>"
                )));
            }

            // Schedule retry
            final TrackContext retryContext = context;
            Bukkit.getScheduler().runTaskLaterAsynchronously(MixerPlugin.getPlugin(), () -> {
                retryLoad(retryContext);
            }, (RETRY_DELAY_MS / 50)); // Convert ms to ticks (approx)

            return; // Don't proceed to error handling/skipping
        }

        if (exception.getCause() instanceof RuntimeException) {
            RuntimeException cause = (RuntimeException) exception.getCause();
            String causeMessage = cause.getMessage();

            if (causeMessage != null && causeMessage.contains("Not success status code: 403")) {
                errorMessage = "<yellow>Track URL expired. Please reload the track.</yellow>";
                shouldLog = false;
                logThrottled("HTTP 403 for track: " + track.getInfo().uri, 300000); // 5 minutes
            }
            else if (causeMessage != null && causeMessage.contains("Not success status code: 404")) {
                errorMessage = "<red>Track not found (404). URL may be invalid.</red>";
            }
            else if (causeMessage != null && causeMessage.contains("Not success status code: 429")) {
                errorMessage = "<yellow>Rate limit reached. Please wait before retrying.</yellow>";
            }
            else { errorMessage = "<red>Playback error: " + exception.getMessage() + "</red>"; }
        }
        else { errorMessage = "<red>Playback error: " + exception.getMessage() + "</red>"; }

        if (shouldNotifyPlayer) { location.getNearbyPlayers(10).forEach(p -> { p.sendMessage(MiniMessage.miniMessage().deserialize(errorMessage)); }); }
        if (shouldLog) { MixerPlugin.getPlugin().getLogger().warning("Playback error for track [" + track.getInfo().title + "]: " + exception.getMessage()); }

        if (!playlist.isEmpty()) { start(); }
    }

    private void logThrottled(String message, long throttleMs) {
        long now = System.currentTimeMillis();
        Long lastTime = lastLogTimes.get(message);

        if (lastTime == null || (now - lastTime) > throttleMs) {
            MixerPlugin.getPlugin().getLogger().warning(message);
            lastLogTimes.put(message, now);
        }
    }
}