package me.andromedov.mixer.core.api;

import me.andromedov.mixer.api.playback.MixerPlaybackDecision;
import me.andromedov.mixer.api.playback.MixerPlaybackPolicy;
import me.andromedov.mixer.api.playback.MixerPlaybackPolicyRegistration;
import me.andromedov.mixer.api.playback.MixerPlaybackPolicyRegistry;
import me.andromedov.mixer.api.playback.MixerPlaybackRequest;
import me.andromedov.mixer.core.MixerPlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
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

final class ImplMixerPlaybackPolicyRegistry implements MixerPlaybackPolicyRegistry {
    private final MixerPlugin plugin;
    private final ConcurrentMap<String, Registration> registrations = new ConcurrentHashMap<>();

    ImplMixerPlaybackPolicyRegistry(MixerPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public MixerPlaybackPolicyRegistration register(Plugin owner, MixerPlaybackPolicy policy) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(policy, "policy");
        requireMainThread("register a playback policy");

        String policyId = validateId(policy.id());
        String key = owner.getName().toLowerCase(Locale.ROOT) + ":" + policyId;
        Registration registration = new Registration(key, owner, policy);
        if (registrations.putIfAbsent(key, registration) != null) {
            throw new IllegalStateException("Playback policy already registered: " + key);
        }
        return registration;
    }

    @Override
    public Collection<MixerPlaybackPolicyRegistration> registrations() {
        return List.copyOf(registrations.values());
    }

    @Override
    public MixerPlaybackDecision evaluate(MixerPlaybackRequest request) {
        Objects.requireNonNull(request, "request");
        requireMainThread("evaluate playback policies");

        List<Registration> ordered = registrations.values().stream()
                .filter(Registration::active)
                .sorted(Comparator.comparingInt((Registration registration) -> registration.policy().priority()).reversed()
                        .thenComparing(registration -> registration.key))
                .toList();

        for (Registration registration : ordered) {
            try {
                MixerPlaybackDecision decision = Objects.requireNonNull(
                        registration.policy().evaluate(request),
                        "Playback policy " + registration.key + " returned null"
                );
                if (!decision.allowed()) return decision;
            } catch (Exception exception) {
                plugin.logDebug(Level.WARNING, "Playback policy failed closed: " + registration.key, exception);
                return MixerPlaybackDecision.deny(Component.text("Playback authorization failed."));
            }
        }
        return MixerPlaybackDecision.allow();
    }

    void unregisterOwnedBy(Plugin owner) {
        registrations.values().stream()
                .filter(registration -> registration.owner().equals(owner))
                .toList()
                .forEach(Registration::close);
    }

    void shutdown() {
        List.copyOf(registrations.values()).forEach(Registration::close);
    }

    private static String validateId(String id) {
        Objects.requireNonNull(id, "policy id");
        String normalized = id.toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9][a-z0-9._-]{0,63}")) {
            throw new IllegalArgumentException(
                    "policy id must match [a-z0-9][a-z0-9._-]{0,63}: " + id);
        }
        return normalized;
    }

    private static void requireMainThread(String action) {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Must " + action + " on the Bukkit main thread");
        }
    }

    private final class Registration implements MixerPlaybackPolicyRegistration {
        private final String key;
        private final Plugin owner;
        private final MixerPlaybackPolicy policy;
        private final AtomicBoolean active = new AtomicBoolean(true);

        private Registration(String key, Plugin owner, MixerPlaybackPolicy policy) {
            this.key = key;
            this.owner = owner;
            this.policy = policy;
        }

        @Override
        public Plugin owner() {
            return owner;
        }

        @Override
        public MixerPlaybackPolicy policy() {
            return policy;
        }

        @Override
        public boolean active() {
            return active.get();
        }

        @Override
        public void close() {
            if (active.compareAndSet(true, false)) registrations.remove(key, this);
        }
    }
}
