package net.somewhatcity.mixer.core.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.somewhatcity.mixer.core.MixerPlugin;
import okhttp3.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * Helper for automatically getting a YouTube Refresh Token via Google Device Flow.
 * Reads Client ID and Client Secret from config.yml.
 */
public class YoutubeOAuthHelper {

    private static final OkHttpClient client = new OkHttpClient();
    private static final AtomicBoolean isPolling = new AtomicBoolean(false);

    /**
     * Starts the authorization process.
     * Call this method from a command (e.g., /mixer youtube login)
     */
    public static void startOAuthFlow(Player admin) {
        LocalizationManager lang = MixerPlugin.getPlugin().getLocalizationManager();
        MixerPlugin plugin = MixerPlugin.getPlugin();

        String clientId = plugin.getConfig().getString("mixer.youtube.clientId", "");
        String clientSecret = plugin.getConfig().getString("mixer.youtube.clientSecret", "");

        if (clientId.isEmpty() || clientSecret.isEmpty() || clientId.equals("YOUR_CLIENT_ID")) {
            admin.sendMessage(MiniMessage.miniMessage().deserialize("<red>Google API Credentials are not configured!</red>"));
            admin.sendMessage(MiniMessage.miniMessage().deserialize("<gray>Please create an OAuth 2.0 Client ID (TVs and Limited Input devices) in Google Cloud Console and add it to <yellow>config.yml</yellow>.</gray>"));
            return;
        }

        if (isPolling.get()) {
            admin.sendMessage(MiniMessage.miniMessage().deserialize(lang.getMessage("youtube.already_polling")));
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                RequestBody body = new FormBody.Builder()
                        .add("client_id", clientId)
                        .add("scope", "https://www.googleapis.com/auth/youtube")
                        .build();

                Request request = new Request.Builder()
                        .url("https://oauth2.googleapis.com/device/code")
                        .post(body)
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful() || response.body() == null) {
                        String errorBody = response.body() != null ? response.body().string() : "Empty response body";
                        plugin.logDebug(Level.SEVERE, "Google API OAuth Error (Step 1): HTTP " + response.code() + " - " + errorBody, null);

                        admin.sendMessage(MiniMessage.miniMessage().deserialize(lang.getMessage("youtube.api_error")));
                        return;
                    }

                    JsonObject json = JsonParser.parseString(response.body().string()).getAsJsonObject();
                    String userCode = json.get("user_code").getAsString();
                    String verificationUrl = json.get("verification_url").getAsString();
                    String deviceCode = json.get("device_code").getAsString();
                    int interval = json.get("interval").getAsInt();

                    String instructions = lang.getMessage("youtube.instructions")
                            .replace("%url%", verificationUrl)
                            .replace("%code%", userCode);

                    admin.sendMessage(MiniMessage.miniMessage().deserialize(instructions));

                    isPolling.set(true);
                    pollForToken(admin, deviceCode, interval, clientId, clientSecret);

                }
            } catch (Exception e) {
                plugin.logDebug(Level.SEVERE, "Error during OAuth flow", e);
                isPolling.set(false);
            }
        });
    }

    private static void pollForToken(Player admin, String deviceCode, int interval, String clientId, String clientSecret) {
        LocalizationManager lang = MixerPlugin.getPlugin().getLocalizationManager();
        MixerPlugin plugin = MixerPlugin.getPlugin();

        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task -> {
            if (!isPolling.get()) {
                task.cancel();
                return;
            }

            try {
                RequestBody body = new FormBody.Builder()
                        .add("client_id", clientId)
                        .add("client_secret", clientSecret)
                        .add("device_code", deviceCode)
                        .add("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
                        .build();

                Request request = new Request.Builder()
                        .url("https://oauth2.googleapis.com/token")
                        .post(body)
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (response.body() == null) return;
                    String resStr = response.body().string();
                    JsonObject json = JsonParser.parseString(resStr).getAsJsonObject();

                    // Handles successful token save or varied error responses
                    if (response.isSuccessful()) {
                        String refreshToken = json.get("refresh_token").getAsString();
                        saveTokenAndReload(admin, refreshToken);
                        isPolling.set(false);
                        task.cancel();
                    } else {
                        String error = json.has("error") ? json.get("error").getAsString() : "unknown_error";

                        if (error.equals("authorization_pending")) {
                            return;
                        }

                        if (error.equals("expired_token")) {
                            admin.sendMessage(MiniMessage.miniMessage().deserialize(lang.getMessage("youtube.timeout")));
                        } else {
                            String errorMsg = lang.getMessage("youtube.auth_error").replace("%error%", error);
                            admin.sendMessage(MiniMessage.miniMessage().deserialize(errorMsg));
                            plugin.logDebug(Level.SEVERE, "Google Auth Error details (Step 4): " + resStr, null);
                        }
                        isPolling.set(false);
                        task.cancel();
                    }
                }
            } catch (IOException e) {
                plugin.logDebug(Level.WARNING, "Error during OAuth polling", e);
            }
        }, interval * 20L, interval * 20L); // Bukkit timer ticks (1 sec = 20 ticks)
    }

    private static void saveTokenAndReload(Player admin, String token) {
        LocalizationManager lang = MixerPlugin.getPlugin().getLocalizationManager();
        MixerPlugin plugin = MixerPlugin.getPlugin();

        Bukkit.getScheduler().runTask(plugin, () -> {
            // Save to config
            plugin.getConfig().set("mixer.youtube.enabled", true);
            plugin.getConfig().set("mixer.youtube.useOAuth", true);
            plugin.getConfig().set("mixer.youtube.refreshToken", token);
            plugin.saveConfig();

            // Reload config in plugin
            plugin.reloadPluginConfig();

            admin.sendMessage(MiniMessage.miniMessage().deserialize(lang.getMessage("youtube.success")));
        });
    }
}