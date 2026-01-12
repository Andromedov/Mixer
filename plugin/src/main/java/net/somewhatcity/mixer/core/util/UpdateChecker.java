package net.somewhatcity.mixer.core.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.somewhatcity.mixer.core.MixerPlugin;
import okhttp3.Request;
import okhttp3.Response;
import org.bukkit.Bukkit;

import java.io.IOException;
import java.util.function.BiConsumer;
import java.util.logging.Level;

public class UpdateChecker {
    private final MixerPlugin plugin;
    private final String currentVersion;
    private static final String MODRINTH_API_URL = "https://api.modrinth.com/v2/project/94MyQ3m6/version";

    public UpdateChecker(MixerPlugin plugin) {
        this.plugin = plugin;
        this.currentVersion = plugin.getDescription().getVersion();
    }

    public void check(BiConsumer<String, String> onSuccess) {
        if (!plugin.isUpdateNotifierEnabled()) return;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Request request = new Request.Builder()
                    .url(MODRINTH_API_URL)
                    .addHeader("User-Agent", "Andromedov/Mixer/" + currentVersion)
                    .build();

            try (Response response = Utils.client.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) return;

                String json = response.body().string();
                JsonElement element = JsonParser.parseString(json);

                if (!element.isJsonArray()) return;
                JsonArray versions = element.getAsJsonArray();

                if (versions.isEmpty()) return;

                for (JsonElement verElement : versions) {
                    JsonObject versionObj = verElement.getAsJsonObject();

                    String versionType = versionObj.get("version_type").getAsString();
                    if (!versionType.equalsIgnoreCase("release")) {
                        continue;
                    }

                    String latestVersion = versionObj.get("version_number").getAsString();
                    String versionId = versionObj.get("id").getAsString();

                    if (isNewer(currentVersion, latestVersion)) {
                        plugin.getLogger().info("========================================");
                        plugin.getLogger().info("Mixer update available!");
                        plugin.getLogger().info("Current: " + currentVersion);
                        plugin.getLogger().info("New: " + latestVersion);
                        plugin.getLogger().info("Download: https://modrinth.com/plugin/mixer-reloaded/version/" + versionId);
                        plugin.getLogger().info("========================================");

                        Bukkit.getScheduler().runTask(plugin, () -> onSuccess.accept(latestVersion, versionId));
                    }
                    break;
                }

            } catch (IOException e) {
                plugin.logDebug(Level.WARNING, "Failed to check for updates", e);
            } catch (Exception e) {
                plugin.logDebug(Level.WARNING, "Unexpected error during update check", e);
            }
        });
    }

    /**
     * @param current Current Version (e.g., 2.2.0)
     * @param remote Modrinth Version (e.g., v2.1.2)
     * @return true, only if remote > current
     */
    private boolean isNewer(String current, String remote) {
        String c = current.replaceAll("[^0-9.]", "");
        String r = remote.replaceAll("[^0-9.]", "");

        String[] cParts = c.split("\\.");
        String[] rParts = r.split("\\.");

        int length = Math.max(cParts.length, rParts.length);

        for (int i = 0; i < length; i++) {
            int cPart = i < cParts.length && !cParts[i].isEmpty() ? Integer.parseInt(cParts[i]) : 0;
            int rPart = i < rParts.length && !rParts[i].isEmpty() ? Integer.parseInt(rParts[i]) : 0;

            if (rPart > cPart) {
                return true;
            } else if (rPart < cPart) {
                return false;
            }
        }

        return false;
    }
}