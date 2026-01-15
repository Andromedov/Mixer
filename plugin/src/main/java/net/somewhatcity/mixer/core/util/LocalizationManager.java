package net.somewhatcity.mixer.core.util;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import net.somewhatcity.mixer.core.MixerPlugin;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.logging.Level;

public class LocalizationManager {
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
            try {
                langDir.mkdirs();
            } catch (Exception e) {
                plugin.logDebug(Level.FINEST, "Failed to create lang directory", e);
            }
        }

        updateLanguageFile("en");
        updateLanguageFile("uk");

        File[] langFiles = langDir.listFiles((dir, name) -> name.endsWith(".yml"));
        if (langFiles != null) {
            for (File langFile : langFiles) {
                String langCode = langFile.getName().replace(".yml", "");
                FileConfiguration config = YamlConfiguration.loadConfiguration(langFile);
                languageConfigs.put(langCode, config);
            }
        }
    }

    private void updateLanguageFile(String langCode) {
        File langFile = new File(plugin.getDataFolder(), "lang/" + langCode + ".yml");

        if (!langFile.exists()) {
            plugin.saveResource("lang/" + langCode + ".yml", false);
            return;
        }

        try {
            FileConfiguration currentConfig = YamlConfiguration.loadConfiguration(langFile);

            List<String> templateLines = new ArrayList<>();
            InputStream resourceStream = plugin.getResource("lang/" + langCode + ".yml");

            if (resourceStream == null) {
                plugin.logDebug(Level.WARNING, "Resource not found for update: lang/" + langCode + ".yml", null);
                return;
            }

            try (java.util.Scanner scanner = new java.util.Scanner(resourceStream, StandardCharsets.UTF_8)) {
                while (scanner.hasNextLine()) {
                    templateLines.add(scanner.nextLine());
                }
            }

            List<String> newLines = new ArrayList<>();
            Map<Integer, String> context = new HashMap<>();
            boolean skipListItems = false;

            for (String line : templateLines) {
                String trimmed = line.trim();

                if (trimmed.startsWith("#") || trimmed.isEmpty()) {
                    newLines.add(line);
                    continue;
                }

                if (trimmed.startsWith("-")) {
                    if (!skipListItems) {
                        newLines.add(line);
                    }
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

                    if (currentConfig.contains(fullKey)) {
                        Object userValue = currentConfig.get(fullKey);

                        if (currentConfig.isConfigurationSection(fullKey)) {
                            newLines.add(line);
                            skipListItems = false;
                        } else {
                            String yamlValue = formatYamlValue(userValue, indentation);
                            if (yamlValue.contains("\n")) {
                                newLines.add(keyPart + ":");
                                newLines.add(yamlValue);
                            } else {
                                newLines.add(keyPart + ": " + yamlValue);
                            }

                            if (userValue instanceof List) {
                                skipListItems = true;
                            } else {
                                skipListItems = false;
                            }
                        }
                    } else {
                        newLines.add(line);
                        skipListItems = false;
                    }
                } else {
                    newLines.add(line);
                }
            }

            Files.write(langFile.toPath(), newLines, StandardCharsets.UTF_8);

        } catch (Exception e) {
            plugin.logDebug(Level.WARNING, "Failed to merge language file: " + langCode, e);
        }
    }

    private String formatYamlValue(Object value, int indentation) {
        if (value instanceof List<?>) {
            StringBuilder sb = new StringBuilder();
            List<?> list = (List<?>) value;
            String spaces = String.join("", Collections.nCopies(indentation, "  "));

            for (Object item : list) {
                if (sb.length() > 0) sb.append("\n");
                sb.append(spaces).append("- \"").append(item.toString().replace("\"", "\\\"")).append("\"");
            }
            return sb.toString();
        } else if (value instanceof String) {
            return "\"" + value.toString().replace("\"", "\\\"") + "\"";
        } else {
            return value.toString();
        }
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
        try {
            return String.format(message, args);
        } catch (Exception e) {
            return message;
        }
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