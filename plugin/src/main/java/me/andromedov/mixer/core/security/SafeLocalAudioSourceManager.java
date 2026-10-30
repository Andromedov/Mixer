package me.andromedov.mixer.core.security;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.local.LocalAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;

import java.io.DataInput;
import java.io.IOException;

/** Local loader restricted to files created in Mixer's own audio directory. */
public final class SafeLocalAudioSourceManager extends LocalAudioSourceManager {
    private final AudioSourcePolicy policy;

    public SafeLocalAudioSourceManager(AudioSourcePolicy policy) {
        this.policy = policy;
    }

    @Override
    public AudioItem loadItem(AudioPlayerManager manager, AudioReference reference) {
        try {
            String safePath = policy.validateLocalReference(reference.identifier);
            return super.loadItem(manager, new AudioReference(safePath, reference.title));
        } catch (AudioSourcePolicyException exception) {
            return null;
        }
    }

    @Override
    public AudioTrack decodeTrack(AudioTrackInfo trackInfo, DataInput input) throws IOException {
        try {
            policy.validateLocalReference(trackInfo.identifier);
            return super.decodeTrack(trackInfo, input);
        } catch (AudioSourcePolicyException exception) {
            return null;
        }
    }
}
