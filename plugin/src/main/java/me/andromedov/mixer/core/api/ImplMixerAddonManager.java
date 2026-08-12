package me.andromedov.mixer.core.api;

import me.andromedov.mixer.api.MixerApi;
import me.andromedov.mixer.api.addon.MixerAddon;
import me.andromedov.mixer.api.addon.MixerAddonContext;
import me.andromedov.mixer.api.addon.MixerAddonManager;
import me.andromedov.mixer.api.addon.MixerAddonRegistration;
import me.andromedov.mixer.api.source.MixerAudioSourceResolver;
import me.andromedov.mixer.api.source.MixerAudioSourceResolverRegistration;
import me.andromedov.mixer.api.playback.MixerPlaybackPolicy;
import me.andromedov.mixer.api.playback.MixerPlaybackPolicyRegistration;
import me.andromedov.mixer.core.MixerPlugin;
import me.andromedov.mixer.core.util.MixerScheduler;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

final class ImplMixerAddonManager implements MixerAddonManager, Listener {
    private final MixerPlugin plugin;
    private final MixerApi api;
    private final ImplMixerAudioSourceRegistry sources;
    private final ImplMixerPlaybackPolicyRegistry playbackPolicies;
    private final ConcurrentMap<String, Registration> registrations = new ConcurrentHashMap<>();

    ImplMixerAddonManager(MixerPlugin plugin, MixerApi api, ImplMixerAudioSourceRegistry sources,
                          ImplMixerPlaybackPolicyRegistry playbackPolicies) {
        this.plugin = plugin;
        this.api = api;
        this.sources = sources;
        this.playbackPolicies = playbackPolicies;
    }

    @Override
    public MixerAddonRegistration register(Plugin owner, MixerAddon addon) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(addon, "addon");
        requireMainThread("register a Mixer addon");

        String id = validateAddonId(addon.id());
        Registration registration = new Registration(id, owner, addon);
        if (registrations.putIfAbsent(id, registration) != null) {
            throw new IllegalStateException("Mixer addon already registered: " + id);
        }

        try {
            addon.onEnable(new Context(registration));
            plugin.getLogger().info("Enabled Mixer addon " + addon.name() + " " + addon.version() + " (" + id + ")");
            return registration;
        } catch (Exception exception) {
            registration.closeResources();
            registration.active.set(false);
            registrations.remove(id, registration);
            throw new IllegalStateException("Failed to enable Mixer addon " + id, exception);
        }
    }

    @Override
    public boolean unregister(String addonId) {
        requireMainThread("unregister a Mixer addon");
        Registration registration = registrations.get(normalizeAddonId(addonId));
        if (registration == null) return false;
        registration.close();
        return true;
    }

    @Override
    public Optional<MixerAddonRegistration> find(String addonId) {
        return Optional.ofNullable(registrations.get(normalizeAddonId(addonId)));
    }

    @Override
    public Collection<MixerAddonRegistration> registrations() {
        return List.copyOf(registrations.values());
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        unregisterOwnedBy(event.getPlugin());
    }

    void unregisterOwnedBy(Plugin owner) {
        registrations.values().stream()
                .filter(registration -> registration.owner().equals(owner))
                .toList()
                .forEach(Registration::close);
        sources.unregisterOwnedBy(owner);
        playbackPolicies.unregisterOwnedBy(owner);
    }

    void shutdown() {
        List.copyOf(registrations.values()).forEach(Registration::close);
        sources.shutdown();
        playbackPolicies.shutdown();
    }

    private static String validateAddonId(String id) {
        String normalized = normalizeAddonId(id);
        if (!normalized.matches("[a-z0-9][a-z0-9._-]{0,63}")) {
            throw new IllegalArgumentException("Addon id must match [a-z0-9][a-z0-9._-]{0,63}: " + id);
        }
        return normalized;
    }

    private static String normalizeAddonId(String id) {
        return Objects.requireNonNull(id, "addon id").toLowerCase(Locale.ROOT);
    }

    private static void requireMainThread(String action) {
        MixerScheduler.requireGlobalThread(action);
    }

    private final class Context implements MixerAddonContext {
        private final Registration registration;

        private Context(Registration registration) {
            this.registration = registration;
        }

        @Override
        public MixerApi api() {
            return api;
        }

        @Override
        public Plugin owner() {
            return registration.owner();
        }

        @Override
        public MixerAudioSourceResolverRegistration registerSourceResolver(MixerAudioSourceResolver resolver) {
            if (!registration.active()) {
                throw new IllegalStateException("Addon is no longer active: " + registration.id);
            }
            MixerAudioSourceResolverRegistration sourceRegistration = sources.register(owner(), resolver);
            registration.resources.add(sourceRegistration);
            return sourceRegistration;
        }

        @Override
        public MixerPlaybackPolicyRegistration registerPlaybackPolicy(MixerPlaybackPolicy policy) {
            if (!registration.active()) {
                throw new IllegalStateException("Addon is no longer active: " + registration.id);
            }
            MixerPlaybackPolicyRegistration policyRegistration = playbackPolicies.register(owner(), policy);
            registration.resources.add(policyRegistration);
            return policyRegistration;
        }
    }

    private final class Registration implements MixerAddonRegistration {
        private final String id;
        private final Plugin owner;
        private final MixerAddon addon;
        private final AtomicBoolean active = new AtomicBoolean(true);
        private final List<AutoCloseable> resources = new CopyOnWriteArrayList<>();

        private Registration(String id, Plugin owner, MixerAddon addon) {
            this.id = id;
            this.owner = owner;
            this.addon = addon;
        }

        @Override
        public Plugin owner() {
            return owner;
        }

        @Override
        public MixerAddon addon() {
            return addon;
        }

        @Override
        public boolean active() {
            return active.get();
        }

        @Override
        public void close() {
            requireMainThread("unregister a Mixer addon");
            if (!active.compareAndSet(true, false)) return;

            registrations.remove(id, this);
            closeResources();
            try {
                addon.onDisable();
            } catch (Exception exception) {
                plugin.logDebug(Level.WARNING, "Failed to disable Mixer addon " + id, exception);
            }
        }

        private void closeResources() {
            List.copyOf(resources).forEach(resource -> {
                try {
                    resource.close();
                } catch (Exception exception) {
                    plugin.logDebug(Level.WARNING, "Failed to close a resource owned by Mixer addon " + id, exception);
                }
            });
            resources.clear();
        }
    }
}
