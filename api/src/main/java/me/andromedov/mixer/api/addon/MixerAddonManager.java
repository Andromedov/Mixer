package me.andromedov.mixer.api.addon;

import org.bukkit.plugin.Plugin;

import java.util.Collection;
import java.util.Optional;

public interface MixerAddonManager {
    MixerAddonRegistration register(Plugin owner, MixerAddon addon);
    boolean unregister(String addonId);
    Optional<MixerAddonRegistration> find(String addonId);
    Collection<MixerAddonRegistration> registrations();
}
