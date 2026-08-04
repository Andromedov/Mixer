package me.andromedov.mixer.api.addon;

import org.bukkit.plugin.Plugin;

public interface MixerAddonRegistration extends AutoCloseable {
    Plugin owner();
    MixerAddon addon();
    boolean active();

    @Override
    void close();
}
