package net.somewhatcity.mixer.core.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import io.papermc.paper.math.BlockPosition;
import io.papermc.paper.command.brigadier.argument.resolvers.BlockPositionResolver;

import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.components.JukeboxPlayableComponent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.block.Block;
import org.bukkit.block.Jukebox;

import net.somewhatcity.mixer.core.MixerPlugin;
import net.somewhatcity.mixer.core.audio.IMixerAudioPlayer;
import net.somewhatcity.mixer.core.util.MessageUtil;
import net.somewhatcity.mixer.core.util.Utils;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CommandRegistry {

    private final MixerPlugin plugin;
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final ExecutorService EXECUTOR_SERVICE = Executors.newSingleThreadExecutor();

    public CommandRegistry(MixerPlugin plugin) {
        this.plugin = plugin;
    }

    public void registerCommands() {
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            final Commands commands = event.registrar();

            LiteralArgumentBuilder<CommandSourceStack> mixerCommand = Commands.literal("mixer")
                    .then(registerBurnCommand())
                    .then(registerLinkCommand())
                    .then(registerRedstoneCommand())
                    .then(registerDspCommand());

            commands.register(mixerCommand.build(), "Main command for the Mixer plugin.");
        });
    }

    // --- /mixer burn ---
    private LiteralArgumentBuilder<CommandSourceStack> registerBurnCommand() {
        return Commands.literal("burn")
                .requires(source -> source.getSender().hasPermission("mixer.command.burn"))
                .then(Commands.argument("url", StringArgumentType.greedyString())
                        .executes(this::executeBurn));
    }

    private int executeBurn(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            MessageUtil.sendErrMsg(sender, "must_be_player");
            return 0;
        }

        String url = ctx.getArgument("url", String.class);

        ItemStack item = player.getInventory().getItemInMainHand();
        if (!Utils.isDisc(item)) {
            MessageUtil.sendErrMsg(player, "no_disc");
            return 0;
        }

        MessageUtil.sendMsg(player, "loading_track");

        EXECUTOR_SERVICE.submit(() -> {
            String oldUrl;
            String finalUrl = url;
            if (finalUrl.startsWith("file://")) {
                String filename = finalUrl.substring(7);
                File file = new File(filename);
                if (file.exists() && file.isFile()) {
                    finalUrl = file.getAbsolutePath();
                }
            }
            if (finalUrl.startsWith("cobalt://")) {
                String uri = finalUrl.substring(8);
                oldUrl = finalUrl;
                finalUrl = Utils.requestCobaltMediaUrl(uri);
                if (finalUrl == null) {
                    player.sendMessage("<c>Error while loading cobalt media");
                    return;
                }
            } else {
                oldUrl = "";
            }

            String urlForLambda = finalUrl;
            IMixerAudioPlayer.APM.loadItem(urlForLambda, new AudioLoadResultHandler() {
                @Override
                public void trackLoaded(AudioTrack audioTrack) {
                    AudioTrackInfo info = audioTrack.getInfo();
                    Bukkit.getScheduler().runTask(MixerPlugin.getPlugin(), () -> {
                        String urlToSet =!oldUrl.isEmpty()? oldUrl : urlForLambda;
                        applyDiscMeta(item, info, urlToSet);
                        MessageUtil.sendMsg(player, "track_loaded", info.title);
                    });
                }

                @Override
                public void playlistLoaded(AudioPlaylist audioPlaylist) {
                    AudioTrackInfo info = audioPlaylist.getSelectedTrack().getInfo();
                    Bukkit.getScheduler().runTask(MixerPlugin.getPlugin(), () -> {
                        applyDiscMeta(item, info, urlForLambda);
                        MessageUtil.sendMsg(player, "track_loaded", info.title);
                    });
                }

                @Override
                public void noMatches() {
                    MessageUtil.sendErrMsg(player, "no_matches");
                }

                @Override
                public void loadFailed(FriendlyException e) {
                    MessageUtil.sendErrMsg(player, e.getMessage());
                }
            });
        });

        return Command.SINGLE_SUCCESS;
    }

    private void applyDiscMeta(ItemStack item, AudioTrackInfo info, String urlToSet) {
        item.editMeta(meta -> {
            meta.displayName(MM.deserialize("<reset>" + info.author + " - " + info.title).decoration(TextDecoration.ITALIC, false));
            NamespacedKey mixerData = new NamespacedKey(MixerPlugin.getPlugin(), "mixer_data");
            meta.getPersistentDataContainer().set(mixerData, PersistentDataType.STRING, urlToSet);

            JukeboxPlayableComponent playableComponent = meta.getJukeboxPlayable();
            try {
                playableComponent.setSongKey(mixerData);
            } catch (Exception e) {
                MixerPlugin.getPlugin().getLogger().warning("Failed to set custom jukebox key, using fallback: " + e.getMessage());
                try {
                    NamespacedKey fallbackKey = NamespacedKey.minecraft("pigstep");
                    playableComponent.setSongKey(fallbackKey);
                } catch (Exception fallbackException) {
                    MixerPlugin.getPlugin().getLogger().severe("Even fallback jukebox key failed: " + fallbackException.getMessage());
                    return;
                }
            }
            meta.setJukeboxPlayable(playableComponent);
        });
    }

    // --- /mixer link ---
    private LiteralArgumentBuilder<CommandSourceStack> registerLinkCommand() {
        return Commands.literal("link")
                .requires(source -> source.getSender().hasPermission("mixer.command.link"))
                .then(Commands.argument("jukebox", ArgumentTypes.blockPosition())
                        .executes(this::executeLink));
    }

    private int executeLink(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        CommandSender sender = source.getSender();
        if (!(sender instanceof Player player)) {
            MessageUtil.sendErrMsg(sender, "must_be_player");
            return 0;
        }

        Location jukeboxLoc = getBukkitLocation(ctx, "jukebox", player.getWorld());
        if (jukeboxLoc == null) {
            MessageUtil.sendErrMsg(player, "invalid_location");
            return 0;
        }

        Block block = jukeboxLoc.getBlock();
        if (!block.getType().equals(Material.JUKEBOX)) {
            MessageUtil.sendErrMsg(player, "no_jukebox");
            return 0;
        }

        Jukebox jukebox = (Jukebox) block.getState();
        NamespacedKey mixerLinks = new NamespacedKey(MixerPlugin.getPlugin(), "mixer_links");
        String data = jukebox.getPersistentDataContainer().get(mixerLinks, PersistentDataType.STRING);
        JsonArray linked = (data == null || data.isEmpty())? new JsonArray() : (JsonArray) JsonParser.parseString(data);

        Location loc = player.getLocation().toCenterLocation();
        JsonObject locData = new JsonObject();
        locData.addProperty("x", loc.getX());
        locData.addProperty("y", loc.getY());
        locData.addProperty("z", loc.getZ());
        locData.addProperty("world", loc.getWorld().getName());

        linked.add(locData);
        jukebox.getPersistentDataContainer().set(mixerLinks, PersistentDataType.STRING, linked.toString());
        jukebox.update();
        MessageUtil.sendMsg(player, "location_link");
        return Command.SINGLE_SUCCESS;
    }


    // --- /mixer redstone ---
    private LiteralArgumentBuilder<CommandSourceStack> registerRedstoneCommand() {
        return Commands.literal("redstone")
                .requires(source -> source.getSender().hasPermission("mixer.command.redstone"))
                .then(Commands.argument("jukebox", ArgumentTypes.blockPosition())
                        .then(Commands.argument("magnitude", IntegerArgumentType.integer(0, 2048))
                                .then(Commands.argument("trigger", IntegerArgumentType.integer(0))
                                        .then(Commands.argument("delay", IntegerArgumentType.integer(0))
                                                .executes(this::executeRedstone)))));
    }

    private int executeRedstone(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        CommandSender sender = source.getSender();
        if (!(sender instanceof Player player)) {
            MessageUtil.sendErrMsg(sender, "must_be_player");
            return 0;
        }

        Location jukeboxLoc = getBukkitLocation(ctx, "jukebox", player.getWorld());
        if (jukeboxLoc == null) {
            MessageUtil.sendErrMsg(player, "invalid_location");
            return 0;
        }

        Block block = jukeboxLoc.getBlock();
        if (!block.getType().equals(Material.JUKEBOX)) {
            MessageUtil.sendErrMsg(player, "no_jukebox");
            return 0;
        }

        Jukebox jukebox = (Jukebox) block.getState();
        NamespacedKey mixerRedstones = new NamespacedKey(MixerPlugin.getPlugin(), "mixer_redstones");
        String data = jukebox.getPersistentDataContainer().get(mixerRedstones, PersistentDataType.STRING);
        JsonArray redstones = (data == null || data.isEmpty())? new JsonArray() : (JsonArray) JsonParser.parseString(data);

        if (player.getTargetBlockExact(10) == null) {
            MessageUtil.sendErrMsg(player, "not_looking");
            return 0;
        }

        Location loc = player.getTargetBlockExact(10).getLocation();
        JsonObject locData = new JsonObject();
        locData.addProperty("x", loc.getX());
        locData.addProperty("y", loc.getY());
        locData.addProperty("z", loc.getZ());
        locData.addProperty("world", loc.getWorld().getName());
        locData.addProperty("mag", ctx.getArgument("magnitude", Integer.class));
        locData.addProperty("trigger", ctx.getArgument("trigger", Integer.class));
        locData.addProperty("delay", ctx.getArgument("delay", Integer.class));

        redstones.add(locData);
        jukebox.getPersistentDataContainer().set(mixerRedstones, PersistentDataType.STRING, redstones.toString());
        jukebox.update();
        MessageUtil.sendMsg(player, "redstone_location_link");
        return Command.SINGLE_SUCCESS;
    }

    // --- /mixer dsp ---
    private LiteralArgumentBuilder<CommandSourceStack> registerDspCommand() {
        return Commands.literal("dsp")
                .then(Commands.argument("jukebox", ArgumentTypes.blockPosition())
                        // /mixer dsp <location>
                        .executes(this::executeDspReset)

                        // /mixer dsp <location> gain <gain>
                        .then(Commands.literal("gain")
                                .requires(source -> source.getSender().hasPermission("mixer.command.dsp.gain"))
                                .then(Commands.argument("gain", DoubleArgumentType.doubleArg())
                                        .executes(this::executeDspGain)))

                        // /mixer dsp <location> highPassFilter <frequency>
                        .then(Commands.literal("highPassFilter")
                                .requires(source -> source.getSender().hasPermission("mixer.command.dsp.highpass"))
                                .then(Commands.argument("frequency", FloatArgumentType.floatArg())
                                        .executes(this::executeDspHighPass)))

                        // /mixer dsp <location> lowPassFilter <frequency>
                        .then(Commands.literal("lowPassFilter")
                                .requires(source -> source.getSender().hasPermission("mixer.command.dsp.lowpass"))
                                .then(Commands.argument("frequency", FloatArgumentType.floatArg())
                                        .executes(this::executeDspLowPass)))

                        // /mixer dsp <location> flangerEffect <maxFlangerLength> <wet> <lfoFrequency>
                        .then(Commands.literal("flangerEffect")
                                .requires(source -> source.getSender().hasPermission("mixer.command.dsp.flanger"))
                                .then(Commands.argument("maxFlangerLength", DoubleArgumentType.doubleArg())
                                        .then(Commands.argument("wet", DoubleArgumentType.doubleArg())
                                                .then(Commands.argument("lfoFrequency", DoubleArgumentType.doubleArg())
                                                        .executes(this::executeDspFlanger)))))
                );
    }

    private int executeDspReset(CommandContext<CommandSourceStack> ctx) {
        Location location = getBukkitLocation(ctx, "jukebox");
        if(location == null) {
            ctx.getSource().getSender().sendMessage(MM.deserialize("<red>Invalid location or not a player."));
            return 0;
        }

        JsonObject obj = Utils.loadNbtData(location, "mixer_dsp");
        if(obj == null) {
            ctx.getSource().getSender().sendMessage(MM.deserialize("<red>No jukebox at location"));
            return 0;
        }

        Utils.saveNbtData(location, "mixer_dsp", new JsonObject());
        ctx.getSource().getSender().sendMessage(MM.deserialize("<green>DSP reset"));
        return Command.SINGLE_SUCCESS;
    }

    private int executeDspGain(CommandContext<CommandSourceStack> ctx) {
        Location location = getBukkitLocation(ctx, "jukebox");
        double gain = ctx.getArgument("gain", Double.class);

        JsonObject obj = Utils.loadNbtData(location, "mixer_dsp");
        if(obj == null) {
            ctx.getSource().getSender().sendMessage(MM.deserialize("<red>No jukebox at location"));
            return 0;
        }

        JsonObject settings = new JsonObject();
        settings.addProperty("gain", gain);
        obj.add("gain", settings);
        Utils.saveNbtData(location, "mixer_dsp", obj);
        ctx.getSource().getSender().sendMessage(MM.deserialize("<green>Gain set to " + gain));
        return Command.SINGLE_SUCCESS;
    }

    private int executeDspHighPass(CommandContext<CommandSourceStack> ctx) {
        Location location = getBukkitLocation(ctx, "jukebox");
        float frequency = ctx.getArgument("frequency", Float.class);

        JsonObject obj = Utils.loadNbtData(location, "mixer_dsp");
        if(obj == null) {
            ctx.getSource().getSender().sendMessage(MM.deserialize("<red>No jukebox at location"));
            return 0;
        }

        JsonObject settings = new JsonObject();
        settings.addProperty("frequency", frequency);
        obj.add("highPassFilter", settings);
        Utils.saveNbtData(location, "mixer_dsp", obj);
        ctx.getSource().getSender().sendMessage(MM.deserialize("<green>HighPass filter set to " + frequency + " Hz"));
        return Command.SINGLE_SUCCESS;
    }

    private int executeDspLowPass(CommandContext<CommandSourceStack> ctx) {
        Location location = getBukkitLocation(ctx, "jukebox");
        float frequency = ctx.getArgument("frequency", Float.class);

        JsonObject obj = Utils.loadNbtData(location, "mixer_dsp");
        if(obj == null) {
            ctx.getSource().getSender().sendMessage(MM.deserialize("<red>No jukebox at location"));
            return 0;
        }

        JsonObject settings = new JsonObject();
        settings.addProperty("frequency", frequency);
        obj.add("lowPassFilter", settings);
        Utils.saveNbtData(location, "mixer_dsp", obj);
        ctx.getSource().getSender().sendMessage(MM.deserialize("<green>LowPass filter set to " + frequency + " Hz"));
        return Command.SINGLE_SUCCESS;
    }

    private int executeDspFlanger(CommandContext<CommandSourceStack> ctx) {
        Location location = getBukkitLocation(ctx, "jukebox");
        double maxFlangerLength = ctx.getArgument("maxFlangerLength", Double.class);
        double wet = ctx.getArgument("wet", Double.class);
        double lfoFrequency = ctx.getArgument("lfoFrequency", Double.class);

        JsonObject obj = Utils.loadNbtData(location, "mixer_dsp");
        if(obj == null) {
            ctx.getSource().getSender().sendMessage(MM.deserialize("<red>No jukebox at location"));
            return 0;
        }

        JsonObject settings = new JsonObject();
        settings.addProperty("maxFlangerLength", maxFlangerLength);
        settings.addProperty("wet", wet);
        settings.addProperty("lfoFrequency", lfoFrequency);
        obj.add("flangerEffect", settings);
        Utils.saveNbtData(location, "mixer_dsp", obj);
        ctx.getSource().getSender().sendMessage(MM.deserialize("<green>Flanger effect updated"));
        return Command.SINGLE_SUCCESS;
    }


    private Location getBukkitLocation(CommandContext<CommandSourceStack> ctx, String argumentName) {
        CommandSender sender = ctx.getSource().getSender();
        org.bukkit.World world = null;

        if(sender instanceof Player player) {
            world = player.getWorld();
        } else if (sender instanceof org.bukkit.command.BlockCommandSender blockSender) {
            world = blockSender.getBlock().getWorld();
        }

        if (world == null) {
            return null;
        }

        return getBukkitLocation(ctx, argumentName, world);
    }

    private Location getBukkitLocation(CommandContext<CommandSourceStack> ctx, String argumentName, org.bukkit.World world) {
        try {
            BlockPositionResolver resolver = ctx.getArgument(argumentName, BlockPositionResolver.class);
            BlockPosition pos = resolver.resolve(ctx.getSource());
            return pos.toLocation(world);
        } catch (Exception e) {
            return null;
        }
    }
}
