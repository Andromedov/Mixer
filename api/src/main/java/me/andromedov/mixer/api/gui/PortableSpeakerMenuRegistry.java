package me.andromedov.mixer.api.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.Collection;

public interface PortableSpeakerMenuRegistry {
    PortableSpeakerMenuProviderRegistration register(Plugin owner, PortableSpeakerMenuProvider provider);

    Collection<PortableSpeakerMenuProviderRegistration> registrations();

    /** Applies all active providers to a clone of {@code defaultItem}. */
    ItemStack renderItem(PortableSpeakerMenuElement element, PortableSpeakerMenuContext context,
                         ItemStack defaultItem);

    /** Applies all active title customizers to {@code defaultTitle}. */
    Component renderTitle(PortableSpeakerMenuContext context, Component defaultTitle);
}
