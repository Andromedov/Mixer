package net.somewhatcity.mixer.core.audio;

import org.bukkit.Location;

public class RedstonePoint {

    private Location location;
    private int magnitude;
    private int trigger;
    private int delay;

    public RedstonePoint(Location location, int magnitude, int trigger, int delay) {
        this.location = location;
        this.magnitude = magnitude;
        this.trigger = trigger;
        this.delay = delay;
    }

    public Location getLocation() {
        return location;
    }

    public int getMagnitude() {
        return magnitude;
    }

    public int getTrigger() {
        return trigger;
    }
    public int getDelay() {
        return delay;
    }
}
