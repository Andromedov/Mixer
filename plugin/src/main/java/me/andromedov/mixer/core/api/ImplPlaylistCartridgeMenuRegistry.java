package me.andromedov.mixer.core.api;

import me.andromedov.mixer.api.gui.PlaylistCartridgeMenuContext;
import me.andromedov.mixer.api.gui.PlaylistCartridgeMenuItemContext;
import me.andromedov.mixer.api.gui.PlaylistCartridgeMenuProvider;
import me.andromedov.mixer.api.gui.PlaylistCartridgeMenuProviderRegistration;
import me.andromedov.mixer.api.gui.PlaylistCartridgeMenuRegistry;
import me.andromedov.mixer.core.MixerPlugin;
import me.andromedov.mixer.core.util.MixerScheduler;
import net.kyori.adventure.text.Component;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.Collection;
import java.util.Objects;
import java.util.logging.Level;

final class ImplPlaylistCartridgeMenuRegistry implements PlaylistCartridgeMenuRegistry {
    private final MixerPlugin plugin;
    private final PrioritizedProviderRegistry<PlaylistCartridgeMenuProvider> providers =
            new PrioritizedProviderRegistry<>("Playlist cartridge menu");

    ImplPlaylistCartridgeMenuRegistry(MixerPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public PlaylistCartridgeMenuProviderRegistration register(
            Plugin owner, PlaylistCartridgeMenuProvider provider) {
        Objects.requireNonNull(provider, "provider");
        MixerScheduler.requireGlobalThread("register a playlist cartridge menu provider");
        return new Registration(providers.register(owner, provider.id(), provider));
    }

    @Override
    public Collection<PlaylistCartridgeMenuProviderRegistration> registrations() {
        return providers.entries().stream().map(Registration::new).map(r ->
                (PlaylistCartridgeMenuProviderRegistration) r).toList();
    }

    @Override
    public ItemStack renderItem(PlaylistCartridgeMenuItemContext context, ItemStack defaultItem) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(defaultItem, "defaultItem");
        MixerScheduler.requireOwned(context.menu().player(), "render a playlist cartridge menu item");
        ItemStack rendered = defaultItem.clone();
        for (var registration : providers.ordered(PlaylistCartridgeMenuProvider::priority)) {
            try {
                ItemStack candidate = registration.provider().customizeItem(context, rendered.clone());
                if (candidate != null && !candidate.getType().isAir()) rendered = candidate.clone();
            } catch (Exception exception) {
                plugin.logDebug(Level.WARNING,
                        "Playlist cartridge menu provider failed: " + registration.key(), exception);
            }
        }
        return rendered;
    }

    @Override
    public Component renderTitle(PlaylistCartridgeMenuContext context, Component defaultTitle) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(defaultTitle, "defaultTitle");
        MixerScheduler.requireOwned(context.player(), "render a playlist cartridge menu title");
        Component rendered = defaultTitle;
        for (var registration : providers.ordered(PlaylistCartridgeMenuProvider::priority)) {
            try {
                Component candidate = registration.provider().customizeTitle(context, rendered);
                if (candidate != null) rendered = candidate;
            } catch (Exception exception) {
                plugin.logDebug(Level.WARNING,
                        "Playlist cartridge menu provider failed: " + registration.key(), exception);
            }
        }
        return rendered;
    }

    void unregisterOwnedBy(Plugin owner) { providers.unregisterOwnedBy(owner); }
    void shutdown() { providers.shutdown(); }

    private record Registration(PrioritizedProviderRegistry<PlaylistCartridgeMenuProvider>.Entry entry)
            implements PlaylistCartridgeMenuProviderRegistration {
        @Override public Plugin owner() { return entry.owner(); }
        @Override public PlaylistCartridgeMenuProvider provider() { return entry.provider(); }
        @Override public boolean active() { return entry.active(); }
        @Override public void close() { entry.close(); }
    }
}
