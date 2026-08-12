package me.andromedov.mixer.core.commands;

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
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.block.Block;
import org.bukkit.block.Jukebox;

import me.andromedov.mixer.core.MixerPlugin;
import me.andromedov.mixer.core.audio.IMixerAudioPlayer;
import me.andromedov.mixer.core.util.MessageUtil;
import me.andromedov.mixer.core.util.Utils;
import me.andromedov.mixer.api.source.MixerAudioSourceResolutionException;
import me.andromedov.mixer.api.MixerTrack;
import me.andromedov.mixer.api.disc.MixerDisc;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;

public class CommandRegistry {

    private final MixerPlugin plugin;
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private final ExecutorService executorService = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Mixer-Burn-Thread");
        thread.setDaemon(true);
        return thread;
    });

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
                    .then(registerDspCommand())
                    .then(registerSpeakerCommand())
                    .then(registerCartridgeCommand())
                    .then(registerReloadCommand());

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

        String rawUrl = ctx.getArgument("url", String.class);
        boolean saveLocal = false;

        // Parse the --save or -s argument from the greedy string
        if (rawUrl.endsWith(" --save") || rawUrl.endsWith(" -s")) {
            saveLocal = true;
            rawUrl = rawUrl.replaceAll(" --save$", "").replaceAll(" -s$", "").trim();
        }

        String originalInput = rawUrl;

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) {
            MessageUtil.sendErrMsg(player, "no_disc");
            return 0;
        }

        // Check burn requirements
        if (plugin.isBurnRequirementsEnabled()) {
            boolean validMaterial = true;
            boolean validModelData = true;
            boolean validItemModel = true;

            if (!plugin.getBurnMaterial().equalsIgnoreCase("ANY")) {
                if (!item.getType().name().equalsIgnoreCase(plugin.getBurnMaterial())) {
                    validMaterial = false;
                }
            }

            ItemMeta meta = item.getItemMeta();

            if (plugin.getBurnCustomModelData() != -1) {
                if (meta == null || !meta.hasCustomModelData() || meta.getCustomModelData() != plugin.getBurnCustomModelData()) {
                    validModelData = false;
                }
            }

            if (!plugin.getBurnItemModel().isEmpty()) {
                try {
                    String modelStr = plugin.getBurnItemModel();
                    if (!modelStr.contains(":")) {
                        modelStr = "minecraft:" + modelStr;
                    }
                    NamespacedKey requiredModel = NamespacedKey.fromString(modelStr);

                    if (meta == null || !meta.hasItemModel() || requiredModel == null || !requiredModel.equals(meta.getItemModel())) {
                        validItemModel = false;
                    }
                } catch (Throwable e) {
                    plugin.logDebug(Level.WARNING, "ItemModel check failed. This usually means your server version (<1.21.4) doesn't support item models, or the itemModel format in config is invalid.", null);
                    validItemModel = false;
                }
            }

            if (!validMaterial || !validModelData || !validItemModel) {
                MessageUtil.sendErrMsg(player, "invalid_disc_model");
                return 0;
            }
        } else {
            if (!Utils.isDisc(item)) {
                MessageUtil.sendErrMsg(player, "no_disc");
                return 0;
            }
        }

        MessageUtil.sendMsg(player, "loading_track");

        final boolean doSaveLocal = saveLocal;
        final int sourceSlot = player.getInventory().getHeldItemSlot();
        final ItemStack expectedItem = item.clone();

        executorService.submit(() -> {
            String streamUrl = originalInput;
            String urlToSaveOnDisc = originalInput;
            File localAudioFile = null;

            try {
                streamUrl = plugin.api().sources().resolve(streamUrl);
            } catch (MixerAudioSourceResolutionException exception) {
                plugin.logDebug(Level.WARNING, "Addon source resolver failed", exception);
                runForPlayer(player, () -> MessageUtil.sendErrMsg(player, "loading_failed", exception.getMessage()));
                return;
            }

            // 1. Handle File URLs
            if (streamUrl.startsWith("file://")) {
                String filename = streamUrl.substring(7);
                File file = new File(filename);
                if (file.exists() && file.isFile()) {
                    streamUrl = file.getAbsolutePath();
                    urlToSaveOnDisc = streamUrl;
                }
            }
            // 2. Handle Cobalt URLs
            else if (streamUrl.startsWith("cobalt://") || streamUrl.startsWith("cobalt:")) {
                String uri = streamUrl.replaceFirst("^cobalt:(//)?", "");
                if (!uri.startsWith("http://") && !uri.startsWith("https://")) {
                    uri = "https://" + uri;
                }
                streamUrl = Utils.requestCobaltMediaUrl(uri);
                if (streamUrl == null) {
                    runForPlayer(player, () -> player.sendMessage(MM.deserialize("<red>Cobalt API Error: unable to obtain a direct link. Try again later.</red>")));
                    return; // Stops here, no HTML downloading
                }
                urlToSaveOnDisc = originalInput; // Keep cobalt://... on the disc if NOT saving locally
            }

            // 3. Handle Local Saving (-s)
            if (doSaveLocal && streamUrl.startsWith("http")) {
                runForPlayer(player, () -> MessageUtil.sendMsg(player, "downloading_track"));

                // If we haven't already passed it through Cobalt, and it's a standard link like YouTube/SoundCloud
                if (!originalInput.startsWith("cobalt") && !streamUrl.matches(".*\\.(mp3|wav|ogg|flac|m4a|aac)(\\?.*)?$")) {
                    String resolved = Utils.requestCobaltMediaUrl(streamUrl);
                    if (resolved != null && !resolved.isEmpty()) {
                        streamUrl = resolved;
                    } else {
                        runForPlayer(player, () -> MessageUtil.sendErrMsg(player, "download_failed"));
                        plugin.logDebug(Level.WARNING, "Failed to resolve direct URL for local saving. Aborting download.", null);
                        return;
                    }
                }

                String fileName = UUID.randomUUID().toString() + ".mp3";
                File downloadedAudio = Utils.downloadFile(streamUrl, fileName);

                // Utils.downloadFile now checks for HTML, so it will return null if it's not media
                if (downloadedAudio != null && downloadedAudio.exists()) {
                    localAudioFile = downloadedAudio;
                    streamUrl = downloadedAudio.getAbsolutePath();
                    urlToSaveOnDisc = streamUrl; // Store the local file path on the disc!
                } else {
                    runForPlayer(player, () -> MessageUtil.sendErrMsg(player, "download_failed"));
                    return;
                }
            }

            final String urlForLambda = streamUrl;
            final String finalUrlToSet = urlToSaveOnDisc;
            final File downloadedFile = localAudioFile;

            IMixerAudioPlayer.APM.loadItem(urlForLambda, new AudioLoadResultHandler() {
                @Override
                public void trackLoaded(AudioTrack audioTrack) {
                    AudioTrackInfo info = audioTrack.getInfo();
                    finishBurn(player, sourceSlot, expectedItem, info, finalUrlToSet, downloadedFile);
                }

                @Override
                public void playlistLoaded(AudioPlaylist audioPlaylist) {
                    AudioTrack selectedTrack = audioPlaylist.getSelectedTrack();
                    if (selectedTrack == null && !audioPlaylist.getTracks().isEmpty()) {
                        selectedTrack = audioPlaylist.getTracks().getFirst();
                    }
                    if (selectedTrack == null) {
                        cleanupFailedDownload(downloadedFile);
                        runForPlayer(player, () -> MessageUtil.sendErrMsg(player, "no_matches"));
                        return;
                    }
                    finishBurn(player, sourceSlot, expectedItem, selectedTrack.getInfo(), finalUrlToSet, downloadedFile);
                }

                @Override
                public void noMatches() {
                    cleanupFailedDownload(downloadedFile);
                    runForPlayer(player, () -> MessageUtil.sendErrMsg(player, "no_matches"));
                }

                @Override
                public void loadFailed(FriendlyException e) {
                    cleanupFailedDownload(downloadedFile);
                    runForPlayer(player, () -> MessageUtil.sendErrMsg(player, "loading_failed", e.getMessage()));
                }
            });
        });

        return Command.SINGLE_SUCCESS;
    }

    private void finishBurn(Player player, int sourceSlot, ItemStack expectedItem, AudioTrackInfo info,
                            String urlToSet, File downloadedFile) {
        if (!plugin.isEnabled()) {
            cleanupFailedDownload(downloadedFile);
            return;
        }
        runForPlayer(player, () -> {
            if (!player.isOnline()) {
                cleanupFailedDownload(downloadedFile);
                return;
            }

            ItemStack currentItem = player.getInventory().getItem(sourceSlot);
            if (currentItem == null || currentItem.getAmount() != expectedItem.getAmount()
                    || !currentItem.isSimilar(expectedItem)) {
                cleanupFailedDownload(downloadedFile);
                MessageUtil.sendErrMsg(player, "burn_item_changed");
                return;
            }

            MixerTrack track = new MixerTrack(info.title, info.author, info.uri, info.length, info.isStream);
            ItemStack burnedDisc = plugin.api().discs().createDisc(currentItem, new MixerDisc(urlToSet, track));
            player.getInventory().setItem(sourceSlot, burnedDisc);
            MessageUtil.sendMsg(player, "track_loaded", info.title);
        });
    }

    private void runForPlayer(Player player, Runnable action) {
        if (!plugin.isEnabled()) return;
        if (Bukkit.isOwnedByCurrentRegion(player)) {
            action.run();
        } else {
            plugin.scheduler().runFor(player, action, () -> { });
        }
    }

    private void cleanupFailedDownload(File file) {
        if (file != null && file.exists() && !file.delete()) {
            plugin.logDebug(Level.WARNING, "Failed to remove incomplete audio file: " + file.getAbsolutePath(), null);
        }
    }

    public void shutdown() {
        executorService.shutdownNow();
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
        if (!requireOwnedLocation(player, jukeboxLoc)) return 0;

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
        if (!requireOwnedLocation(player, jukeboxLoc)) return 0;

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
        if (location == null) {
            ctx.getSource().getSender().sendMessage(MM.deserialize("<red>Invalid location or not a player."));
            return 0;
        }
        if (!requireOwnedLocation(ctx.getSource().getSender(), location)) return 0;

        JsonObject obj = Utils.loadNbtData(location, "mixer_dsp");
        if (obj == null) {
            ctx.getSource().getSender().sendMessage(MM.deserialize("<red>No jukebox at location"));
            return 0;
        }

        Utils.saveNbtData(location, "mixer_dsp", new JsonObject());
        ctx.getSource().getSender().sendMessage(MM.deserialize("<green>DSP reset"));
        return Command.SINGLE_SUCCESS;
    }

    private int executeDspGain(CommandContext<CommandSourceStack> ctx) {
        Location location = getBukkitLocation(ctx, "jukebox");
        if (location == null || !requireOwnedLocation(ctx.getSource().getSender(), location)) return 0;
        double gain = ctx.getArgument("gain", Double.class);

        JsonObject obj = Utils.loadNbtData(location, "mixer_dsp");
        if (obj == null) {
            ctx.getSource().getSender().sendMessage(MM.deserialize("<red>No jukebox at location"));
            return 0;
        }

        JsonObject settings = new JsonObject();
        settings.addProperty("gain", gain);
        obj.add("gain", settings);
        Utils.saveNbtData(location, "mixer_dsp", obj);

        IMixerAudioPlayer player = MixerPlugin.getPlugin().playerHashMap().get(location);
        if (player != null) {
            player.updateVolume();
        }

        ctx.getSource().getSender().sendMessage(MM.deserialize("<green>Gain set to " + gain));
        return Command.SINGLE_SUCCESS;
    }

    private int executeDspHighPass(CommandContext<CommandSourceStack> ctx) {
        Location location = getBukkitLocation(ctx, "jukebox");
        if (location == null || !requireOwnedLocation(ctx.getSource().getSender(), location)) return 0;
        float frequency = ctx.getArgument("frequency", Float.class);

        JsonObject obj = Utils.loadNbtData(location, "mixer_dsp");
        if (obj == null) {
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
        if (location == null || !requireOwnedLocation(ctx.getSource().getSender(), location)) return 0;
        float frequency = ctx.getArgument("frequency", Float.class);

        JsonObject obj = Utils.loadNbtData(location, "mixer_dsp");
        if (obj == null) {
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
        if (location == null || !requireOwnedLocation(ctx.getSource().getSender(), location)) return 0;
        double maxFlangerLength = ctx.getArgument("maxFlangerLength", Double.class);
        double wet = ctx.getArgument("wet", Double.class);
        double lfoFrequency = ctx.getArgument("lfoFrequency", Double.class);

        JsonObject obj = Utils.loadNbtData(location, "mixer_dsp");
        if (obj == null) {
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

    // --- /mixer speaker ---
    private LiteralArgumentBuilder<CommandSourceStack> registerSpeakerCommand() {
        return Commands.literal("speaker")
                .requires(source -> source.getSender().hasPermission("mixer.command.speaker"))
                .executes(this::executeSpeaker);
    }

    private int executeSpeaker(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            MessageUtil.sendErrMsg(sender, "must_be_player");
            return 0;
        }

        runForPlayer(player, () -> givePortableSpeaker(player));
        return Command.SINGLE_SUCCESS;
    }

    private void givePortableSpeaker(Player player) {
        if (!MixerPlugin.getPlugin().isPortableSpeakerEnabled()) {
            MessageUtil.sendErrMsg(player, "feature_disabled");
            return;
        }

        String matName = MixerPlugin.getPlugin().getPortableSpeakerItemMaterial();
        Material mat = Material.getMaterial(matName);
        if (mat == null) {
            mat = Material.NOTE_BLOCK;
            MixerPlugin.getPlugin().logDebug(Level.WARNING, "Invalid material for portable speaker: " + matName + ". Using NOTE_BLOCK instead.", null);
        }

        ItemStack speaker = new ItemStack(mat);
        speaker.editMeta(meta -> {
            String name = MixerPlugin.getPlugin().getLocalizationManager().getMessage("portableSpeaker.portable_speaker_item_name");
            meta.displayName(MM.deserialize(name).decoration(TextDecoration.ITALIC, false));
            NamespacedKey key = new NamespacedKey(MixerPlugin.getPlugin(), "mixer_speaker");
            meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);

            // Generate unique ID for this speaker
            NamespacedKey idKey = new NamespacedKey(MixerPlugin.getPlugin(), "mixer_speaker_id");
            meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, UUID.randomUUID().toString());
        });

        player.getInventory().addItem(speaker).values().forEach(leftover ->
                player.getWorld().dropItemNaturally(player.getLocation(), leftover));
        String name = MixerPlugin.getPlugin().getLocalizationManager().getMessage("portableSpeaker.portable_speaker_item_name");
        MessageUtil.sendMsg(player, "speaker_received", name);
    }

    // --- /mixer cartridge ---
    private LiteralArgumentBuilder<CommandSourceStack> registerCartridgeCommand() {
        return Commands.literal("cartridge")
                .requires(source -> source.getSender().hasPermission("mixer.command.cartridge"))
                .executes(this::executeCartridge)
                .then(Commands.literal("rename")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(this::executeCartridgeRename)));
    }

    private int executeCartridge(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            MessageUtil.sendErrMsg(sender, "must_be_player");
            return 0;
        }

        runForPlayer(player, () -> givePlaylistCartridge(player));
        return Command.SINGLE_SUCCESS;
    }

    private void givePlaylistCartridge(Player player) {
        if (!plugin.arePlaylistCartridgesEnabled()) {
            MessageUtil.sendErrMsg(player, "feature_disabled");
            return;
        }

        ItemStack cartridge = plugin.getPlaylistCartridges().createCartridge();
        player.getInventory().addItem(cartridge).values().forEach(leftover ->
                player.getWorld().dropItemNaturally(player.getLocation(), leftover));
        MessageUtil.sendMsg(player, "cartridge_received");
    }

    private int executeCartridgeRename(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            MessageUtil.sendErrMsg(sender, "must_be_player");
            return 0;
        }

        String name = ctx.getArgument("name", String.class).strip();
        runForPlayer(player, () -> renamePlaylistCartridge(player, name));
        return Command.SINGLE_SUCCESS;
    }

    private void renamePlaylistCartridge(Player player, String name) {
        ItemStack item = player.getInventory().getItemInMainHand();
        var service = plugin.getPlaylistCartridges();
        UUID id = service.id(item).orElse(null);
        var playlist = service.read(item).orElse(null);
        if (id == null || playlist == null) {
            MessageUtil.sendErrMsg(player, "must_hold_cartridge");
            return;
        }

        if (name.isEmpty() || name.length() > 32) {
            MessageUtil.sendErrMsg(player, "invalid_cartridge_name");
            return;
        }
        if (!service.write(item, id, new me.andromedov.mixer.api.playlist.MixerPlaylist(
                playlist.version(), name, playlist.tracks()))) {
            MessageUtil.sendErrMsg(player, "cartridge_rename_failed");
            return;
        }
        MessageUtil.sendMsg(player, "cartridge_renamed", name);
    }

    // --- /mixer reload ---
    private LiteralArgumentBuilder<CommandSourceStack> registerReloadCommand() {
        return Commands.literal("reload")
                .requires(source -> source.getSender().hasPermission("mixer.command.reload"))
                .executes(this::executeReload);
    }

    private int executeReload(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        try {
            MessageUtil.reloadMessages();
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        plugin.reloadPluginConfig();
        MessageUtil.sendMsg(sender, "config_reloaded");

        return Command.SINGLE_SUCCESS;
    }


    private Location getBukkitLocation(CommandContext<CommandSourceStack> ctx, String argumentName) {
        CommandSender sender = ctx.getSource().getSender();
        org.bukkit.World world = null;

        if (sender instanceof Player player) {
            world = player.getWorld();
        }
        else if (sender instanceof org.bukkit.command.BlockCommandSender blockSender) {
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
        }
        catch (Exception e) {
            return null;
        }
    }

    private boolean requireOwnedLocation(CommandSender sender, Location location) {
        if (Bukkit.isOwnedByCurrentRegion(location)) return true;
        sender.sendMessage(MM.deserialize("<red>That block is outside the command sender's current Folia region.</red>"));
        return false;
    }
}
