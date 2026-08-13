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
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

final class ImplPlaylistCartridgeMenuRegistry implements PlaylistCartridgeMenuRegistry {
    private final MixerPlugin plugin;
    private final ConcurrentMap<String, Registration> registrations = new ConcurrentHashMap<>();

    ImplPlaylistCartridgeMenuRegistry(MixerPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public PlaylistCartridgeMenuProviderRegistration register(
            Plugin owner, PlaylistCartridgeMenuProvider provider) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(provider, "provider");
        MixerScheduler.requireGlobalThread("register a playlist cartridge menu provider");
        String providerId = validateId(provider.id());
        String key = owner.getName().toLowerCase(Locale.ROOT) + ":" + providerId;
        Registration registration = new Registration(key, owner, provider);
        if (registrations.putIfAbsent(key, registration) != null) {
            throw new IllegalStateException("Playlist cartridge menu provider already registered: " + key);
        }
        return registration;
    }

    @Override
    public Collection<PlaylistCartridgeMenuProviderRegistration> registrations() {
        return List.copyOf(registrations.values());
    }

    @Override
    public ItemStack renderItem(PlaylistCartridgeMenuItemContext context, ItemStack defaultItem) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(defaultItem, "defaultItem");
        MixerScheduler.requireOwned(context.menu().player(), "render a playlist cartridge menu item");
        ItemStack rendered = defaultItem.clone();
        for (Registration registration : orderedRegistrations()) {
            try {
                ItemStack candidate = registration.provider().customizeItem(context, rendered.clone());
                if (candidate != null && !candidate.getType().isAir()) rendered = candidate.clone();
            } catch (Exception exception) {
                plugin.logDebug(Level.WARNING,
                        "Playlist cartridge menu provider failed: " + registration.key, exception);
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
        for (Registration registration : orderedRegistrations()) {
            try {
                Component candidate = registration.provider().customizeTitle(context, rendered);
                if (candidate != null) rendered = candidate;
            } catch (Exception exception) {
                plugin.logDebug(Level.WARNING,
                        "Playlist cartridge menu provider failed: " + registration.key, exception);
            }
        }
        return rendered;
    }

    void unregisterOwnedBy(Plugin owner) {
        registrations.values().stream()
                .filter(registration -> registration.owner().equals(owner))
                .toList().forEach(Registration::close);
    }

    void shutdown() {
        List.copyOf(registrations.values()).forEach(Registration::close);
    }

    private List<Registration> orderedRegistrations() {
        return registrations.values().stream()
                .filter(Registration::active)
                .sorted(Comparator.comparingInt((Registration registration) ->
                                registration.provider().priority())
                        .thenComparing(registration -> registration.key))
                .toList();
    }

    private static String validateId(String id) {
        String normalized = Objects.requireNonNull(id, "provider id").toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9][a-z0-9._-]{0,63}")) {
            throw new IllegalArgumentException(
                    "provider id must match [a-z0-9][a-z0-9._-]{0,63}: " + id);
        }
        return normalized;
    }

    private final class Registration implements PlaylistCartridgeMenuProviderRegistration {
        private final String key;
        private final Plugin owner;
        private final PlaylistCartridgeMenuProvider provider;
        private final AtomicBoolean active = new AtomicBoolean(true);

        private Registration(String key, Plugin owner, PlaylistCartridgeMenuProvider provider) {
            this.key = key;
            this.owner = owner;
            this.provider = provider;
        }

        @Override public Plugin owner() { return owner; }
        @Override public PlaylistCartridgeMenuProvider provider() { return provider; }
        @Override public boolean active() { return active.get(); }

        @Override
        public void close() {
            if (active.compareAndSet(true, false)) registrations.remove(key, this);
        }
    }
}
