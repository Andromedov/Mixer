package me.andromedov.mixer.core.api;

import me.andromedov.mixer.api.MixerApi;
import me.andromedov.mixer.core.MixerPlugin;
import me.andromedov.mixer.core.audio.IMixerAudioPlayer;
import org.bukkit.Location;

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