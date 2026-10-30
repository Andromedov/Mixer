package me.andromedov.mixer.core.api;

import org.bukkit.plugin.Plugin;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.ToIntFunction;
import java.util.function.Function;

/** Shared registration lifecycle for the independent public provider APIs. */
final class PrioritizedProviderRegistry<P> {
    private final String providerLabel;
    private final ConcurrentMap<String, Entry> entries = new ConcurrentHashMap<>();

    PrioritizedProviderRegistry(String providerLabel) {
        this.providerLabel = providerLabel;
    }

    Entry register(Plugin owner, String id, P provider) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(provider, "provider");
        String providerId = validateId(id);
        String key = owner.getName().toLowerCase(Locale.ROOT) + ":" + providerId;
        Entry entry = new Entry(key, owner, provider);
        if (entries.putIfAbsent(key, entry) != null) {
            throw new IllegalStateException(providerLabel + " provider already registered: " + key);
        }
        return entry;
    }

    <R> Collection<R> registrations(Function<Entry, R> mapper) {
        return entries.values().stream().map(mapper).toList();
    }

    List<Entry> ordered(ToIntFunction<P> priority) {
        return ordered(priority, false);
    }

    List<Entry> orderedDescending(ToIntFunction<P> priority) {
        return ordered(priority, true);
    }

    private List<Entry> ordered(ToIntFunction<P> priority, boolean descending) {
        Comparator<Entry> comparator = Comparator.comparingInt(
                entry -> priority.applyAsInt(entry.provider()));
        if (descending) comparator = comparator.reversed();
        return entries.values().stream()
                .filter(Entry::active)
                .sorted(comparator.thenComparing(Entry::key))
                .toList();
    }

    void unregisterOwnedBy(Plugin owner) {
        entries.values().stream()
                .filter(entry -> entry.owner().equals(owner))
                .toList()
                .forEach(Entry::close);
    }

    void shutdown() {
        List.copyOf(entries.values()).forEach(Entry::close);
    }

    private static String validateId(String id) {
        String normalized = Objects.requireNonNull(id, "provider id").toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9][a-z0-9._-]{0,63}")) {
            throw new IllegalArgumentException(
                    "provider id must match [a-z0-9][a-z0-9._-]{0,63}: " + id);
        }
        return normalized;
    }

    final class Entry implements AutoCloseable {
        private final String key;
        private final Plugin owner;
        private final P provider;
        private final AtomicBoolean active = new AtomicBoolean(true);

        private Entry(String key, Plugin owner, P provider) {
            this.key = key;
            this.owner = owner;
            this.provider = provider;
        }

        String key() { return key; }
        Plugin owner() { return owner; }
        P provider() { return provider; }
        boolean active() { return active.get(); }

        @Override
        public void close() {
            if (active.compareAndSet(true, false)) entries.remove(key, this);
        }
    }
}
