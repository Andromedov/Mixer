/*
 * Copyright (c) 2023 mrmrmystery
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice (including the next paragraph) shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package net.somewhatcity.mixer.core.commands;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.GreedyStringArgument;
import dev.jorel.commandapi.arguments.IntegerArgument;
import dev.jorel.commandapi.arguments.LocationArgument;
import dev.jorel.commandapi.arguments.LocationType;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.somewhatcity.mixer.core.audio.IMixerAudioPlayer;
import net.somewhatcity.mixer.core.commands.dsp.DspCommand;
import net.somewhatcity.mixer.core.MixerPlugin;
import net.somewhatcity.mixer.core.util.MessageUtil;
import net.somewhatcity.mixer.core.util.Utils;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Jukebox;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.components.JukeboxPlayableComponent;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MixerCommand {
    private static CommandAPICommand command;
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final ExecutorService EXECUTOR_SERVICE = Executors.newSingleThreadExecutor();

    public MixerCommand() {
        command = new CommandAPICommand("mixer")
                .withSubcommand(new CommandAPICommand("burn")
                .withPermission("mixer.command.burn")
                .withArguments(new GreedyStringArgument("url"))
                .executesPlayer((player, args) -> {
                    ItemStack item = player.getInventory().getItemInMainHand();
                    if (!Utils.isDisc(item)) {
                        MessageUtil.sendErrMsg(player, "no_disc");
                        return;
                    }
                    MessageUtil.sendMsg(player, "loading_track");
                    EXECUTOR_SERVICE.submit(() -> {
                        try {
                            String url = (String) args.get(0);
                            String oldUrl = "";

                            if (url.startsWith("file://")) {
                                String filename = url.substring(7);
                                File file = new File(filename);
                                if (file.exists() && file.isFile()) {
                                    url = file.getAbsolutePath();
                                }
                            }

                            if (url.startsWith("cobalt://")) {
                                String uri = url.substring(8);
                                oldUrl = url;
                                url = Utils.requestCobaltMediaUrl(uri);
                                if (url == null) {
                                    MessageUtil.sendErrMsg(player, "loading_failed", "cobalt media");
                                    return;
                                }
                            }

                            String finalUrl = url;
                            String finalOldUrl = oldUrl;

                            IMixerAudioPlayer.APM.loadItem(url, new AudioLoadResultHandler() {
                                @Override
                                public void trackLoaded(AudioTrack audioTrack) {
                                    AudioTrackInfo info = audioTrack.getInfo();
                                    Bukkit.getScheduler().runTask(MixerPlugin.getPlugin(), () -> {
                                        String urlToSet = !finalOldUrl.isEmpty() ? finalOldUrl : finalUrl;
                                        updateDiscMetadata(item, info, urlToSet);
                                        MessageUtil.sendMsg(player, "track_loaded", info.title);
                                    });
                                }

                                @Override
                                public void playlistLoaded(AudioPlaylist audioPlaylist) {
                                    AudioTrack selectedTrack = audioPlaylist.getSelectedTrack();
                                    if (selectedTrack == null && !audioPlaylist.getTracks().isEmpty()) {
                                        selectedTrack = audioPlaylist.getTracks().get(0);
                                    }

                                    if (selectedTrack == null) {
                                        MessageUtil.sendErrMsg(player, "no_matches");
                                        return;
                                    }

                                    AudioTrackInfo info = selectedTrack.getInfo();
                                    Bukkit.getScheduler().runTask(MixerPlugin.getPlugin(), () -> {
                                        String urlToSet = !finalOldUrl.isEmpty() ? finalOldUrl : finalUrl;
                                        updateDiscMetadata(item, info, urlToSet);
                                        MessageUtil.sendMsg(player, "track_loaded", info.title);
                                    });
                                }

                                @Override
                                public void noMatches() {
                                    MessageUtil.sendErrMsg(player, "no_matches");
                                }

                                @Override
                                public void loadFailed(FriendlyException e) {
                                    MessageUtil.sendErrMsg(player, "loading_failed", e.getMessage());
                                }
                            });
                        } catch (Exception e) {
                            MixerPlugin.getPlugin().getLogger().severe("Error in burn command: " + e.getMessage());
                            e.printStackTrace();
                            MessageUtil.sendErrMsg(player, "loading_failed", e.getMessage());
                        }
                    });
                }))
                .withSubcommand(new CommandAPICommand("link")
                        .withPermission("mixer.command.link")
                        .withArguments(new LocationArgument("jukebox", LocationType.BLOCK_POSITION))
                        .executesPlayer((player, args) -> {
                            Location jukeboxLoc = (Location) args.get(0);
                            Block block = jukeboxLoc.getBlock();
                            if (!block.getType().equals(Material.JUKEBOX)) {
                                MessageUtil.sendErrMsg(player, "no_jukebox");
                                return;
                            }
                            JsonArray linked;
                            Jukebox jukebox = (Jukebox) block.getState();
                            NamespacedKey mixerLinks = new NamespacedKey(MixerPlugin.getPlugin(), "mixer_links");
                            String data = jukebox.getPersistentDataContainer().get(mixerLinks, PersistentDataType.STRING);
                            if (data == null || data.isEmpty()) {
                                linked = new JsonArray();
                            } else {
                                linked = (JsonArray) JsonParser.parseString(data);
                            }
                            Location loc = player.getLocation().toCenterLocation();
                            JsonObject locData = new JsonObject();
                            locData.addProperty("x", loc.getX());
                            locData.addProperty("y", loc.getY());
                            locData.addProperty("z", loc.getZ());
                            locData.addProperty("world", loc.getWorld().getName());

                            linked.add(locData);
                            jukebox.getPersistentDataContainer().set(mixerLinks, PersistentDataType.STRING, linked.toString());
                            jukebox.update();
                            MessageUtil.sendMsg(player, "location_linked");
                        }))
                .withSubcommand(new CommandAPICommand("redstone")
                        .withPermission("mixer.command.redstone")
                        .withArguments(new LocationArgument("jukebox", LocationType.BLOCK_POSITION))
                        .withArguments(new IntegerArgument("magnitude", 0, 2048))
                        .withArguments(new IntegerArgument("trigger", 0))
                        .withArguments(new IntegerArgument("delay", 0))
                        .executesPlayer((player, args) -> {
                            Location jukeboxLoc = (Location) args.get(0);
                            Block block = jukeboxLoc.getBlock();
                            if (!block.getType().equals(Material.JUKEBOX)) {
                                MessageUtil.sendErrMsg(player, "no_jukebox");
                                return;
                            }
                            JsonArray redstones;
                            Jukebox jukebox = (Jukebox) block.getState();
                            NamespacedKey mixerRedstones = new NamespacedKey(MixerPlugin.getPlugin(), "mixer_redstones");
                            String data = jukebox.getPersistentDataContainer().get(mixerRedstones, PersistentDataType.STRING);
                            if (data == null || data.isEmpty()) {
                                redstones = new JsonArray();
                            } else {
                                redstones = (JsonArray) JsonParser.parseString(data);
                            }
                            if (player.getTargetBlockExact(10) == null) {
                                MessageUtil.sendErrMsg(player, "no_block");
                                return;
                            }
                            Location loc = player.getTargetBlockExact(10).getLocation();

                            JsonObject locData = new JsonObject();
                            locData.addProperty("x", loc.getX());
                            locData.addProperty("y", loc.getY());
                            locData.addProperty("z", loc.getZ());
                            locData.addProperty("world", loc.getWorld().getName());
                            locData.addProperty("mag", (int) args.get(1));
                            locData.addProperty("trigger", (int) args.get(2));
                            locData.addProperty("delay", (int) args.get(3));
                            redstones.add(locData);
                            jukebox.getPersistentDataContainer().set(mixerRedstones, PersistentDataType.STRING, redstones.toString());
                            jukebox.update();
                            MessageUtil.sendMsg(player, "redstone_linked");
                        }))
                .withSubcommand(new CommandAPICommand("reload")
                        .withPermission("mixer.command.reload")
                        .executesPlayer((player, args) -> {
                            MixerPlugin.getPlugin().reloadPluginConfig();
                            MessageUtil.sendMsg(player, "config_reloaded");
                        }))
                .withSubcommand(new CommandAPICommand("audioinfo")
                        .withPermission("mixer.command.audioinfo")
                        .executesPlayer((player, args) -> {
                            Utils.logAudioConfiguration();
                            MixerPlugin plugin = MixerPlugin.getPlugin();

                            player.sendMessage("§a=== Audio Configuration ===");
                            player.sendMessage("§7Sample Rate: §f" + plugin.getAudioSampleRate() + " Hz");
                            player.sendMessage("§7Buffer Size: §f" + plugin.getAudioBufferSize() + " samples");
                            player.sendMessage("§7Frame Buffer Duration: §f" + plugin.getAudioFrameBufferDuration() + " ms");
                            player.sendMessage("§7Volume: §f" + plugin.getVolumePercent() + "% (" +
                                    String.format("%.2f", plugin.getVolumeMultiplier()) + "x)");
                            player.sendMessage("§7YouTube: §f" + (plugin.isYoutubeEnabled() ? "Enabled" : "Disabled"));
                            player.sendMessage("§7Language: §f" + plugin.getLanguage());
                        }))
                .withSubcommand(new DspCommand());
        register();
    }

    private void updateDiscMetadata(ItemStack item, AudioTrackInfo info, String url) {
        item.editMeta(meta -> {
            String displayName = "<green>" + info.author + " - " + info.title;
            meta.displayName(MM.deserialize(displayName).decoration(TextDecoration.ITALIC, false));

            NamespacedKey mixerData = new NamespacedKey(MixerPlugin.getPlugin(), "mixer_data");
            meta.getPersistentDataContainer().set(mixerData, PersistentDataType.STRING, url);

            JukeboxPlayableComponent playableComponent = meta.getJukeboxPlayable();

            boolean keySet = false;
            try {
                playableComponent.setSongKey(mixerData);
                keySet = true;
            } catch (Exception e) {
                MixerPlugin.getPlugin().getLogger().warning("Failed to set custom jukebox key, using fallback: " + e.getMessage());
            }

            if (!keySet) {
                try {
                    NamespacedKey fallbackKey = NamespacedKey.minecraft("pigstep");
                    playableComponent.setSongKey(fallbackKey);
                } catch (Exception fallbackException) {
                    MixerPlugin.getPlugin().getLogger().severe("Even fallback jukebox key failed: " + fallbackException.getMessage());
                }
            }

            meta.setJukeboxPlayable(playableComponent);
        });
    }
    public void register() {
        command.register();
    }
}