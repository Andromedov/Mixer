package me.andromedov.mixer.core.portable;

import me.andromedov.mixer.core.MixerPlugin;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;

/** Owns the persistent state stored directly on a portable speaker item. */
public final class PortableSpeakerService {
    private final NamespacedKey markerKey;
    private final NamespacedKey idKey;
    private final NamespacedKey mediaKey;

    public PortableSpeakerService(MixerPlugin plugin) {
        markerKey = new NamespacedKey(plugin, "mixer_speaker");
        idKey = new NamespacedKey(plugin, "mixer_speaker_id");
        mediaKey = new NamespacedKey(plugin, "mixer_speaker_media");
    }

    public boolean isSpeaker(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer()
                .has(markerKey, PersistentDataType.BYTE);
    }

    public Optional<UUID> id(ItemStack item) {
        if (!isSpeaker(item)) return Optional.empty();
        String value = item.getItemMeta().getPersistentDataContainer()
                .get(idKey, PersistentDataType.STRING);
        try {
            return Optional.of(UUID.fromString(value));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    public UUID ensureId(ItemStack item) {
        UUID existing = id(item).orElse(null);
        if (existing != null) return existing;
        UUID generated = UUID.randomUUID();
        item.editMeta(meta -> meta.getPersistentDataContainer()
                .set(idKey, PersistentDataType.STRING, generated.toString()));
        return generated;
    }

    public boolean store(Player player, UUID speakerId, ItemStack media) {
        ItemStack speaker = find(player, speakerId).orElse(null);
        if (speaker == null || media == null || media.getType().isAir()) return false;
        ItemStack stored = media.clone();
        stored.setAmount(1);
        speaker.editMeta(meta -> meta.getPersistentDataContainer()
                .set(mediaKey, PersistentDataType.BYTE_ARRAY, stored.serializeAsBytes()));
        return true;
    }

    public Optional<ItemStack> peek(Player player, UUID speakerId) {
        return find(player, speakerId).flatMap(this::peek);
    }

    public Optional<ItemStack> take(Player player, UUID speakerId) {
        return find(player, speakerId).flatMap(this::take);
    }

    public Optional<ItemStack> take(ItemStack speaker) {
        Optional<ItemStack> media = peek(speaker);
        if (media.isPresent()) {
            speaker.editMeta(meta -> meta.getPersistentDataContainer().remove(mediaKey));
        }
        return media;
    }

    public boolean eject(Player player, UUID speakerId) {
        if (!Bukkit.isOwnedByCurrentRegion(player)) return false;
        return take(player, speakerId).map(media -> {
            give(player, media);
            return true;
        }).orElse(false);
    }

    public boolean eject(Player player, ItemStack speaker) {
        if (!Bukkit.isOwnedByCurrentRegion(player)) return false;
        return take(speaker).map(media -> {
            give(player, media);
            return true;
        }).orElse(false);
    }

    private Optional<ItemStack> peek(ItemStack speaker) {
        if (!isSpeaker(speaker)) return Optional.empty();
        byte[] bytes = speaker.getItemMeta().getPersistentDataContainer()
                .get(mediaKey, PersistentDataType.BYTE_ARRAY);
        if (bytes == null || bytes.length == 0) return Optional.empty();
        try {
            return Optional.of(ItemStack.deserializeBytes(bytes));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    private Optional<ItemStack> find(Player player, UUID speakerId) {
        if (speakerId == null) return Optional.empty();
        for (ItemStack item : player.getInventory().getContents()) {
            if (speakerId.equals(id(item).orElse(null))) return Optional.of(item);
        }
        return Optional.empty();
    }

    private static void give(Player player, ItemStack item) {
        HashMap<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
        leftovers.values().forEach(leftover ->
                player.getWorld().dropItemNaturally(player.getLocation(), leftover));
    }
}
