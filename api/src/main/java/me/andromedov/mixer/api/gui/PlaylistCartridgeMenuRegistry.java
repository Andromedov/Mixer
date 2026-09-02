package me.andromedov.mixer.api.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.Collection;

public interface PlaylistCartridgeMenuRegistry {
    PlaylistCartridgeMenuProviderRegistration register(
            Plugin owner, PlaylistCartridgeMenuProvider provider);

    Collection<PlaylistCartridgeMenuProviderRegistration> registrations();

    ItemStack renderItem(PlaylistCartridgeMenuItemContext context, ItemStack defaultItem);

    Component renderTitle(PlaylistCartridgeMenuContext context, Component defaultTitle);
}
