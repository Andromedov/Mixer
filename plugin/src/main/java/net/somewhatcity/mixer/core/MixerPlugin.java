package net.somewhatcity.mixer.core;

import de.maxhenkel.voicechat.api.BukkitVoicechatService;
import net.somewhatcity.mixer.api.MixerApi;
import net.somewhatcity.mixer.core.api.ImplMixerApi;
import net.somewhatcity.mixer.core.audio.IMixerAudioPlayer;
import net.somewhatcity.mixer.core.commands.CommandRegistry;
import net.somewhatcity.mixer.core.listener.PlayerInteractListener;
import net.somewhatcity.mixer.core.listener.RedstoneListener;
import net.somewhatcity.mixer.core.util.LocalizationManager;
import net.somewhatcity.mixer.core.util.MessageUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.filter.AbstractFilter;
import org.apache.logging.log4j.core.Filter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

public class MixerPlugin extends JavaPlugin {
    private static MixerPlugin plugin;
    private ImplMixerApi api;
    private static final String PLUGIN_ID = "mixer";
    private final HashMap<Location, IMixerAudioPlayer> playerHashMap = new HashMap<>();
    private LocalizationManager localizationManager;
    private File dataFile;
    private FileConfiguration mixersConfig;
    protected PlayerInteractListener playerInteractListener;

    // Config
    private boolean youtubeEnabled;
    private boolean youtubeUseOAuth;
    private String youtubeRefreshToken;
    private int volumePercent;
    private int audioSampleRate;
    private int audioBufferSize;
    private int audioFrameBufferDuration;
    private String language;

    @Override
    public void onEnable() {
        plugin = this;
        initializeConfig();

        localizationManager = new LocalizationManager(this);
        localizationManager.setLanguage(language);
        MessageUtil.initialize(localizationManager);

        dataFile = new File(getDataFolder(), "data.yml");
        if (!dataFile.exists()) {
            saveResource("data.yml", false);
        }
        mixersConfig = YamlConfiguration.loadConfiguration(dataFile);

        new CommandRegistry(this).registerCommands();

        BukkitVoicechatService vcService = getServer().getServicesManager().load(BukkitVoicechatService.class);
        if (vcService != null) {
            MixerVoicechatPlugin voicechatPlugin = new MixerVoicechatPlugin();
            vcService.registerPlugin(voicechatPlugin);
        } else {
            getLogger().info("VoiceChat not found");
        }

        // registerCustomJukeboxSongs();

        playerInteractListener = new PlayerInteractListener();
        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(playerInteractListener, this);
        pm.registerEvents(new RedstoneListener(), this);

        this.api = new ImplMixerApi(this);
        Bukkit.getServicesManager().register(MixerApi.class, api, this, ServicePriority.Normal);

        ((Logger) LogManager.getRootLogger()).addFilter(new AbstractFilter() {
            @Override
            public Result filter(LogEvent event) {
                String loggerName = event.getLoggerName();
                Throwable thrown = event.getThrown();
                if (loggerName.contains("LocalAudioTrackExecutor")) {
                    if (thrown != null) {
                        String msg = thrown.getMessage();
                        if (msg != null && (msg.contains("403") || msg.contains("410") || msg.contains("Something broke"))) {
                            return Filter.Result.DENY;
                        }

                        if (thrown.getCause() != null) {
                            String causeMsg = thrown.getCause().getMessage();
                            if (causeMsg != null && (causeMsg.contains("403") || causeMsg.contains("410"))) {
                                return Filter.Result.DENY;
                            }
                        }
                    }
                }

                if (loggerName.contains("DefaultAudioPlayerManager")) {
                    String logMsg = event.getMessage().getFormattedMessage();
                    if (logMsg.contains("Error in loading item")) {
                        if (thrown != null) {
                            String msg = thrown.getMessage();
                            if (msg != null && msg.contains("Something went wrong when looking up the track")) {
                                return Filter.Result.DENY;
                            }
                            if (thrown.getCause() != null) {
                                String causeMsg = thrown.getCause().getMessage();
                                if (causeMsg != null && causeMsg.contains("Did not detect any supported formats")) {
                                    return Filter.Result.DENY;
                                }
                            }
                        }
                    }
                }
                return Filter.Result.NEUTRAL;
            }
        });

        getLogger().info("Mixer filters enabled: HTTP 403/410 and Loading errors will be suppressed.");
    }

