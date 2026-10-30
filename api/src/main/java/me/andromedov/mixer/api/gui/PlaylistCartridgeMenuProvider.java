package me.andromedov.mixer.api.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

/** Customizes cartridge editor visuals without owning its layout or click handling. */
public interface PlaylistCartridgeMenuProvider {
    String id();

    /** Higher-priority providers are applied after lower-priority providers. */
    default int priority() {
        return 0;
    }

    /** Returning {@code null} keeps the item produced by Mixer or the preceding provider. */
    default @Nullable ItemStack customizeItem(PlaylistCartridgeMenuItemContext context,
                                               ItemStack defaultItem) {
        return defaultItem;
    }

    /** Returning {@code null} keeps the current title unchanged. */
    default @Nullable Component customizeTitle(PlaylistCartridgeMenuContext context,
                                                Component defaultTitle) {
        return defaultTitle;
    }
}
