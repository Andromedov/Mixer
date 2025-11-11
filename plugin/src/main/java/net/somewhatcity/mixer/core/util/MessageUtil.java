/*
 * Copyright (c) 2024 mrmrmystery
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice (including the next paragraph) shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

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

    public static void reloadMessages() {
        if (localizationManager == null) {
            localizationManager.reloadLanguages();
        }
    }
}
