package net.somewhatcity.mixer.core.util;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import net.somewhatcity.mixer.core.MixerPlugin;
import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

public class LocalizationManager {
    private static final int latestLangVersion = 3;
    private final MixerPlugin plugin;
    private final Map<String, FileConfiguration> languageConfigs = new HashMap<>();
    private String currentLanguage = "en";

    public LocalizationManager(MixerPlugin plugin) {
        this.plugin = plugin;
        loadLanguages();
    }

    private void loadLanguages() {
        File langDir = new File(plugin.getDataFolder(), "lang");
        if (!langDir.exists()) {
            langDir.mkdirs();
        }

        createDefaultLanguageFile("en", latestLangVersion);
        createDefaultLanguageFile("uk", latestLangVersion);

        File[] langFiles = langDir.listFiles((dir, name) -> name.endsWith(".yml"));
        if (langFiles != null) {
            for (File langFile : langFiles) {
                String langCode = langFile.getName().replace(".yml", "");
                FileConfiguration config = YamlConfiguration.loadConfiguration(langFile);
                languageConfigs.put(langCode, config);
            }
        }
    }

    private void createDefaultLanguageFile(String langCode, int latestVersion) {
        File langFile = new File(plugin.getDataFolder(), "lang/" + langCode + ".yml");

        if (langFile.exists()) {
            FileConfiguration diskConfig = YamlConfiguration.loadConfiguration(langFile);
            int diskVersion = diskConfig.getInt("lang-version", 0);

            if (diskVersion >= latestVersion) {
                return;
            }

            File oldFile = new File(plugin.getDataFolder(), "lang/" + langCode + "_v" + diskVersion + ".yml.old");
            if (oldFile.exists()) {
                oldFile.delete();
            }

            langFile.renameTo(oldFile);
            plugin.logDebug(Level.WARNING, "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!", null);
            plugin.logDebug(Level.WARNING, "Your language file '" + langCode + ".yml' is outdated!", null);
            plugin.logDebug(Level.WARNING, "A new file with version " + latestVersion + " will be created.", null);
            plugin.logDebug(Level.WARNING, "Your old file has been backed up to: " + oldFile.getName(), null);
            plugin.logDebug(Level.WARNING, "Please update your custom messages from the old file.", null);
            plugin.logDebug(Level.WARNING, "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!", null);
        }

        plugin.saveResource("lang/" + langCode + ".yml", false);
    }

    public void setLanguage(String langCode) {
        if (languageConfigs.containsKey(langCode)) {
            this.currentLanguage = langCode;
        } else {
            MixerPlugin.getPlugin().logDebug(Level.WARNING, "Language " + langCode + " not found, using default (en)", null);
            this.currentLanguage = "en";
        }
    }

    public String getMessage(String key) {
        FileConfiguration config = languageConfigs.get(currentLanguage);
        if (config == null) {
            config = languageConfigs.get("en"); // Fallback
        }

        if (config != null && config.contains("messages." + key)) {
            return config.getString("messages." + key);
        }

        return "[Missing translation: " + key + "]";
    }

    public String getMessage(String key, Object... args) {
        String message = getMessage(key);
        return String.format(message, args);
    }

    public List<String> getMessageList(String key) {
        FileConfiguration config = languageConfigs.get(currentLanguage);
        if (config == null) {
            config = languageConfigs.get("en"); // Fallback
        }

        if (config != null && config.contains("messages." + key)) {
            return config.getStringList("messages." + key);
        }

        return Collections.singletonList("[Missing translation list: " + key + "]");
    }

    public String getPrefix() {
        return getMessage("plugin.prefix");
    }

    public void reloadLanguages() {
        languageConfigs.clear();
        loadLanguages();
    }
}