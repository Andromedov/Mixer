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

final class ImplPlaylistCartridgeMenuRegistry implements PlaylistCartridgeMenuRegistry {
    private final MenuProviderPipeline<PlaylistCartridgeMenuProvider> providers;

    ImplPlaylistCartridgeMenuRegistry(MixerPlugin plugin) {
        this.providers = new MenuProviderPipeline<>(
                plugin, "Playlist cartridge menu", PlaylistCartridgeMenuProvider::priority);
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
        return providers.registrations(Registration::new);
    }

    @Override
    public ItemStack renderItem(PlaylistCartridgeMenuItemContext context, ItemStack defaultItem) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(defaultItem, "defaultItem");
        MixerScheduler.requireOwned(context.menu().player(), "render a playlist cartridge menu item");
        return providers.renderItem(defaultItem,
                (provider, current) -> provider.customizeItem(context, current));
    }

    @Override
    public Component renderTitle(PlaylistCartridgeMenuContext context, Component defaultTitle) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(defaultTitle, "defaultTitle");
        MixerScheduler.requireOwned(context.player(), "render a playlist cartridge menu title");
        return providers.renderTitle(defaultTitle,
                (provider, current) -> provider.customizeTitle(context, current));
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
