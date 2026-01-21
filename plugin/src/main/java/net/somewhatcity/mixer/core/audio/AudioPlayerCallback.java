package net.somewhatcity.mixer.core.audio;

import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;

public interface AudioPlayerCallback {
    public void onLoaded(AudioTrackInfo info);
}
