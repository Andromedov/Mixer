package me.andromedov.mixer.api.gui;

import org.bukkit.plugin.Plugin;

public interface DspMenuProviderRegistration extends AutoCloseable {
    Plugin owner();
    DspMenuProvider provider();
    boolean active();

    @Override
    void close();
}
