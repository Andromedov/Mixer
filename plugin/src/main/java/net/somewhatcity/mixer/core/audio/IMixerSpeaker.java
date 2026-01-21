package net.somewhatcity.mixer.core.audio;

import net.somewhatcity.mixer.api.MixerSpeaker;
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