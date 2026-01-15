package net.somewhatcity.mixer.core.audio;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.GainProcessor;
import be.tarsos.dsp.effects.FlangerEffect;
import be.tarsos.dsp.filters.HighPass;
import be.tarsos.dsp.filters.LowPassFS;
import be.tarsos.dsp.io.TarsosDSPAudioFormat;
import be.tarsos.dsp.io.jvm.JVMAudioInputStream;
import com.google.gson.JsonObject;
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
import dev.lavalink.youtube.YoutubeAudioSourceManager;
import dev.lavalink.youtube.clients.*;
import net.somewhatcity.mixer.api.MixerAudioPlayer;
import net.somewhatcity.mixer.api.MixerDsp;
import net.somewhatcity.mixer.core.MixerPlugin;
import net.somewhatcity.mixer.core.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;

import javax.sound.sampled.AudioFormat;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;

public abstract class AbstractMixerAudioPlayer implements MixerAudioPlayer {
    public static final AudioPlayerManager APM = new DefaultAudioPlayerManager();

    // Retry settings
    protected static final int MAX_RETRIES = 3;

    static {
        FileConfiguration config = MixerPlugin.getPlugin().getConfig();

        if (config.getBoolean("mixer.youtube.enabled", false)) {
            YoutubeAudioSourceManager youtube = new YoutubeAudioSourceManager(true,
                    new Music(), new Web(), new MusicWithThumbnail(), new WebWithThumbnail(),
                    new TvHtml5Embedded(), new TvHtml5EmbeddedWithThumbnail(), new Android(), new AndroidMusic()
            );

            if (config.getBoolean("mixer.youtube.useOAuth", false)) {
                String refreshToken = config.getString("mixer.youtube.refreshToken", "");
                if (!refreshToken.isEmpty()) {
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

    public static class TrackContext {
        public String originalUrl;
        public int retries;

        public TrackContext(String originalUrl, int retries) {
            this.originalUrl = originalUrl;
            this.retries = retries;
        }
    }

    protected final AudioFormat audioFormat;
    protected final int frameSize;
    protected final int frameBufferDuration;
    protected GainProcessor gainProcessor;

    protected final Object initializationLock = new Object();
    protected volatile boolean isInitialized = false;

    protected MixerDsp dsp;
    protected ScheduledExecutorService audioScheduler;
    protected ScheduledFuture<?> audioTask;
    protected AudioPlayer lavaplayer;
    protected OpusDecoder decoder;
    protected OpusEncoder encoder;
    protected LavaplayerAudioStream audioStream;
    protected volatile boolean running = true;
    protected boolean playbackStarted = false;
    protected AudioDispatcher dispatcher;
    protected JVMAudioInputStream jvmAudioInputStream;

    protected Deque<AudioTrack> playlist = new ConcurrentLinkedDeque<>();
    protected Queue<String> loadingQueue = new ConcurrentLinkedDeque<>();
    protected Queue<byte[]> audioQueue = new ConcurrentLinkedDeque<>();
    protected JsonObject dspSettings;

    // Debug counters
    protected long frameCount = 0;
    protected long decodedBytes = 0;
    protected long encodedBytes = 0;

    public AbstractMixerAudioPlayer() {
        MixerPlugin plugin = MixerPlugin.getPlugin();
        int sampleRate = plugin.getAudioSampleRate();
        this.frameSize = plugin.getAudioBufferSize();
        this.frameBufferDuration = plugin.getAudioFrameBufferDuration();

        this.audioFormat = new AudioFormat(
                sampleRate,
                16,
                1,
                true,
                true
        );

        // Default empty settings
        this.dspSettings = new JsonObject();
    }

    protected void initializeAsync() {
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
                            handlePlaybackException(exception);
                        }

                        @Override
                        public void onTrackStuck(AudioPlayer player, AudioTrack track, long thresholdMs) {
                            MixerPlugin.getPlugin().logDebug(Level.WARNING, "Track stuck: " + track.getInfo().title, null);
                            start();
                        }
                    });

                    audioStream = new LavaplayerAudioStream(audioFormat);
                    decoder = new OpusDecoder((int) audioFormat.getSampleRate(), audioFormat.getChannels());
                    decoder.setFrameSize(frameSize);
                    encoder = new OpusEncoder((int) audioFormat.getSampleRate(), audioFormat.getChannels(), OpusEncoder.Application.AUDIO);

                    if ("ALL".equals(MixerPlugin.getPlugin().getDebugLevel())) {
                        MixerPlugin.getPlugin().logDebug(Level.INFO, "Initializing audio scheduler...", null);
                    }

                    audioScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                        Thread t = new Thread(r, "Mixer-Audio-Thread");
                        t.setPriority(Thread.MAX_PRIORITY);
                        return t;
                    });

