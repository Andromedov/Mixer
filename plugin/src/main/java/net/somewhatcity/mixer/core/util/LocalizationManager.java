package net.somewhatcity.mixer.core.util;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class LocalizationManager {
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

        createDefaultLanguageFile("en");
        createDefaultLanguageFile("uk");

        File[] langFiles = langDir.listFiles((dir, name) -> name.endsWith(".yml"));
        if (langFiles != null) {
            for (File langFile : langFiles) {
                String langCode = langFile.getName().replace(".yml", "");
                FileConfiguration config = YamlConfiguration.loadConfiguration(langFile);
                languageConfigs.put(langCode, config);
            }
        }
    }

    private void createDefaultLanguageFile(String langCode) {
        File langFile = new File(plugin.getDataFolder(), "lang/" + langCode + ".yml");
        if (!langFile.exists()) {
            try {
                InputStream resource = plugin.getResource("lang/" + langCode + ".yml");
                if (resource != null) {
                    plugin.saveResource("lang/" + langCode + ".yml", false);
                } else {
                    FileConfiguration config = YamlConfiguration.loadConfiguration(langFile);
                    config.save(langFile);
                }
            } catch (IOException e) {
                plugin.getLogger().warning("Could not create language file: " + langCode + ".yml");
            }
        }
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
