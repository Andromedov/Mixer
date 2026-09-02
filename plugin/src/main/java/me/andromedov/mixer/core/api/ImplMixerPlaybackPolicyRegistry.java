package me.andromedov.mixer.core.api;

import me.andromedov.mixer.api.playback.MixerPlaybackDecision;
import me.andromedov.mixer.api.playback.MixerPlaybackPolicy;
import me.andromedov.mixer.api.playback.MixerPlaybackPolicyRegistration;
import me.andromedov.mixer.api.playback.MixerPlaybackPolicyRegistry;
import me.andromedov.mixer.api.playback.MixerPlaybackRequest;
import me.andromedov.mixer.core.MixerPlugin;
import me.andromedov.mixer.core.util.MixerScheduler;
import net.kyori.adventure.text.Component;
import org.bukkit.plugin.Plugin;

import java.util.Collection;
import java.util.Objects;
import java.util.logging.Level;

final class ImplMixerPlaybackPolicyRegistry implements MixerPlaybackPolicyRegistry {
    private final MixerPlugin plugin;
    private final PrioritizedProviderRegistry<MixerPlaybackPolicy> policies =
            new PrioritizedProviderRegistry<>("Playback policy");

    ImplMixerPlaybackPolicyRegistry(MixerPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public MixerPlaybackPolicyRegistration register(Plugin owner, MixerPlaybackPolicy policy) {
        Objects.requireNonNull(policy, "policy");
        MixerScheduler.requireGlobalThread("register a playback policy");
        return new Registration(policies.register(owner, policy.id(), policy));
    }

    @Override
    public Collection<MixerPlaybackPolicyRegistration> registrations() {
        return policies.registrations(Registration::new);
    }

    @Override
    public MixerPlaybackDecision evaluate(MixerPlaybackRequest request) {
        Objects.requireNonNull(request, "request");
        requireMainThread("evaluate playback policies");

        for (var registration : policies.orderedDescending(MixerPlaybackPolicy::priority)) {
            try {
                MixerPlaybackDecision decision = Objects.requireNonNull(
                        registration.provider().evaluate(request),
                        "Playback policy " + registration.key() + " returned null"
                );
                if (!decision.allowed()) return decision;
            } catch (Exception exception) {
                plugin.logDebug(Level.WARNING,
                        "Playback policy failed closed: " + registration.key(), exception);
                return MixerPlaybackDecision.deny(Component.text("Playback authorization failed."));
            }
        }
        return MixerPlaybackDecision.allow();
    }

    void unregisterOwnedBy(Plugin owner) {
        policies.unregisterOwnedBy(owner);
    }

    void shutdown() {
        policies.shutdown();
    }

    private static void requireMainThread(String action) {
        MixerScheduler.requireTickThread(action);
    }

    private record Registration(PrioritizedProviderRegistry<MixerPlaybackPolicy>.Entry entry)
            implements MixerPlaybackPolicyRegistration {
        @Override public Plugin owner() { return entry.owner(); }
        @Override public MixerPlaybackPolicy policy() { return entry.provider(); }
        @Override public boolean active() { return entry.active(); }
        @Override public void close() { entry.close(); }
    }
}
