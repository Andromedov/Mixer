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
import java.util.Objects;

final class ImplPortableSpeakerMenuRegistry implements PortableSpeakerMenuRegistry {
    private final MenuProviderPipeline<PortableSpeakerMenuProvider> providers;

    ImplPortableSpeakerMenuRegistry(MixerPlugin plugin) {
        this.providers = new MenuProviderPipeline<>(
                plugin, "Portable speaker menu", PortableSpeakerMenuProvider::priority);
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
        return providers.registrations(Registration::new);
    }

    @Override
    public ItemStack renderItem(PortableSpeakerMenuElement element,
                                PortableSpeakerMenuContext context, ItemStack defaultItem) {
        Objects.requireNonNull(element, "element");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(defaultItem, "defaultItem");
        MixerScheduler.requireOwned(context.player(), "render a portable speaker menu item");

        return providers.renderItem(defaultItem,
                (provider, current) -> provider.customizeItem(element, context, current));
    }

    @Override
    public Component renderTitle(PortableSpeakerMenuContext context, Component defaultTitle) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(defaultTitle, "defaultTitle");
        MixerScheduler.requireOwned(context.player(), "render a portable speaker menu title");

        return providers.renderTitle(defaultTitle,
                (provider, current) -> provider.customizeTitle(context, current));
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
