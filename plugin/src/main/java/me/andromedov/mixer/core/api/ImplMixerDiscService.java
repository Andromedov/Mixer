package me.andromedov.mixer.core.api;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import me.andromedov.mixer.api.MixerTrack;
import me.andromedov.mixer.api.disc.MixerDisc;
import me.andromedov.mixer.api.disc.MixerDiscProbeException;
import me.andromedov.mixer.api.disc.MixerDiscService;
import me.andromedov.mixer.api.source.MixerAudioSourceResolutionException;
import me.andromedov.mixer.core.MixerPlugin;
import me.andromedov.mixer.core.audio.AbstractMixerAudioPlayer;
import me.andromedov.mixer.core.util.Utils;
import me.andromedov.mixer.core.util.MixerScheduler;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class ImplMixerDiscService implements MixerDiscService {
    private final MixerPlugin plugin;
    private final ImplMixerAudioSourceRegistry sources;
    private final ExecutorService probeExecutor = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "Mixer-Disc-Probe");
        thread.setDaemon(true);
        return thread;
    });
    private final NamespacedKey sourceKey;
    private final NamespacedKey titleKey;
    private final NamespacedKey authorKey;
    private final NamespacedKey uriKey;
    private final NamespacedKey durationKey;
    private final NamespacedKey streamKey;

    ImplMixerDiscService(MixerPlugin plugin, ImplMixerAudioSourceRegistry sources) {
        this.plugin = plugin;
        this.sources = sources;
        this.sourceKey = new NamespacedKey(plugin, "mixer_data");
        this.titleKey = new NamespacedKey(plugin, "mixer_title");
        this.authorKey = new NamespacedKey(plugin, "mixer_author");
        this.uriKey = new NamespacedKey(plugin, "mixer_uri");
        this.durationKey = new NamespacedKey(plugin, "mixer_duration");
        this.streamKey = new NamespacedKey(plugin, "mixer_stream");
    }

    @Override
    public CompletionStage<MixerDisc> probe(String source) {
        Objects.requireNonNull(source, "source");
        if (source.isBlank()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("source must not be blank"));
        }

        String stableSource = source.trim();
        return CompletableFuture.supplyAsync(() -> resolveForProbe(stableSource), probeExecutor)
                .thenCompose(this::loadTrack)
                .thenApply(track -> new MixerDisc(stableSource, track));
    }

    @Override
    public ItemStack createDisc(ItemStack template, MixerDisc disc) {
        requireMainThread("create a Mixer disc");
        Objects.requireNonNull(template, "template");
        Objects.requireNonNull(disc, "disc");
        if (template.getType() == Material.AIR) {
            throw new IllegalArgumentException("template must not be air");
        }

        ItemStack result = template.clone();
        result.editMeta(meta -> {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(sourceKey, PersistentDataType.STRING, disc.source());

            disc.track().ifPresent(track -> {
                meta.displayName(Component.text(track.author() + " - " + track.title())
                        .decoration(TextDecoration.ITALIC, false));
                pdc.set(titleKey, PersistentDataType.STRING, track.title());
                pdc.set(authorKey, PersistentDataType.STRING, track.author());
                pdc.set(uriKey, PersistentDataType.STRING, track.uri());
                pdc.set(durationKey, PersistentDataType.LONG, track.durationMillis());
                pdc.set(streamKey, PersistentDataType.BYTE, track.stream() ? (byte) 1 : (byte) 0);
            });

            meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        });
        // Mixer discs are inserted by PlayerInteractListener and played through
        // Mixer audio. A vanilla jukebox component would start a second song.
        result.unsetData(DataComponentTypes.JUKEBOX_PLAYABLE);
        return result;
    }

    @Override
    public Optional<MixerDisc> readDisc(ItemStack item) {
        requireMainThread("read a Mixer disc");
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return Optional.empty();
        }

        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        String source = pdc.get(sourceKey, PersistentDataType.STRING);
        if (source == null || source.isBlank()) return Optional.empty();

        String title = pdc.get(titleKey, PersistentDataType.STRING);
        String author = pdc.get(authorKey, PersistentDataType.STRING);
        String uri = pdc.get(uriKey, PersistentDataType.STRING);
        Long duration = pdc.get(durationKey, PersistentDataType.LONG);
        Byte stream = pdc.get(streamKey, PersistentDataType.BYTE);
        if (title == null || author == null || uri == null || duration == null || stream == null) {
            return Optional.of(MixerDisc.sourceOnly(source));
        }

        return Optional.of(new MixerDisc(source,
                new MixerTrack(title, author, uri, duration, stream != 0)));
    }

    @Override
    public boolean isMixerDisc(ItemStack item) {
        return readDisc(item).isPresent();
    }

    void shutdown() {
        probeExecutor.shutdownNow();
    }

    private String resolveForProbe(String stableSource) {
        String resolved;
        try {
            resolved = sources.resolve(stableSource);
        } catch (MixerAudioSourceResolutionException exception) {
            throw new MixerDiscProbeException("Failed to resolve audio source", exception);
        }

        if (resolved.startsWith("file://")) {
            File file = new File(resolved.substring(7));
            if (!file.isFile()) throw new MixerDiscProbeException("Local audio file does not exist");
            return file.getAbsolutePath();
        }

        if (resolved.startsWith("cobalt://") || resolved.startsWith("cobalt:")) {
            String rawUrl = resolved.replaceFirst("^cobalt:(//)?", "");
            if (!rawUrl.startsWith("http://") && !rawUrl.startsWith("https://")) {
                rawUrl = "https://" + rawUrl;
            }
            String cobaltUrl = Utils.requestCobaltMediaUrl(rawUrl);
            if (cobaltUrl == null || cobaltUrl.isBlank()) {
                throw new MixerDiscProbeException("Cobalt could not resolve the submitted source");
            }
            return cobaltUrl;
        }
        if (resolved.startsWith("https://youtube.com") || resolved.startsWith("https://www.youtube.com")) {
            String cobaltUrl = Utils.requestCobaltMediaUrl(resolved);
            if (cobaltUrl != null && !cobaltUrl.isBlank()) return cobaltUrl;
        }
        return resolved;
    }

    private CompletionStage<MixerTrack> loadTrack(String resolvedSource) {
        CompletableFuture<MixerTrack> future = new CompletableFuture<>();
        AbstractMixerAudioPlayer.APM.loadItem(resolvedSource, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack audioTrack) {
                future.complete(toApiTrack(audioTrack.getInfo()));
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                AudioTrack selected = playlist.getSelectedTrack();
                if (selected == null && !playlist.getTracks().isEmpty()) {
                    selected = playlist.getTracks().getFirst();
                }
                if (selected == null) {
                    future.completeExceptionally(new MixerDiscProbeException("Playlist contains no tracks"));
                } else {
                    future.complete(toApiTrack(selected.getInfo()));
                }
            }

            @Override
            public void noMatches() {
                future.completeExceptionally(new MixerDiscProbeException("No tracks matched the submitted source"));
            }

            @Override
            public void loadFailed(FriendlyException exception) {
                future.completeExceptionally(new MixerDiscProbeException(
                        "Failed to load the submitted source: " + exception.getMessage(), exception));
            }
        });
        return future;
    }

    private static MixerTrack toApiTrack(AudioTrackInfo info) {
        return new MixerTrack(info.title, info.author, info.uri, info.length, info.isStream);
    }

    private static void requireMainThread(String action) {
        MixerScheduler.requireTickThread(action);
    }
}
