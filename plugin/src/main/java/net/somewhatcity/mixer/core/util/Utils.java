package net.somewhatcity.mixer.core.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.somewhatcity.mixer.core.MixerPlugin;
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
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
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

    public static String requestCobaltMediaUrl(String url) {
        try {
            JsonObject send = new JsonObject();
            send.addProperty("url", url);

            send.addProperty("downloadMode", "audio");
            send.addProperty("isAudioOnly", true);
            send.addProperty("audioFormat", "mp3");
            send.addProperty("aFormat", "mp3");

            RequestBody body = RequestBody.create(send.toString().getBytes(StandardCharsets.UTF_8));

            Request requestV7 = new Request.Builder()
                    .url("https://api.cobalt.tools/")
                    .addHeader("Accept", "application/json")
                    .addHeader("Content-Type", "application/json")
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .post(body)
                    .build();

            try (Response response = client.newCall(requestV7).execute()) {
                String res = response.body() != null ? response.body().string() : "";
                if (response.isSuccessful()) {
                    JsonObject json = (JsonObject) JsonParser.parseString(res);
                    if (json.has("url")) {
                        return json.get("url").getAsString();
                    } else {
                        MixerPlugin.getPlugin().logDebug(Level.WARNING, "Cobalt API answered correctly, but without a URL: " + res, null);
                    }
                } else {
                    MixerPlugin.getPlugin().logDebug(Level.WARNING, "Cobalt API Error (v7): HTTP " + response.code() + " - " + res, null);
                }
            }
        } catch (Exception e) {
            MixerPlugin.getPlugin().logDebug(Level.WARNING, "Error connecting to Cobalt API v7", e);
        }

        try {
            JsonObject send = new JsonObject();
            send.addProperty("url", url);
            send.addProperty("isAudioOnly", true);

            RequestBody body = RequestBody.create(send.toString().getBytes(StandardCharsets.UTF_8));

            Request requestV6 = new Request.Builder()
                    .url("https://api.cobalt.tools/api/json")
                    .addHeader("Accept", "application/json")
                    .addHeader("Content-Type", "application/json")
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .post(body)
                    .build();

            try (Response response = client.newCall(requestV6).execute()) {
                String res = response.body() != null ? response.body().string() : "";
                if (response.isSuccessful()) {
                    JsonObject json = (JsonObject) JsonParser.parseString(res);
                    if (json.has("url")) {
                        return json.get("url").getAsString();
                    }
                } else {
                    MixerPlugin.getPlugin().logDebug(Level.WARNING, "Cobalt API Error (v6 fallback): HTTP " + response.code() + " - " + res, null);
                }
            }
        } catch (Exception e) {
            MixerPlugin.getPlugin().logDebug(Level.WARNING, "Error connectiong to Cobalt API v6", e);
        }

        return null;
    }
}