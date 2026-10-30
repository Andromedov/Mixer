package me.andromedov.mixer.api.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.Collection;

public interface DspMenuRegistry {
    DspMenuProviderRegistration register(Plugin owner, DspMenuProvider provider);

    Collection<DspMenuProviderRegistration> registrations();

    ItemStack renderItem(DspMenuItemContext context, ItemStack defaultItem);

    Component renderTitle(DspMenuContext context, Component defaultTitle);
}
