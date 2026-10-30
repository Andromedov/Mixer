package me.andromedov.mixer.api.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Customizes the portable speaker menu without owning its layout or click handling.
 * Providers run on the owning thread of {@link PortableSpeakerMenuContext#player()}.
 */
public interface PortableSpeakerMenuProvider {
    String id();

    /** Higher-priority providers are applied after lower-priority providers. */
    default int priority() {
        return 0;
    }

    /**
     * Receives a clone of the item produced by Mixer or the preceding provider.
     * Returning {@code null} keeps that item unchanged.
     */
    default @Nullable ItemStack customizeItem(PortableSpeakerMenuElement element,
                                               PortableSpeakerMenuContext context,
                                               ItemStack defaultItem) {
        return defaultItem;
    }

    /** Returning {@code null} keeps the current title unchanged. */
    default @Nullable Component customizeTitle(PortableSpeakerMenuContext context,
                                                Component defaultTitle) {
        return defaultTitle;
    }
}
