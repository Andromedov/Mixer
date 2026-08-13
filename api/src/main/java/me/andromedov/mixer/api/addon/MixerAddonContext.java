package me.andromedov.mixer.api.addon;

import me.andromedov.mixer.api.MixerApi;
import me.andromedov.mixer.api.source.MixerAudioSourceResolver;
import me.andromedov.mixer.api.source.MixerAudioSourceResolverRegistration;
import me.andromedov.mixer.api.gui.PortableSpeakerMenuProvider;
import me.andromedov.mixer.api.gui.PortableSpeakerMenuProviderRegistration;
import me.andromedov.mixer.api.playback.MixerPlaybackPolicy;
import me.andromedov.mixer.api.playback.MixerPlaybackPolicyRegistration;
import org.bukkit.plugin.Plugin;

public interface MixerAddonContext {
    MixerApi api();
    Plugin owner();

    MixerAudioSourceResolverRegistration registerSourceResolver(MixerAudioSourceResolver resolver);

    MixerPlaybackPolicyRegistration registerPlaybackPolicy(MixerPlaybackPolicy policy);

    PortableSpeakerMenuProviderRegistration registerPortableSpeakerMenuProvider(
            PortableSpeakerMenuProvider provider);
}
