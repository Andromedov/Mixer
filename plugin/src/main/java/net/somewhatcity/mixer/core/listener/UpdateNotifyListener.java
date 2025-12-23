package net.somewhatcity.mixer.core.listener;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.somewhatcity.mixer.core.MixerPlugin;
import net.somewhatcity.mixer.core.util.MessageUtil;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class UpdateNotifyListener implements Listener {

    private final MixerPlugin plugin;
    private String newVersion;
    private String versionId;

    public UpdateNotifyListener(MixerPlugin plugin) {
        this.plugin = plugin;
    }

    public void setNewVersion(String newVersion, String versionId) {
        this.newVersion = newVersion;
        this.versionId = versionId;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (!plugin.isUpdateNotifierJoin() || newVersion == null) return;

        if (e.getPlayer().hasPermission("mixer.update-notify") || e.getPlayer().isOp()) {
            MessageUtil.sendPlgMsg(e.getPlayer(), "update_available", newVersion, plugin.getDescription().getVersion());
            String message = plugin.getLocalizationManager().getMessage("plugin.click_to_download", versionId);
            e.getPlayer().sendMessage(MiniMessage.miniMessage().deserialize(message));
        }
    }
}