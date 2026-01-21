package net.somewhatcity.mixer.core.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;

public class MessageUtil {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static LocalizationManager localizationManager;

    public static void initialize(LocalizationManager manager) {
        localizationManager = manager;
    }

    public static void sendMsg(CommandSender sender, String messageKey, Object... args) {
        if (localizationManager == null) {
            return;
        }

        String prefix = localizationManager.getPrefix();
        String message = localizationManager.getMessage("success." + messageKey, args);

        Component component = MM.deserialize(prefix + message);
        sender.sendMessage(component);
    }

    public static void sendErrMsg(CommandSender sender, String messageKey, Object... args) {
        if (localizationManager == null) {
            sender.sendMessage("§cLocalization not initialized!");
            return;
        }

        String prefix = localizationManager.getPrefix();
        String message = localizationManager.getMessage("errors." + messageKey, args);

        Component component = MM.deserialize(prefix + "<red>" + message);
        sender.sendMessage(component);
    }

    public static void sendPlgMsg(CommandSender sender, String messageKey, Object... args) {
        if (localizationManager == null) {
            sender.sendMessage("§cLocalization not initialized!");
            return;
        }

        String prefix = localizationManager.getPrefix();
        String message = localizationManager.getMessage("plugin." + messageKey, args);

        Component component = MM.deserialize(prefix + message);
        sender.sendMessage(component);
    }

    public static void sendActionBarMsg(CommandSender sender, String messageKey, Object... args) {
        if (localizationManager == null) {
            sender.sendMessage("§cLocalization not initialized!");
            return;
        }

        String message = localizationManager.getMessage("actionBar." + messageKey, args);
        sender.sendActionBar(MM.deserialize(message));
    }

    public static void reloadMessages() {
        if (localizationManager != null) {
            localizationManager.reloadLanguages();
        }
    }
}
