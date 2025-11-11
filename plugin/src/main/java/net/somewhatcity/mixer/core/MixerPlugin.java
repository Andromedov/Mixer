/*
 * Copyright (c) 2024 mrmrmystery
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice (including the next paragraph) shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

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
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
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

        registerCustomJukeboxSongs();

        playerInteractListener = new PlayerInteractListener();
        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(playerInteractListener, this);
        pm.registerEvents(new RedstoneListener(), this);

        this.api = new ImplMixerApi(this);
        Bukkit.getServicesManager().register(MixerApi.class, api, this, ServicePriority.Normal);
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

    private void registerCustomJukeboxSongs() {
        try {
            NamespacedKey mixerKey = new NamespacedKey(this, "mixer_data");

            getServer().getScheduler().runTaskLater(this, () -> {
                try {
                    getLogger().info("Registering custom jukebox song key: " + mixerKey);
                } catch (Exception e) {
                    getLogger().warning("Could not register jukebox song: " + e.getMessage());
                }
            }, 1L);

        } catch (Exception e) {
            getLogger().warning("JukeboxSong registry not available, using fallback approach");
        }
    }

    @Override
    public void onDisable() {
        playerHashMap.values().forEach(player -> {
            try {
                player.stop();
            } catch (Exception e) {
                getLogger().warning("Error stopping audio player during shutdown: " + e.getMessage());
            }
        });
    }

    public HashMap<Location, IMixerAudioPlayer> playerHashMap() {
        return playerHashMap;
    }
    public MixerApi api() {
        return api;
    }

    public LocalizationManager getLocalizationManager() {
        return localizationManager;
    }

    public boolean isYoutubeEnabled() {
        return youtubeEnabled;
    }

    public boolean isYoutubeUseOAuth() {
        return youtubeUseOAuth;
    }

    public String getYoutubeRefreshToken() {
        return youtubeRefreshToken;
    }

    public int getVolumePercent() {
        return volumePercent;
    }

    public float getVolumeMultiplier() {
        return volumePercent / 100.0f;
    }

    public int getAudioSampleRate() {
        return audioSampleRate;
    }

    public int getAudioBufferSize() {
        return audioBufferSize;
    }

    public int getAudioFrameBufferDuration() {
        return audioFrameBufferDuration;
    }

    public String getLanguage() {
        return language;
    }

    public void reloadPluginConfig() {
        reloadConfig();
        initializeConfig();
        localizationManager.setLanguage(language);

        for (IMixerAudioPlayer player : playerHashMap.values()) {
            player.updateVolume();
        }
    }

    public FileConfiguration getMixersConfig() {
        return mixersConfig;
    }

    public void saveMixersConfig() {
        try {
            mixersConfig.save(dataFile);
        } catch (IOException e) {
            getLogger().warning("Could not save data.yml: " + e.getMessage());
        }
    }

    public static MixerPlugin getPlugin() {
        return plugin;
    }

    public static String getPluginId() {
        return PLUGIN_ID;
    }
}
