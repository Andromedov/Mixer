package net.somewhatcity.mixer.api;

import org.bukkit.Location;

public interface MixerApi {
    MixerAudioPlayer createPlayer(Location location);
    MixerAudioPlayer getMixerAudioPlayer(Location location);
}