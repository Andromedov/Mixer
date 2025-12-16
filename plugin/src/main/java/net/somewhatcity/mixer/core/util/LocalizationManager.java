package net.somewhatcity.mixer.core.util;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class LocalizationManager {
    private static final int latestLangVersion = 1;
    private final JavaPlugin plugin;
    private final Map<String, FileConfiguration> languageConfigs = new HashMap<>();
    private String currentLanguage = "en";

    public LocalizationManager(JavaPlugin plugin) {
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
            plugin.getLogger().warning("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
            plugin.getLogger().warning("Your language file '" + langCode + ".yml' is outdated!");
            plugin.getLogger().warning("A new file with version " + latestVersion + " will be created.");
            plugin.getLogger().warning("Your old file has been backed up to: " + oldFile.getName());
            plugin.getLogger().warning("Please update your custom messages from the old file.");
            plugin.getLogger().warning("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
        }

        plugin.saveResource("lang/" + langCode + ".yml", false);
    }

    public void setLanguage(String langCode) {
        if (languageConfigs.containsKey(langCode)) {
            this.currentLanguage = langCode;
        } else {
            plugin.getLogger().warning("Language " + langCode + " not found, using default (en)");
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

    public String getPrefix() {
        return getMessage("plugin.prefix");
    }

    public void reloadLanguages() {
        languageConfigs.clear();
        loadLanguages();
    }
}