                    audioTask = audioScheduler.scheduleAtFixedRate(() -> {
                        if (!running) {
                            if (audioTask != null) audioTask.cancel(false);
                            return;
                        }

                        try {
                            processAudioFrame();
                        } catch (Throwable e) {
                            if (running) {
                                MixerPlugin.getPlugin().logDebug(Level.SEVERE, "Critical error in audio loop", e);
                                if (e instanceof LinkageError) {
                                    stop();
                                }
                            }
                        }
                    }, 0, 20, TimeUnit.MILLISECONDS);

                    Thread.sleep(100);

                    isInitialized = true;
                    initializationLock.notifyAll();

                    if (!loadingQueue.isEmpty() && !playbackStarted && playlist.isEmpty()) {
                        loadNextFromQueue();
                    }
                } catch (Exception e) {
                    MixerPlugin.getPlugin().logDebug(Level.SEVERE, "Failed to initialize audio player", e);
                }
            }
        });
    }

    protected abstract void broadcastAudio(byte[] data);
    protected abstract void notifyUser(String message);

    protected void processAudioFrame() {
        try {
            AudioFrame frame = lavaplayer.provide();

            if (frameCount++ % 100 == 0 && "ALL".equals(MixerPlugin.getPlugin().getDebugLevel())) {
                MixerPlugin.getPlugin().logDebug(Level.INFO,
                        String.format("Stats: Frame=%d, Queue=%d, Decoded=%d, Encoded=%d, Playing=%s",
                                frameCount, audioQueue.size(), decodedBytes, encodedBytes, (frame != null)), null);
            }

            if (frame != null && running) {
                byte[] data = frame.getData();
                byte[] decoded;
                if (data.length >= 1500) {
                    decoded = data; // Assume Raw PCM
                } else if (decoder != null && !decoder.isClosed()) {
                    decoded = Utils.shortToByte(decoder.decode(data));
                } else {
                    decoded = new byte[0];
                }

                if (audioStream != null && decoded.length > 0) {
                    decodedBytes += decoded.length;
                    try {
                        audioStream.appendData(decoded);
                    } catch (Exception ex) {
                        if (running) {
                            MixerPlugin.getPlugin().logDebug(Level.WARNING, "Error appending audio data to stream", ex);
                        }
                    }
                }
            }

            if (!audioQueue.isEmpty() && running) {
                byte[] data = audioQueue.poll();
                if (data != null) {
                    broadcastAudio(data);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Error processing frame", e);
        }
    }

    @Override
    public MixerDsp dsp() { return dsp; }

    public AudioTrack getPlayingTrack() {
        return lavaplayer != null ? lavaplayer.getPlayingTrack() : null;
    }

    @Override
    public void load(String... url) {
        loadingQueue.addAll(List.of(url));
        if (isInitialized && !playbackStarted && playlist.isEmpty()) {
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

    protected void loadNextFromQueue() {
        if (!loadingQueue.isEmpty() && running) {
            String nextUrl = loadingQueue.poll();
            CompletableFuture.runAsync(() -> attemptLoad(nextUrl, 0));
        }
    }

    protected void attemptLoad(String audioUrl, int retryCount) {
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
                    MixerPlugin.getPlugin().logDebug(Level.WARNING, "Max retries reached for Cobalt URL: " + audioUrl, null);
                    notifyUser("<red>Error resolving Cobalt media. Max retries reached.</red>");
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
                loadNextFromQueue();
            }

            @Override
            public void playlistLoaded(AudioPlaylist audioPlaylist) {
                AudioTrack track = audioPlaylist.getSelectedTrack();
                if (track == null && !audioPlaylist.getTracks().isEmpty()) {
                    track = audioPlaylist.getTracks().getFirst();
                }
                if (track != null) {
                    track.setUserData(new TrackContext(audioUrl, 0));
                    configureAndPlay(track);
                }
                loadNextFromQueue();
            }

            @Override
            public void noMatches() {
                if (retryCount < MAX_RETRIES) {
                    scheduleRetry(audioUrl, retryCount, "No matches found");
                } else {
                    MixerPlugin.getPlugin().logDebug(Level.WARNING, "No matches found for URL: " + audioUrl, null);
                    notifyUser("<red>No matches found after retries</red>");
                    loadNextFromQueue();
                }
            }

            @Override
            public void loadFailed(FriendlyException e) {
                if (retryCount < MAX_RETRIES) {
                    scheduleRetry(audioUrl, retryCount, e.getMessage());
                } else {
                    MixerPlugin.getPlugin().logDebug(Level.SEVERE, "Failed to load URL: " + audioUrl, e);
                    notifyUser("<red>Error loading: " + e.getMessage() + "</red>");
                    loadNextFromQueue();
                }
            }
        });
    }

    protected void scheduleRetry(String url, int currentRetry, String reason) {
        int nextRetry = currentRetry + 1;
        MixerPlugin.getPlugin().logDebug(Level.WARNING, "Load failed (" + reason + "). Retrying " + nextRetry + "/" + MAX_RETRIES, null);
        notifyUser("<yellow>Retrying... (" + nextRetry + "/" + MAX_RETRIES + ")</yellow>");
        Bukkit.getScheduler().runTaskLaterAsynchronously(MixerPlugin.getPlugin(), () -> attemptLoad(url, nextRetry), 60L);
    }

    // Subclasses can override this to save config
    protected void configureAndPlay(AudioTrack track) {
        playlist.add(track);
        if (!playbackStarted) {
            loadDsp();
            start();
            playbackStarted = true;
        }
    }

    @Override
    public void stop() {
        running = false;

        if (audioTask != null) {
            audioTask.cancel(true);
            audioTask = null;
        }
        if (audioScheduler != null) {
            audioScheduler.shutdownNow();
            audioScheduler = null;
        }

        if (dispatcher != null) {
            try { dispatcher.stop(); } catch (Exception ignored) {}
            dispatcher = null;
        }
        if (encoder != null) { try { encoder.close(); } catch (Exception ignored) {} }
        if (decoder != null) { try { decoder.close(); } catch (Exception ignored) {} }
        if (audioStream != null) { try { audioStream.close(); } catch (Exception ignored) {} }
        if (jvmAudioInputStream != null) { try { jvmAudioInputStream.close(); } catch (IOException ignored) {} }
    }

    protected void start() {
        if (!running) return;
        AudioTrack track = playlist.poll();
        if (track != null) {
            if ("ALL".equals(MixerPlugin.getPlugin().getDebugLevel())) {
                MixerPlugin.getPlugin().logDebug(Level.INFO, "Starting lavaplayer playback: " + track.getInfo().title, null);
            }
            lavaplayer.playTrack(track);
        }
        else {
            if (!loadingQueue.isEmpty()) { loadNextFromQueue(); }
        }
    }

    public void updateVolume() {
        if (this.gainProcessor == null) return;

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

    public void reloadDspSettings() {
        if (this instanceof IMixerAudioPlayer) {
            Location loc = this.location();
            this.dspSettings = Utils.loadNbtData(loc, "mixer_dsp");
            if (this.dspSettings == null) this.dspSettings = new JsonObject();
        }
    }

    public void loadDsp() {
        if (dispatcher != null && !dispatcher.isStopped()) {
            dispatcher.stop();
            dispatcher = null;
        }

        CompletableFuture.runAsync(() -> {
            try { Thread.sleep(200); } catch (InterruptedException e) { return; }
            if (!running) return;

            if ("ALL".equals(MixerPlugin.getPlugin().getDebugLevel())) {
                MixerPlugin.getPlugin().logDebug(Level.INFO, "Starting DSP dispatcher thread...", null);
            }

            try {
                if (audioStream == null) {
                    MixerPlugin.getPlugin().logDebug(Level.WARNING, "AudioStream is null in loadDsp, attempting to recover...", null);
                    audioStream = new LavaplayerAudioStream(audioFormat);
                }

                jvmAudioInputStream = new JVMAudioInputStream(audioStream);
                dispatcher = new AudioDispatcher(jvmAudioInputStream, frameSize, 0);
                TarsosDSPAudioFormat format = dispatcher.getFormat();

                float currentVolumeMultiplier = MixerPlugin.getPlugin().getVolumeMultiplier();
                JsonObject gainSettings = dspSettings.getAsJsonObject("gain");
                if (gainSettings != null) {
                    double configuredGain = gainSettings.get("gain").getAsDouble();
                    double finalGain = configuredGain * currentVolumeMultiplier;
                    gainProcessor = new GainProcessor(finalGain);
                } else {
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
                    encodedBytes += data.length;
                    if (encoder != null && !encoder.isClosed()) {
                        byte[] encoded = encoder.encode(Utils.byteToShort(data));
                        audioQueue.add(encoded);
                    }
                }));

                dispatcher.run();
                if ("ALL".equals(MixerPlugin.getPlugin().getDebugLevel())) {
                    MixerPlugin.getPlugin().logDebug(Level.INFO, "DSP dispatcher stopped naturally.", null);
                }
            } catch (Exception e) {
                MixerPlugin.getPlugin().logDebug(Level.WARNING, "Error in DSP processing", e);
            }
        });
    }

    protected void handlePlaybackException(FriendlyException exception) {
        String errorMessage = "<red>Playback error: " + exception.getMessage() + "</red>";
        notifyUser(errorMessage);
        MixerPlugin.getPlugin().logDebug(Level.WARNING, "FriendlyException in track playback", exception);
        if (!playlist.isEmpty()) { start(); }
    }
}