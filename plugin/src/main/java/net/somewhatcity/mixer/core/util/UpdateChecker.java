package net.somewhatcity.mixer.core.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.somewhatcity.mixer.core.MixerPlugin;
import okhttp3.Request;
import okhttp3.Response;
import org.bukkit.Bukkit;

import java.io.IOException;
import java.util.function.BiConsumer;

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

                // Modrinth returns versions sorted by date (newest first)
                String latestVersion = versions.get(0).getAsJsonObject().get("version_number").getAsString();
                String versionId = versions.get(0).getAsJsonObject().get("id").getAsString();

                if (isNewer(currentVersion, latestVersion)) {
                    plugin.getLogger().info("========================================");
                    plugin.getLogger().info("Mixer update available!");
                    plugin.getLogger().info("Current: " + currentVersion);
                    plugin.getLogger().info("New: " + latestVersion);
                    plugin.getLogger().info("Download: https://modrinth.com/plugin/mixer-reloaded/version/" + versionId);
                    plugin.getLogger().info("========================================");

                    Bukkit.getScheduler().runTask(plugin, () -> onSuccess.accept(latestVersion, versionId));
                }

            } catch (IOException e) {
                plugin.getLogger().warning("Failed to check for updates: " + e.getMessage());
            } catch (Exception e) {
                plugin.getLogger().warning("Unexpected error during update check: " + e.getMessage());
            }
        });
    }

    private boolean isNewer(String current, String remote) {
        String c = current.replaceAll("[^0-9.]", "");
        String r = remote.replaceAll("[^0-9.]", "");

        return !c.equalsIgnoreCase(r);
    }
}