package me.andromedov.mixer.core.dsp;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DspSettingsTest {
    @Test
    void usesDefaultsForMissingAndMalformedValues() {
        JsonObject json = new JsonObject();
        json.addProperty("gain", "invalid");

        DspSettings settings = DspSettings.fromJson(json);

        assertEquals(DspSettings.DEFAULT_GAIN, settings.gain());
        assertEquals(DspSettings.DEFAULT_HIGH_PASS, settings.highPassFrequency());
        assertEquals(DspSettings.DEFAULT_LOW_PASS, settings.lowPassFrequency());
        assertFalse(settings.flangerEnabled());
    }

    @Test
    void clampsGainAndRemovesDisabledFilters() {
        DspSettings settings = DspSettings.fromJson(null);

        settings.adjustGain(10);
        settings.adjustHighPass(100);
        settings.adjustHighPass(-100);
        settings.adjustLowPass(-500);
        settings.adjustLowPass(500);

        assertEquals(3.0, settings.gain());
        assertFalse(settings.toJson().has("highPassFilter"));
        assertFalse(settings.toJson().has("lowPassFilter"));
    }

    @Test
    void togglesFlangerAndResetClearsAllSettings() {
        DspSettings settings = DspSettings.fromJson(null);

        settings.toggleFlanger();
        assertTrue(settings.flangerEnabled());
        settings.reset();

        assertFalse(settings.flangerEnabled());
        assertEquals(DspSettings.DEFAULT_GAIN, settings.gain());
        assertEquals(0, settings.toJson().size());
    }

    @Test
    void doesNotMutateInputOrExposeInternalJson() {
        JsonObject source = new JsonObject();
        source.addProperty("custom", true);
        DspSettings settings = DspSettings.fromJson(source);

        settings.toJson().remove("custom");
        settings.adjustGain(0.1);

        assertTrue(source.has("custom"));
        assertFalse(source.has("gain"));
        assertTrue(settings.toJson().has("custom"));
    }
}
