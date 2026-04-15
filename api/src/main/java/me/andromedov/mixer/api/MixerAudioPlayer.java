package me.andromedov.mixer.api;

import org.bukkit.Location;

import java.util.Set;

public interface MixerAudioPlayer {
    Location location();
    Set<MixerSpeaker> speakers();
    MixerDsp dsp();
    void load(String... url);
    void stop();
}