package me.andromedov.mixer.core.api;

import me.andromedov.mixer.core.MixerPlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.Collection;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.logging.Level;

/** Applies menu providers consistently while isolating failures and defensive copies. */
final class MenuProviderPipeline<P> {
    private final MixerPlugin plugin;
    private final String label;
    private final ToIntFunction<P> priority;
    private final PrioritizedProviderRegistry<P> providers;

    MenuProviderPipeline(MixerPlugin plugin, String label, ToIntFunction<P> priority) {
        this.plugin = plugin;
        this.label = label;
        this.priority = priority;
        this.providers = new PrioritizedProviderRegistry<>(label);
    }

    PrioritizedProviderRegistry<P>.Entry register(Plugin owner, String id, P provider) {
        return providers.register(owner, id, provider);
    }

    <R> Collection<R> registrations(
            Function<PrioritizedProviderRegistry<P>.Entry, R> mapper) {
        return providers.registrations(mapper);
    }

    ItemStack renderItem(ItemStack defaultItem, BiFunction<P, ItemStack, ItemStack> customizer) {
        ItemStack rendered = defaultItem.clone();
        for (var registration : providers.ordered(priority)) {
            try {
                ItemStack candidate = customizer.apply(registration.provider(), rendered.clone());
                if (candidate != null && !candidate.getType().isAir()) rendered = candidate.clone();
            } catch (Exception exception) {
                logFailure(registration.key(), exception);
            }
        }
        return rendered;
    }

    Component renderTitle(Component defaultTitle,
                          BiFunction<P, Component, Component> customizer) {
        Component rendered = defaultTitle;
        for (var registration : providers.ordered(priority)) {
            try {
                Component candidate = customizer.apply(registration.provider(), rendered);
                if (candidate != null) rendered = candidate;
            } catch (Exception exception) {
                logFailure(registration.key(), exception);
            }
        }
        return rendered;
    }

    void unregisterOwnedBy(Plugin owner) {
        providers.unregisterOwnedBy(owner);
    }

    void shutdown() {
        providers.shutdown();
    }

    private void logFailure(String key, Exception exception) {
        plugin.logDebug(Level.WARNING, label + " provider failed: " + key, exception);
    }
}
