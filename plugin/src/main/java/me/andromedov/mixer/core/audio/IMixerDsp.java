package me.andromedov.mixer.core.audio;

import com.google.gson.JsonObject;
import me.andromedov.mixer.api.MixerDsp;
import me.andromedov.mixer.api.dsp.Flanger;
import me.andromedov.mixer.api.dsp.HighPass;
import me.andromedov.mixer.api.dsp.LowPass;
import org.bukkit.Bukkit;

import java.util.Optional;

public final class IMixerDsp implements MixerDsp {
    private final AbstractMixerAudioPlayer player;

    public IMixerDsp(AbstractMixerAudioPlayer player) {
        this.player = player;
    }

    @Override
    public double gain() {
        JsonObject gain = settings().getAsJsonObject("gain");
        return gain != null && gain.has("gain") ? gain.get("gain").getAsDouble() : 1.0;
    }

    @Override
    public void setGain(double gain) {
        requireFiniteRange(gain, 0.0, 10.0, "gain");
        mutate(settings -> {
            JsonObject value = new JsonObject();
            value.addProperty("gain", gain);
            settings.add("gain", value);
        });
    }

    @Override
    public Optional<HighPass> highPass() {
        JsonObject value = settings().getAsJsonObject("highPassFilter");
        if (value == null || !value.has("frequency")) return Optional.empty();
        float frequency = value.get("frequency").getAsFloat();
        return Optional.of(() -> frequency);
    }

    @Override
    public void setHighPass(float frequency) {
        validateFrequency(frequency);
        mutate(settings -> {
            JsonObject value = new JsonObject();
            value.addProperty("frequency", frequency);
            settings.add("highPassFilter", value);
        });
    }

    @Override
    public void clearHighPass() {
        mutate(settings -> settings.remove("highPassFilter"));
    }

    @Override
    public Optional<LowPass> lowPass() {
        JsonObject value = settings().getAsJsonObject("lowPassFilter");
        if (value == null || !value.has("frequency")) return Optional.empty();
        float frequency = value.get("frequency").getAsFloat();
        return Optional.of(() -> frequency);
    }

    @Override
    public void setLowPass(float frequency) {
        validateFrequency(frequency);
        mutate(settings -> {
            JsonObject value = new JsonObject();
            value.addProperty("frequency", frequency);
            settings.add("lowPassFilter", value);
        });
    }

    @Override
    public void clearLowPass() {
        mutate(settings -> settings.remove("lowPassFilter"));
    }

    @Override
    public Optional<Flanger> flanger() {
        JsonObject value = settings().getAsJsonObject("flangerEffect");
        if (value == null || !value.has("maxFlangerLength") || !value.has("wet") || !value.has("lfoFrequency")) {
            return Optional.empty();
        }
        return Optional.of(new FlangerSettings(
                value.get("maxFlangerLength").getAsDouble(),
                value.get("wet").getAsDouble(),
                value.get("lfoFrequency").getAsDouble()
        ));
    }

    @Override
    public void setFlanger(double maxFlangerLength, double wet, double lfoFrequency) {
        requireFiniteRange(maxFlangerLength, 0.0001, 10.0, "maxFlangerLength");
        requireFiniteRange(wet, 0.0, 1.0, "wet");
        requireFiniteRange(lfoFrequency, 0.0001, 1000.0, "lfoFrequency");
        mutate(settings -> {
            JsonObject value = new JsonObject();
            value.addProperty("maxFlangerLength", maxFlangerLength);
            value.addProperty("wet", wet);
            value.addProperty("lfoFrequency", lfoFrequency);
            settings.add("flangerEffect", value);
        });
    }

    @Override
    public void clearFlanger() {
        mutate(settings -> settings.remove("flangerEffect"));
    }

    @Override
    public void reset() {
        requireMainThread();
        player.applyDspSettingsFromApi(new JsonObject());
    }

    private JsonObject settings() {
        return player.getDspSettings();
    }

    private void mutate(java.util.function.Consumer<JsonObject> mutation) {
        requireMainThread();
        JsonObject copy = settings().deepCopy();
        mutation.accept(copy);
        player.applyDspSettingsFromApi(copy);
    }

    private void validateFrequency(float frequency) {
        requireFiniteRange(frequency, 1.0, player.audioFormat.getSampleRate() / 2.0, "frequency");
    }

    private static void requireFiniteRange(double value, double minimum, double maximum, String name) {
        if (!Double.isFinite(value) || value < minimum || value > maximum) {
            throw new IllegalArgumentException(name + " must be between " + minimum + " and " + maximum);
        }
    }

    private static void requireMainThread() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("DSP settings must be changed on the Bukkit main thread");
        }
    }

    private record FlangerSettings(double maxFlangerLength, double wet, double lfoFrequency) implements Flanger { }
}
