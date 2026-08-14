package me.andromedov.mixer.core.api;

import me.andromedov.mixer.api.gui.PortableSpeakerMenuContext;
import me.andromedov.mixer.api.gui.PortableSpeakerMenuElement;
import me.andromedov.mixer.api.gui.PortableSpeakerMenuProvider;
import me.andromedov.mixer.api.gui.PortableSpeakerMenuProviderRegistration;
import me.andromedov.mixer.api.gui.PortableSpeakerMenuRegistry;
import me.andromedov.mixer.core.MixerPlugin;
import me.andromedov.mixer.core.util.MixerScheduler;
import net.kyori.adventure.text.Component;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;

final class ImplPortableSpeakerMenuRegistry implements PortableSpeakerMenuRegistry {
    private final MixerPlugin plugin;
    private final PrioritizedProviderRegistry<PortableSpeakerMenuProvider> providers =
            new PrioritizedProviderRegistry<>("Portable speaker menu");

    ImplPortableSpeakerMenuRegistry(MixerPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public PortableSpeakerMenuProviderRegistration register(Plugin owner,
                                                            PortableSpeakerMenuProvider provider) {
        Objects.requireNonNull(provider, "provider");
        MixerScheduler.requireGlobalThread("register a portable speaker menu provider");
        return new Registration(providers.register(owner, provider.id(), provider));
    }

    @Override
    public Collection<PortableSpeakerMenuProviderRegistration> registrations() {
        return providers.entries().stream().map(Registration::new).map(r ->
                (PortableSpeakerMenuProviderRegistration) r).toList();
    }

    @Override
    public ItemStack renderItem(PortableSpeakerMenuElement element,
                                PortableSpeakerMenuContext context, ItemStack defaultItem) {
        Objects.requireNonNull(element, "element");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(defaultItem, "defaultItem");
        MixerScheduler.requireOwned(context.player(), "render a portable speaker menu item");

        ItemStack rendered = defaultItem.clone();
        for (var registration : providers.ordered(PortableSpeakerMenuProvider::priority)) {
            try {
                ItemStack candidate = registration.provider().customizeItem(
                        element, context, rendered.clone());
                if (candidate != null && !candidate.getType().isAir()) rendered = candidate.clone();
            } catch (Exception exception) {
                plugin.logDebug(Level.WARNING,
                        "Portable speaker menu provider failed: " + registration.key(), exception);
            }
        }
        return rendered;
    }

    @Override
    public Component renderTitle(PortableSpeakerMenuContext context, Component defaultTitle) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(defaultTitle, "defaultTitle");
        MixerScheduler.requireOwned(context.player(), "render a portable speaker menu title");

        Component rendered = defaultTitle;
        for (var registration : providers.ordered(PortableSpeakerMenuProvider::priority)) {
            try {
                Component candidate = registration.provider().customizeTitle(context, rendered);
                if (candidate != null) rendered = candidate;
            } catch (Exception exception) {
                plugin.logDebug(Level.WARNING,
                        "Portable speaker menu provider failed: " + registration.key(), exception);
            }
        }
        return rendered;
    }

    void unregisterOwnedBy(Plugin owner) { providers.unregisterOwnedBy(owner); }
    void shutdown() { providers.shutdown(); }

    private record Registration(PrioritizedProviderRegistry<PortableSpeakerMenuProvider>.Entry entry)
            implements PortableSpeakerMenuProviderRegistration {
        @Override public Plugin owner() { return entry.owner(); }
        @Override public PortableSpeakerMenuProvider provider() { return entry.provider(); }
        @Override public boolean active() { return entry.active(); }
        @Override public void close() { entry.close(); }
    }
}
