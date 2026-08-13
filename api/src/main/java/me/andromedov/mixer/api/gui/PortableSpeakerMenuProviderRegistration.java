package me.andromedov.mixer.api.gui;

import org.bukkit.plugin.Plugin;

public interface PortableSpeakerMenuProviderRegistration extends AutoCloseable {
    Plugin owner();
    PortableSpeakerMenuProvider provider();
    boolean active();

    @Override
    void close();
}
