package me.andromedov.mixer.core.api;

import me.andromedov.mixer.api.source.MixerAudioSourceRegistry;
import me.andromedov.mixer.api.source.MixerAudioSourceResolutionException;
import me.andromedov.mixer.api.source.MixerAudioSourceResolver;
import me.andromedov.mixer.api.source.MixerAudioSourceResolverRegistration;
import me.andromedov.mixer.core.util.MixerScheduler;
import org.bukkit.plugin.Plugin;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

final class ImplMixerAudioSourceRegistry implements MixerAudioSourceRegistry {
    private final ConcurrentMap<String, Registration> registrations = new ConcurrentHashMap<>();

    @Override
    public MixerAudioSourceResolverRegistration register(Plugin owner, MixerAudioSourceResolver resolver) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(resolver, "resolver");
        requireMainThread("register an audio source resolver");

        String resolverId = validateId(resolver.id(), "resolver");
        String key = owner.getName().toLowerCase(Locale.ROOT) + ":" + resolverId;
        Registration registration = new Registration(key, owner, resolver);
        if (registrations.putIfAbsent(key, registration) != null) {
            throw new IllegalStateException("Audio source resolver already registered: " + key);
        }
        return registration;
    }

    @Override
    public Collection<MixerAudioSourceResolverRegistration> registrations() {
        return List.copyOf(registrations.values());
    }

    @Override
    public String resolve(String source) throws MixerAudioSourceResolutionException {
        Objects.requireNonNull(source, "source");
        if (MixerScheduler.isTickThread()) {
            throw new MixerAudioSourceResolutionException("Audio sources must be resolved off server tick threads");
        }

        List<Registration> ordered = registrations.values().stream()
                .filter(Registration::active)
                .sorted(Comparator.comparingInt((Registration registration) -> registration.resolver().priority()).reversed()
                        .thenComparing(registration -> registration.key))
                .toList();

        for (Registration registration : ordered) {
            try {
                if (!registration.resolver().supports(source)) continue;
                String resolved = registration.resolver().resolve(source);
                if (resolved == null || resolved.isBlank()) {
                    throw new MixerAudioSourceResolutionException(
                            "Resolver " + registration.key + " returned an empty source");
                }
                return resolved;
            } catch (MixerAudioSourceResolutionException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new MixerAudioSourceResolutionException(
                        "Resolver " + registration.key + " failed for source " + source, exception);
            }
        }
        return source;
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

    private static String validateId(String id, String type) {
        Objects.requireNonNull(id, type + " id");
        String normalized = id.toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9][a-z0-9._-]{0,63}")) {
            throw new IllegalArgumentException(type + " id must match [a-z0-9][a-z0-9._-]{0,63}: " + id);
        }
        return normalized;
    }

    private static void requireMainThread(String action) {
        MixerScheduler.requireGlobalThread(action);
    }

    private final class Registration implements MixerAudioSourceResolverRegistration {
        private final String key;
        private final Plugin owner;
        private final MixerAudioSourceResolver resolver;
        private final AtomicBoolean active = new AtomicBoolean(true);

        private Registration(String key, Plugin owner, MixerAudioSourceResolver resolver) {
            this.key = key;
            this.owner = owner;
            this.resolver = resolver;
        }

        @Override
        public Plugin owner() {
            return owner;
        }

        @Override
        public MixerAudioSourceResolver resolver() {
            return resolver;
        }

        @Override
        public boolean active() {
            return active.get();
        }

        @Override
        public void close() {
            if (active.compareAndSet(true, false)) {
                registrations.remove(key, this);
            }
        }
    }
}
