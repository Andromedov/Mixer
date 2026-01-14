package net.somewhatcity.mixer.core;

import de.maxhenkel.voicechat.api.BukkitVoicechatService;
import net.somewhatcity.mixer.api.MixerApi;
import net.somewhatcity.mixer.core.api.ImplMixerApi;
import net.somewhatcity.mixer.core.audio.EntityMixerAudioPlayer;
import net.somewhatcity.mixer.core.audio.IMixerAudioPlayer;
import net.somewhatcity.mixer.core.commands.CommandRegistry;
import net.somewhatcity.mixer.core.gui.DspGui;
import net.somewhatcity.mixer.core.gui.PortableSpeakerGui;
import net.somewhatcity.mixer.core.listener.*;
import net.somewhatcity.mixer.core.papi.MixerPapiExpansion;
import net.somewhatcity.mixer.core.util.LocalizationManager;
import net.somewhatcity.mixer.core.util.MessageUtil;
import net.somewhatcity.mixer.core.util.UpdateChecker;
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

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class MixerPlugin extends JavaPlugin {
    private static MixerPlugin plugin;
    private ImplMixerApi api;
    private static final String PLUGIN_ID = "mixer";

    // Jukebox Map
    private final HashMap<Location, IMixerAudioPlayer> playerHashMap = new HashMap<>();

    // Entity/Player Map
    private final Map<UUID, EntityMixerAudioPlayer> portablePlayerMap = new ConcurrentHashMap<>();

    private LocalizationManager localizationManager;
    private File dataFile;
    private FileConfiguration mixersConfig;
    protected PlayerInteractListener playerInteractListener;

    // GUIs
    private PortableSpeakerGui portableSpeakerGui;
    private DspGui dspGui;

    // Config
    private boolean youtubeEnabled;
    private boolean youtubeUseOAuth;
    private String youtubeRefreshToken;
    private int volumePercent;
    private int audioSampleRate;
    private int audioBufferSize;
    private int audioFrameBufferDuration;
    private String language;
    private String debugLevel; // "NONE", "WARNING", "ALL"

    // Portable Speaker Config
    private boolean portableSpeakerEnabled;
    private int portableSpeakerRange;
    private String portableSpeakerItemMaterial;

    // Update Notifier Config
    private boolean updateNotifierEnabled;
    private boolean updateNotifierJoin;

    @Override
    public void onEnable() {
        plugin = this;

        cleanupOpusTemp();

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
            logDebug(Level.INFO, "VoiceChat not found", null);
        }

        playerInteractListener = new PlayerInteractListener();
        PluginManager pm = getServer().getPluginManager();

        // Listener registration
        pm.registerEvents(playerInteractListener, this);
        pm.registerEvents(new RedstoneListener(), this);
        pm.registerEvents(new PlayerQuitListener(), this);
        pm.registerEvents(new PlayerItemListener(), this);
        pm.registerEvents(new InventoryListener(), this);

        // Update Notifier Listener
        UpdateNotifyListener updateNotifyListener = new UpdateNotifyListener(this);
        pm.registerEvents(updateNotifyListener, this);

        // GUI registration
        portableSpeakerGui = new PortableSpeakerGui();
        pm.registerEvents(portableSpeakerGui, this);

        // Register DSP GUI
        dspGui = new DspGui();
        pm.registerEvents(dspGui, this);

        this.api = new ImplMixerApi(this);
        Bukkit.getServicesManager().register(MixerApi.class, api, this, ServicePriority.Normal);

        setupLogFilters();

        // Register PlaceholderAPI expansion
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new MixerPapiExpansion(this).register();
            logDebug(Level.INFO, "PlaceholderAPI expansion registered!", null);
        } else {
            logDebug(Level.INFO, "PlaceholderAPI not found. Placeholders will not work.", null);
        }

        // Check for updates
        new UpdateChecker(this).check((version, id) -> {
            updateNotifyListener.setNewVersion(version, id);
        });
    }

    private void cleanupOpusTemp() {
        try {
            String tempDir = System.getProperty("java.io.tmpdir");
            File dir = new File(tempDir);
            File[] files = dir.listFiles((d, name) -> name.startsWith("opus4j-"));
            if (files != null) {
                for (File f : files) {
                    if (!deleteRecursively(f)) {
                        logDebug(Level.WARNING, "Couldn't delete temp file: " + f.getAbsolutePath(), null);
                    } else {
                        logDebug(Level.INFO, "Cleaned up old opus4j temp files: " + f.getName(), null);
                    }
                }
            }
        } catch (Exception e) {
            logDebug(Level.WARNING, "Failed to cleanup opus4j temp files", e);
        }
    }

    private boolean deleteRecursively(File file) {
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File c : files) {
                    deleteRecursively(c);
                }
            }
        }
        return file.delete();
    }

    private void initializeConfig() {
        File configFile = new File(getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            saveDefaultConfig();
        } else {
            updateConfig();
        }

        reloadConfig();
        FileConfiguration config = getConfig();
        loadConfigValues(config);
    }

    /**
     * Reads the default config from JAR (with comments) and merges user values into it.
     * This preserves new comments/structure while keeping user settings.
     */
    private void updateConfig() {
        try {
            File configFile = new File(getDataFolder(), "config.yml");
            FileConfiguration currentConfig = YamlConfiguration.loadConfiguration(configFile);

            // Read lines from the internal resource (JAR)
            List<String> templateLines = new ArrayList<>();
            try (java.io.InputStream is = getResource("config.yml");
                 java.util.Scanner scanner = new java.util.Scanner(is, StandardCharsets.UTF_8)) {
                while (scanner.hasNextLine()) {
                    templateLines.add(scanner.nextLine());
                }
            }

            List<String> newLines = new ArrayList<>();
            Map<Integer, String> context = new HashMap<>(); // To track YAML indentation path

            for (String line : templateLines) {
                String trimmed = line.trim();

                if (trimmed.startsWith("#") || trimmed.isEmpty()) {
                    newLines.add(line);
                    continue;
                }

                if (line.contains(":")) {
                    String[] parts = line.split(":", 2);
                    String keyPart = parts[0];

                    int indentation = 0;
                    while (indentation < keyPart.length() && keyPart.charAt(indentation) == ' ') {
                        indentation++;
                    }

                    String keyName = keyPart.trim();
                    context.put(indentation, keyName);
                    int finalIndentation = indentation;
                    context.keySet().removeIf(k -> k > finalIndentation);

                    StringBuilder fullKeyBuilder = new StringBuilder();
                    for (int i = 0; i <= indentation; i++) {
                        if (context.containsKey(i)) {
                            if (fullKeyBuilder.length() > 0) fullKeyBuilder.append(".");
                            fullKeyBuilder.append(context.get(i));
                        }
                    }
                    String fullKey = fullKeyBuilder.toString();

                    if (currentConfig.contains(fullKey) && !currentConfig.isConfigurationSection(fullKey)) {
                        Object userValue = currentConfig.get(fullKey);
                        String valueStr;

                        if (userValue instanceof String) {
                            valueStr = "\"" + userValue.toString() + "\"";
                        } else {
                            valueStr = userValue.toString();
                        }

                        newLines.add(keyPart + ": " + valueStr);
                    } else {
                        newLines.add(line);
                    }
                } else {
                    newLines.add(line);
                }
            }

            // Write the merged content back to disk
            Files.write(configFile.toPath(), newLines, StandardCharsets.UTF_8);

        } catch (Exception e) {
            logDebug(Level.SEVERE, "Failed to update config.yml structure. Please check file permissions or syntax.", e);
        }
    }

    private void loadConfigValues(FileConfiguration config) {
        youtubeEnabled = config.getBoolean("mixer.youtube.enabled", false);
        youtubeUseOAuth = config.getBoolean("mixer.youtube.useOAuth", false);
        youtubeRefreshToken = config.getString("mixer.youtube.refreshToken", "");
        volumePercent = config.getInt("mixer.volume", 50);
        audioSampleRate = config.getInt("mixer.audio.sampleRate", 48000);
        audioBufferSize = config.getInt("mixer.audio.bufferSize", 960);
        audioFrameBufferDuration = config.getInt("mixer.audio.frameBufferDuration", 100);
        language = config.getString("lang", "en");

        // Load debug level
        debugLevel = config.getString("system.debugLevel", "WARNING").toUpperCase();

        portableSpeakerEnabled = config.getBoolean("portableSpeakers.portableSpeaker", true);
        portableSpeakerRange = config.getInt("portableSpeakers.portableSpeakerRange", 100);
        portableSpeakerItemMaterial = config.getString("portableSpeakers.portableSpeakerItemMaterial", "NOTE_BLOCK");

        updateNotifierEnabled = config.getBoolean("updateNotifier.enabled", true);
        updateNotifierJoin = config.getBoolean("updateNotifier.on-join", true);

        // Volume validation
        if (volumePercent < 0 || volumePercent > 200) {
            logDebug(Level.WARNING, "Invalid volume percentage: " + volumePercent + ". Setting to 50%", null);
            volumePercent = 50;
            config.set("mixer.volume", 50);
            saveConfig();
        }
    }

    private void setupLogFilters() {
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

        logDebug(Level.INFO, "Mixer filters enabled: HTTP 403/410 and Loading errors will be suppressed.", null);
    }

    /**
     * Smart logging method based on the configured debug level.
     * @param level The severity level (Level.INFO, Level.WARNING, or Level.SEVERE)
     * @param message The message to log
     * @param e The exception that occurred (nullable)
     */
    public void logDebug(Level level, String message, Throwable e) {
        if ("NONE".equals(debugLevel)) {
            if (level == Level.INFO) {
                getLogger().log(level, message);
                return;
            }
            return;
        }

        if ("ALL".equals(debugLevel)) {
            // Full stack trace
            getLogger().log(level, message, e);
        } else {
            // "WARNING" (default) - Only message, but always show INFO
            if (level == Level.INFO) {
                getLogger().log(level, message);
            } else if (e != null) {
                getLogger().log(level, message + ": " + e.getMessage());
            } else {
                getLogger().log(level, message);
            }
        }
    }

    @Override
    public void onDisable() {
        // Stop audio players
        new ArrayList<>(playerHashMap.values()).forEach(player -> {
            try {
                player.stop();
            } catch (Exception e) {
                logDebug(Level.WARNING, "Error stopping audio player during shutdown", e);
            }
        });

        // Stop portable audio players
        new ArrayList<>(portablePlayerMap.values()).forEach(player -> {
            try {
                player.stop();
            } catch (Exception e) {
                logDebug(Level.WARNING, "Error stopping portable audio player during shutdown", e);
            }
        });
        portablePlayerMap.clear();
    }

    public HashMap<Location, IMixerAudioPlayer> playerHashMap() { return playerHashMap; }
    public Map<UUID, EntityMixerAudioPlayer> getPortablePlayerMap() { return portablePlayerMap; }
    public PortableSpeakerGui getPortableSpeakerGui() { return portableSpeakerGui; }
    public DspGui getDspGui() { return dspGui; }

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
    public String getDebugLevel() { return debugLevel; }

    public boolean isPortableSpeakerEnabled() { return portableSpeakerEnabled; }
    public int getPortableSpeakerRange() { return portableSpeakerRange; }
    public String getPortableSpeakerItemMaterial() { return portableSpeakerItemMaterial; }

    public boolean isUpdateNotifierEnabled() { return updateNotifierEnabled; }
    public boolean isUpdateNotifierJoin() { return updateNotifierJoin; }

    public FileConfiguration getMixersConfig() { return mixersConfig; }
    public static MixerPlugin getPlugin() { return plugin; }
    public static String getPluginId() { return PLUGIN_ID; }

    public void saveMixersConfig() {
        try {
            mixersConfig.save(dataFile);
        } catch (IOException e) {
            logDebug(Level.WARNING, "Could not save data.yml: " + e.getMessage(), null);
        }
    }

    public void reloadPluginConfig() {
        reloadConfig();
        initializeConfig();
        localizationManager.setLanguage(language);

        for (IMixerAudioPlayer player : playerHashMap.values()) {
            player.updateVolume();
        }
        for (EntityMixerAudioPlayer player : portablePlayerMap.values()) {
            player.updateVolume();
        }
    }
}