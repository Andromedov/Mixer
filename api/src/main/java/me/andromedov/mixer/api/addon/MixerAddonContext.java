package me.andromedov.mixer.api.addon;

import me.andromedov.mixer.api.MixerApi;
import me.andromedov.mixer.api.source.MixerAudioSourceResolver;
import me.andromedov.mixer.api.source.MixerAudioSourceResolverRegistration;
import org.bukkit.plugin.Plugin;

public interface MixerAddonContext {
    MixerApi api();
    Plugin owner();

    MixerAudioSourceResolverRegistration registerSourceResolver(MixerAudioSourceResolver resolver);
}
