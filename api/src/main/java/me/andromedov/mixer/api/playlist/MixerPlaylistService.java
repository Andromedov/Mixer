package me.andromedov.mixer.api.playlist;

import org.bukkit.inventory.ItemStack;

import java.util.Optional;
import java.util.UUID;

/** Creates and reads portable Music Cartridge items. All item methods require a server tick thread. */
public interface MixerPlaylistService {
    ItemStack createCartridge();

    boolean isCartridge(ItemStack item);

    Optional<UUID> id(ItemStack item);

    Optional<MixerPlaylist> read(ItemStack item);

    /** Updates a cartridge without modifying its identity. */
    boolean write(ItemStack item, UUID cartridgeId, MixerPlaylist playlist);

    int maxTracks();
}
