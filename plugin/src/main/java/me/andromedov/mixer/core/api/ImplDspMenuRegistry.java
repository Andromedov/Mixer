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
import java.util.logging.Level;

final class ImplDspMenuRegistry implements DspMenuRegistry {
    private final MixerPlugin plugin;
    private final PrioritizedProviderRegistry<DspMenuProvider> providers =
            new PrioritizedProviderRegistry<>("DSP menu");

    ImplDspMenuRegistry(MixerPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public DspMenuProviderRegistration register(Plugin owner, DspMenuProvider provider) {
        Objects.requireNonNull(provider, "provider");
        MixerScheduler.requireGlobalThread("register a DSP menu provider");
        return new Registration(providers.register(owner, provider.id(), provider));
    }

    @Override
    public Collection<DspMenuProviderRegistration> registrations() {
        return providers.entries().stream().map(Registration::new).map(r ->
                (DspMenuProviderRegistration) r).toList();
    }

    @Override
    public ItemStack renderItem(DspMenuItemContext context, ItemStack defaultItem) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(defaultItem, "defaultItem");
        MixerScheduler.requireOwned(context.menu().player(), "render a DSP menu item");
        ItemStack rendered = defaultItem.clone();
        for (var registration : providers.ordered(DspMenuProvider::priority)) {
            try {
                ItemStack candidate = registration.provider().customizeItem(context, rendered.clone());
                if (candidate != null && !candidate.getType().isAir()) rendered = candidate.clone();
            } catch (Exception exception) {
                plugin.logDebug(Level.WARNING,
                        "DSP menu provider failed: " + registration.key(), exception);
            }
        }
        return rendered;
    }

    @Override
    public Component renderTitle(DspMenuContext context, Component defaultTitle) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(defaultTitle, "defaultTitle");
        MixerScheduler.requireOwned(context.player(), "render a DSP menu title");
        Component rendered = defaultTitle;
        for (var registration : providers.ordered(DspMenuProvider::priority)) {
            try {
                Component candidate = registration.provider().customizeTitle(context, rendered);
                if (candidate != null) rendered = candidate;
            } catch (Exception exception) {
                plugin.logDebug(Level.WARNING,
                        "DSP menu provider failed: " + registration.key(), exception);
            }
        }
        return rendered;
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
