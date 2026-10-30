package me.andromedov.mixer.api.playlist;

import me.andromedov.mixer.api.MixerTrack;
import me.andromedov.mixer.api.disc.MixerDisc;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Objects;

/** A stable audio source and its display metadata as stored in a Mixer playlist. */
public record MixerPlaylistTrack(
        String source,
        String title,
        String author,
        String uri,
        long durationMillis,
        boolean stream,
        String iconMaterial,
        Integer iconCustomModelData,
        String iconItemModel
) {
    public MixerPlaylistTrack {
        source = Objects.requireNonNull(source, "source");
        if (source.isBlank()) throw new IllegalArgumentException("source must not be blank");
        title = Objects.requireNonNullElse(title, "Unknown track");
        author = Objects.requireNonNullElse(author, "Unknown artist");
        uri = Objects.requireNonNullElse(uri, source);
        iconMaterial = Objects.requireNonNullElse(iconMaterial, "MUSIC_DISC_13");
    }

    public MixerPlaylistTrack(String source, String title, String author, String uri,
                              long durationMillis, boolean stream) {
        this(source, title, author, uri, durationMillis, stream, "MUSIC_DISC_13", null, null);
    }

    public static MixerPlaylistTrack fromDisc(MixerDisc disc) {
        Objects.requireNonNull(disc, "disc");
        return disc.track()
                .map(track -> fromTrack(disc.source(), track))
                .orElseGet(() -> new MixerPlaylistTrack(
                        disc.source(), "Unknown track", "Unknown artist", disc.source(), 0L, false));
    }

    /** Captures the visual model of the disc for playlist GUI rendering. */
    public static MixerPlaylistTrack fromDisc(MixerDisc disc, ItemStack discItem) {
        MixerPlaylistTrack track = fromDisc(disc);
        if (discItem == null || discItem.getType().isAir()) return track;
        ItemMeta meta = discItem.getItemMeta();
        Integer customModelData = meta != null && meta.hasCustomModelData()
                ? meta.getCustomModelData() : null;
        String itemModel = meta != null && meta.hasItemModel() && meta.getItemModel() != null
                ? meta.getItemModel().toString() : null;
        return new MixerPlaylistTrack(track.source(), track.title(), track.author(), track.uri(),
                track.durationMillis(), track.stream(), discItem.getType().getKey().toString(),
                customModelData, itemModel);
    }

    private static MixerPlaylistTrack fromTrack(String source, MixerTrack track) {
        return new MixerPlaylistTrack(source, track.title(), track.author(), track.uri(),
                track.durationMillis(), track.stream());
    }
}
