package me.andromedov.mixer.api.gui;

import org.bukkit.plugin.Plugin;

public interface PlaylistCartridgeMenuProviderRegistration extends AutoCloseable {
    Plugin owner();
    PlaylistCartridgeMenuProvider provider();
    boolean active();

    @Override
    void close();
}
