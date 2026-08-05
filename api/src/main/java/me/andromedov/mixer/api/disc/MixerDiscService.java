package me.andromedov.mixer.api.disc;

import org.bukkit.inventory.ItemStack;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

public interface MixerDiscService {
    /**
     * Resolves and loads enough of a source to obtain its metadata without starting
     * playback. The work always completes asynchronously.
     */
    CompletionStage<MixerDisc> probe(String source);

    /**
     * Creates a new Mixer disc from a cloned template. Must be called on the Bukkit
     * main thread. The input item is never modified.
     */
    ItemStack createDisc(ItemStack template, MixerDisc disc);

    /** Must be called on the Bukkit main thread. */
    Optional<MixerDisc> readDisc(ItemStack item);

    /** Must be called on the Bukkit main thread. */
    boolean isMixerDisc(ItemStack item);
}
