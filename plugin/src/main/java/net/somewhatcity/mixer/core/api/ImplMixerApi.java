package net.somewhatcity.mixer.core.api;

import net.somewhatcity.mixer.api.MixerApi;
import net.somewhatcity.mixer.api.MixerAudioPlayer;
import net.somewhatcity.mixer.core.MixerPlugin;
import net.somewhatcity.mixer.core.audio.IMixerAudioPlayer;
import org.bukkit.Location;

import java.util.HashMap;

public class ImplMixerApi implements MixerApi {
    private MixerPlugin plugin;
    public ImplMixerApi(MixerPlugin plugin) {
        this.plugin = plugin;
    }
    @Override
    public IMixerAudioPlayer createPlayer(Location location) {
        if(plugin.playerHashMap().containsKey(location)) throw new IllegalStateException("player at this location already exists");
        return new IMixerAudioPlayer(location);
    }

    @Override
    public IMixerAudioPlayer getMixerAudioPlayer(Location location) {
        return plugin.playerHashMap().get(location);
    }
}