    private void initializeConfig() {
        saveDefaultConfig();
        FileConfiguration config = getFileConfiguration();
        config.options().copyDefaults(true);
    }

    private @NotNull FileConfiguration getFileConfiguration() {
        FileConfiguration config = getConfiguration();

        youtubeEnabled = config.getBoolean("mixer.youtube.enabled");
        youtubeUseOAuth = config.getBoolean("mixer.youtube.useOAuth");
        youtubeRefreshToken = config.getString("mixer.youtube.refreshToken", "");
        volumePercent = config.getInt("mixer.volume");
        audioSampleRate = config.getInt("mixer.audio.sampleRate");
        audioBufferSize = config.getInt("mixer.audio.bufferSize");
        audioFrameBufferDuration = config.getInt("mixer.audio.frameBufferDuration");
        language = config.getString("lang", "en");

        if (volumePercent < 0 || volumePercent > 200) {
            getLogger().warning("Invalid volume percentage: " + volumePercent + ". Setting to 50%");
            volumePercent = 50;
            config.set("mixer.volume", 50);
        }

        return config;
    }

    private @NotNull FileConfiguration getConfiguration() {
        FileConfiguration config = getConfig();

        // YouTube
        config.addDefault("mixer.youtube.enabled", false);
        config.addDefault("mixer.youtube.useOAuth", false);
        config.addDefault("mixer.youtube.refreshToken", "");

        // Volume
        config.addDefault("mixer.volume", 50);

        // Audio
        config.addDefault("mixer.audio.sampleRate", 48000);
        config.addDefault("mixer.audio.bufferSize", 960);
        config.addDefault("mixer.audio.frameBufferDuration", 100);

        // Language
        config.addDefault("lang", "en");
        return config;
    }

    @Override
    public void onDisable() {
        new ArrayList<>(playerHashMap.values()).forEach(player -> {
            try {
                player.stop();
            } catch (Exception e) {
                getLogger().warning("Error stopping audio player during shutdown: " + e.getMessage());
            }
        });
    }

    public HashMap<Location, IMixerAudioPlayer> playerHashMap() { return playerHashMap; }
    public MixerApi api() { return api; }
    public LocalizationManager getLocalizationManager() { return localizationManager; }
    public boolean isYoutubeEnabled() { return youtubeEnabled; }
    public boolean isYoutubeUseOAuth() { return youtubeUseOAuth; }
    public String getYoutubeRefreshToken() { return youtubeRefreshToken; }
    public int getVolumePercent() { return volumePercent; }
    public float getVolumeMultiplier() { return volumePercent / 100.0f; }
    public int getAudioSampleRate() { return audioSampleRate; }
    public int getAudioBufferSize() { return audioBufferSize; }
    public int getAudioFrameBufferDuration() { return audioFrameBufferDuration; }
    public String getLanguage() { return language; }

    public FileConfiguration getMixersConfig() { return mixersConfig; }
    public static MixerPlugin getPlugin() { return plugin; }
    public static String getPluginId() { return PLUGIN_ID; }

    public void saveMixersConfig() {
        try {
            mixersConfig.save(dataFile);
        } catch (IOException e) {
            getLogger().warning("Could not save data.yml: " + e.getMessage());
        }
    }

    public void reloadPluginConfig() {
        reloadConfig();
        initializeConfig();
        localizationManager.setLanguage(language);

        for (IMixerAudioPlayer player : playerHashMap.values()) {
            player.updateVolume();
        }
    }
}