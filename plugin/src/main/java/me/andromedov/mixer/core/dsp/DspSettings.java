package me.andromedov.mixer.core.dsp;

import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

/** Owns the persisted DSP schema and value constraints independently of the GUI. */
public final class DspSettings {
    public static final double DEFAULT_GAIN = 1.0;
    public static final float DEFAULT_HIGH_PASS = 0;
    public static final float DEFAULT_LOW_PASS = 20_000;

    private JsonObject data;

    private DspSettings(JsonObject data) {
        this.data = data;
    }

    public static DspSettings fromJson(@Nullable JsonObject data) {
        return new DspSettings(data == null ? new JsonObject() : data.deepCopy());
    }

    public double gain() {
        return nestedDouble("gain", "gain", DEFAULT_GAIN);
    }

    public float highPassFrequency() {
        return nestedFloat("highPassFilter", "frequency", DEFAULT_HIGH_PASS);
    }

    public float lowPassFrequency() {
        return nestedFloat("lowPassFilter", "frequency", DEFAULT_LOW_PASS);
    }

    public boolean flangerEnabled() {
        return data.has("flangerEffect") && data.get("flangerEffect").isJsonObject();
    }

    public void adjustGain(double delta) {
        JsonObject gain = object("gain");
        gain.addProperty("gain", clamp(gain() + delta, 0.0, 3.0));
        data.add("gain", gain);
    }

    public void adjustHighPass(float delta) {
        float frequency = clamp(highPassFrequency() + delta, 0, 5_000);
        if (frequency <= 0) {
            data.remove("highPassFilter");
            return;
        }
        JsonObject filter = object("highPassFilter");
        filter.addProperty("frequency", frequency);
        data.add("highPassFilter", filter);
    }

    public void adjustLowPass(float delta) {
        float frequency = clamp(lowPassFrequency() + delta, 500, DEFAULT_LOW_PASS);
        if (frequency >= DEFAULT_LOW_PASS) {
            data.remove("lowPassFilter");
            return;
        }
        JsonObject filter = object("lowPassFilter");
        filter.addProperty("frequency", frequency);
        data.add("lowPassFilter", filter);
    }

    public void toggleFlanger() {
        if (flangerEnabled()) {
            data.remove("flangerEffect");
            return;
        }
        JsonObject flanger = new JsonObject();
        flanger.addProperty("maxFlangerLength", 0.01);
        flanger.addProperty("wet", 0.5);
        flanger.addProperty("lfoFrequency", 0.2);
        data.add("flangerEffect", flanger);
    }

    public void reset() {
        data = new JsonObject();
    }

    public JsonObject toJson() {
        return data.deepCopy();
    }

    private JsonObject object(String key) {
        return data.has(key) && data.get(key).isJsonObject()
                ? data.getAsJsonObject(key) : new JsonObject();
    }

    private double nestedDouble(String objectKey, String valueKey, double fallback) {
        try {
            JsonObject object = object(objectKey);
            return object.has(valueKey) ? object.get(valueKey).getAsDouble() : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private float nestedFloat(String objectKey, String valueKey, float fallback) {
        try {
            JsonObject object = object(objectKey);
            return object.has(valueKey) ? object.get(valueKey).getAsFloat() : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
