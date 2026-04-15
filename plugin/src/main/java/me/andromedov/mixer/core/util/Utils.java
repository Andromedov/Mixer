package me.andromedov.mixer.core.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.andromedov.mixer.core.MixerPlugin;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Jukebox;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import javax.sound.sampled.AudioFormat;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;

public class Utils {
    public static boolean isDisc(ItemStack item) {
        return item.getType().name().contains("MUSIC_DISC");
    }

    public static OkHttpClient client = new OkHttpClient();

    public static JsonObject loadNbtData(Location location, String category) {
        if(!location.getBlock().getType().equals(Material.JUKEBOX)) return null;

        Jukebox jukebox = (Jukebox) location.getBlock().getState();
        NamespacedKey key = new NamespacedKey(MixerPlugin.getPlugin(), category);

        String data = jukebox.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if(data == null || data.isEmpty()) return new JsonObject();

        return (JsonObject) JsonParser.parseString(data);
    }

    public static void saveNbtData(Location location, String category, JsonObject data) {
        if(!location.getBlock().getType().equals(Material.JUKEBOX)) return;
        Jukebox jukebox = (Jukebox) location.getBlock().getState();
        NamespacedKey key = new NamespacedKey(MixerPlugin.getPlugin(), category);

        jukebox.getPersistentDataContainer().set(key, PersistentDataType.STRING, data.toString());
        jukebox.update();
    }

    public static byte[] shortToByte(short[] input) {
        int index;
        int iterations = input.length;

        ByteBuffer bb = ByteBuffer.allocate(input.length * 2);

        for(index = 0; index != iterations; ++index) {
            bb.putShort(input[index]);
        }

        return bb.array();
    }

    public static short[] byteToShort(byte[] input) {
        int iterations = input.length / 2;
        short[] output = new short[iterations];

        ByteBuffer bb = ByteBuffer.wrap(input);

        for (int index = 0; index < iterations; index++) {
            output[index] = bb.getShort();
        }

        return output;
    }

    public static short[] applyVolumeToShortArray(short[] input, float volumeMultiplier) {
        if (volumeMultiplier == 1.0f) {
            return input;
        }

        short[] output = new short[input.length];

        for (int i = 0; i < input.length; i++) {
            int sample = Math.round(input[i] * volumeMultiplier);

            if (sample > Short.MAX_VALUE) {
                sample = Short.MAX_VALUE;
            } else if (sample < Short.MIN_VALUE) {
                sample = Short.MIN_VALUE;
            }

            output[i] = (short) sample;
        }

        return output;
    }

    public static byte[] applyVolumeToByteArray(byte[] input, float volumeMultiplier) {
        if (volumeMultiplier == 1.0f) {
            return input;
        }

        short[] shortData = byteToShort(input);
        short[] processedData = applyVolumeToShortArray(shortData, volumeMultiplier);
        return shortToByte(processedData);
    }

    public static AudioFormat createConfiguredAudioFormat() {
        MixerPlugin plugin = MixerPlugin.getPlugin();
        return new AudioFormat(
                plugin.getAudioSampleRate(),  // Sample rate
                16,                           // 16-bit
                1,                            // Mono
                true,                         // Signed
                true                          // Big endian
        );
    }

    public static void logAudioConfiguration() {
        MixerPlugin plugin = MixerPlugin.getPlugin();
        plugin.logDebug(Level.INFO, "=== Audio Configuration ===", null);
        plugin.logDebug(Level.INFO, "Sample Rate: " + plugin.getAudioSampleRate() + " Hz", null);
        plugin.logDebug(Level.INFO, "Buffer Size: " + plugin.getAudioBufferSize() + " samples", null);
        plugin.logDebug(Level.INFO, "Frame Buffer Duration: " + plugin.getAudioFrameBufferDuration() + " ms", null);
        plugin.logDebug(Level.INFO, "Volume: " + plugin.getVolumePercent() + "% (" + plugin.getVolumeMultiplier() + "x)", null);
        plugin.logDebug(Level.INFO, "YouTube Enabled: " + plugin.isYoutubeEnabled(), null);
        plugin.logDebug(Level.INFO, "Language: " + plugin.getLanguage(), null);
        plugin.logDebug(Level.INFO, "===========================", null);
    }

    public static File downloadFile(String urlStr, String fileName) {
        File audioDir = new File(MixerPlugin.getPlugin().getDataFolder(), "audio");
        if (!audioDir.exists() && !audioDir.mkdirs()) {
            MixerPlugin.getPlugin().logDebug(Level.WARNING, "Could not create audio directory.", null);
            return null;
        }

        File target = new File(audioDir, fileName);
        Request request = new Request.Builder()
                .url(urlStr)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                // Строга перевірка типу контенту, щоб уникнути збереження HTML сторінок
                String contentType = response.header("Content-Type", "");
                if (contentType != null && (contentType.contains("text/html") || contentType.contains("application/json"))) {
                    MixerPlugin.getPlugin().logDebug(Level.WARNING, "Blocked download: URL returned an HTML or JSON page instead of an audio stream. (" + urlStr + ")", null);
                    return null;
                }
                Files.copy(response.body().byteStream(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                return target;
            } else {
                MixerPlugin.getPlugin().logDebug(Level.WARNING, "Failed to download file: HTTP " + response.code(), null);
            }
        } catch (Exception e) {
            MixerPlugin.getPlugin().logDebug(Level.WARNING, "Error downloading audio file", e);
        }
        return null;
    }

    public static String requestCobaltMediaUrl(String url) {
        // Fallback instances for Cobalt to bypass rate-limits or Cloudflare issues
        String[] instances = {
                "https://api.cobalt.tools/",
                "https://co.wuk.sh/",
                "https://cobalt.kwiatekq.uk/",
                "https://api.cobalt.rodeo/"
        };

        JsonObject send = new JsonObject();
        send.addProperty("url", url);
        send.addProperty("downloadMode", "audio");
        send.addProperty("isAudioOnly", true);
        send.addProperty("aFormat", "mp3");

        RequestBody body = RequestBody.create(send.toString().getBytes(StandardCharsets.UTF_8));

        for (String instance : instances) {
            try {
                Request request = new Request.Builder()
                        .url(instance)
                        .addHeader("Accept", "application/json")
                        .addHeader("Content-Type", "application/json")
                        .addHeader("User-Agent", "MixerPlugin/2.3.0 (Java)")
                        .post(body)
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    String res = response.body() != null ? response.body().string() : "";
                    if (response.isSuccessful()) {
                        JsonObject json = (JsonObject) JsonParser.parseString(res);
                        if (json.has("url")) {
                            return json.get("url").getAsString();
                        }
                    } else {
                        MixerPlugin.getPlugin().logDebug(Level.INFO, "Cobalt API (" + instance + ") returned HTTP " + response.code() + ". Trying next mirror...", null);
                    }
                }
            } catch (Exception e) {
                MixerPlugin.getPlugin().logDebug(Level.INFO, "Cobalt API (" + instance + ") failed. Trying next mirror...", null);
            }
        }

        MixerPlugin.getPlugin().logDebug(Level.WARNING, "All Cobalt API instances failed to resolve URL: " + url, null);
        return null;
    }
}