package me.andromedov.mixer.api;

import me.andromedov.mixer.api.dsp.Flanger;
import me.andromedov.mixer.api.dsp.HighPass;
import me.andromedov.mixer.api.dsp.LowPass;

import java.util.Optional;

public interface MixerDsp {
    double gain();
    void setGain(double gain);

    Optional<HighPass> highPass();
    void setHighPass(float frequency);
    void clearHighPass();

    Optional<LowPass> lowPass();
    void setLowPass(float frequency);
    void clearLowPass();

    Optional<Flanger> flanger();
    void setFlanger(double maxFlangerLength, double wet, double lfoFrequency);
    void clearFlanger();

    void reset();
}
