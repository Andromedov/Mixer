package me.andromedov.mixer.core.api;

import me.andromedov.mixer.api.source.MixerAudioSourceRegistry;
import me.andromedov.mixer.api.source.MixerAudioSourceResolutionException;
import me.andromedov.mixer.api.source.MixerAudioSourceResolver;
import me.andromedov.mixer.api.source.MixerAudioSourceResolverRegistration;
import me.andromedov.mixer.core.util.MixerScheduler;
import org.bukkit.plugin.Plugin;

import java.util.Collection;
import java.util.Objects;

final class ImplMixerAudioSourceRegistry implements MixerAudioSourceRegistry {
    private final PrioritizedProviderRegistry<MixerAudioSourceResolver> resolvers =
            new PrioritizedProviderRegistry<>("Audio source resolver");

    @Override
    public MixerAudioSourceResolverRegistration register(Plugin owner, MixerAudioSourceResolver resolver) {
        Objects.requireNonNull(resolver, "resolver");
        requireMainThread("register an audio source resolver");
        return new Registration(resolvers.register(owner, resolver.id(), resolver));
    }

    @Override
    public Collection<MixerAudioSourceResolverRegistration> registrations() {
        return resolvers.registrations(Registration::new);
    }

    @Override
    public String resolve(String source) throws MixerAudioSourceResolutionException {
        Objects.requireNonNull(source, "source");
        if (MixerScheduler.isTickThread()) {
            throw new MixerAudioSourceResolutionException("Audio sources must be resolved off server tick threads");
        }

        for (var registration : resolvers.orderedDescending(MixerAudioSourceResolver::priority)) {
            try {
                if (!registration.provider().supports(source)) continue;
                String resolved = registration.provider().resolve(source);
                if (resolved == null || resolved.isBlank()) {
                    throw new MixerAudioSourceResolutionException(
                            "Resolver " + registration.key() + " returned an empty source");
                }
                return resolved;
            } catch (MixerAudioSourceResolutionException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new MixerAudioSourceResolutionException(
                        "Resolver " + registration.key() + " failed for source " + source, exception);
            }
        }
        return source;
    }

    void unregisterOwnedBy(Plugin owner) {
        resolvers.unregisterOwnedBy(owner);
    }

    void shutdown() {
        resolvers.shutdown();
    }

    private static void requireMainThread(String action) {
        MixerScheduler.requireGlobalThread(action);
    }

    private record Registration(PrioritizedProviderRegistry<MixerAudioSourceResolver>.Entry entry)
            implements MixerAudioSourceResolverRegistration {
        @Override public Plugin owner() { return entry.owner(); }
        @Override public MixerAudioSourceResolver resolver() { return entry.provider(); }
        @Override public boolean active() { return entry.active(); }
        @Override public void close() { entry.close(); }
    }
}
