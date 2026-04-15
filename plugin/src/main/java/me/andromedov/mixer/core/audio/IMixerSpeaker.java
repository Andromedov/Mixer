package me.andromedov.mixer.core.audio;

import me.andromedov.mixer.api.MixerSpeaker;
import org.bukkit.Location;

public class IMixerSpeaker implements MixerSpeaker {

    private Location location;
    public IMixerSpeaker(Location location) {
        this.location = location;
    }

    @Override
    public Location location() {
        return location;
    }
}