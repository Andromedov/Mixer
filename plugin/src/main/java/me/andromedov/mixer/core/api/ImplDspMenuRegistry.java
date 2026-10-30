package me.andromedov.mixer.core.api;

import me.andromedov.mixer.api.gui.DspMenuContext;
import me.andromedov.mixer.api.gui.DspMenuItemContext;
import me.andromedov.mixer.api.gui.DspMenuProvider;
import me.andromedov.mixer.api.gui.DspMenuProviderRegistration;
import me.andromedov.mixer.api.gui.DspMenuRegistry;
import me.andromedov.mixer.core.MixerPlugin;
import me.andromedov.mixer.core.util.MixerScheduler;
import net.kyori.adventure.text.Component;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.Collection;
import java.util.Objects;

final class ImplDspMenuRegistry implements DspMenuRegistry {
    private final MenuProviderPipeline<DspMenuProvider> providers;

    ImplDspMenuRegistry(MixerPlugin plugin) {
        this.providers = new MenuProviderPipeline<>(plugin, "DSP menu", DspMenuProvider::priority);
    }

    @Override
    public DspMenuProviderRegistration register(Plugin owner, DspMenuProvider provider) {
        Objects.requireNonNull(provider, "provider");
        MixerScheduler.requireGlobalThread("register a DSP menu provider");
        return new Registration(providers.register(owner, provider.id(), provider));
    }

    @Override
    public Collection<DspMenuProviderRegistration> registrations() {
        return providers.registrations(Registration::new);
    }

    @Override
    public ItemStack renderItem(DspMenuItemContext context, ItemStack defaultItem) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(defaultItem, "defaultItem");
        MixerScheduler.requireOwned(context.menu().player(), "render a DSP menu item");
        return providers.renderItem(defaultItem,
                (provider, current) -> provider.customizeItem(context, current));
    }

    @Override
    public Component renderTitle(DspMenuContext context, Component defaultTitle) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(defaultTitle, "defaultTitle");
        MixerScheduler.requireOwned(context.player(), "render a DSP menu title");
        return providers.renderTitle(defaultTitle,
                (provider, current) -> provider.customizeTitle(context, current));
    }

    void unregisterOwnedBy(Plugin owner) { providers.unregisterOwnedBy(owner); }
    void shutdown() { providers.shutdown(); }

    private record Registration(PrioritizedProviderRegistry<DspMenuProvider>.Entry entry)
            implements DspMenuProviderRegistration {
        @Override public Plugin owner() { return entry.owner(); }
        @Override public DspMenuProvider provider() { return entry.provider(); }
        @Override public boolean active() { return entry.active(); }
        @Override public void close() { entry.close(); }
    }
}
