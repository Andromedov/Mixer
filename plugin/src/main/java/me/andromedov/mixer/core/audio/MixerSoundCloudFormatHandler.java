package me.andromedov.mixer.core.audio;

import com.sedmelluq.discord.lavaplayer.source.soundcloud.DefaultSoundCloudFormatHandler;
import com.sedmelluq.discord.lavaplayer.source.soundcloud.SoundCloudFormatHandler;
import com.sedmelluq.discord.lavaplayer.source.soundcloud.SoundCloudM3uInfo;
import com.sedmelluq.discord.lavaplayer.source.soundcloud.SoundCloudTrackFormat;

import java.util.List;

/**
 * Avoids SoundCloud's legacy HLS lookup URLs when a direct progressive MP3
 * transcoding is available. Those HLS URLs may be present in track metadata but
 * return 404 only when Lavaplayer starts the track.
 */
public final class MixerSoundCloudFormatHandler implements SoundCloudFormatHandler {
    private static final String PROGRESSIVE = "progressive";
    private static final String MPEG = "audio/mpeg";

    private final DefaultSoundCloudFormatHandler fallback = new DefaultSoundCloudFormatHandler();

    @Override
    public SoundCloudTrackFormat chooseBestFormat(List<SoundCloudTrackFormat> formats) {
        return formats.stream()
                .filter(format -> PROGRESSIVE.equals(format.getProtocol()))
                .filter(format -> MPEG.equals(format.getMimeType()))
                .findFirst()
                .orElseGet(() -> fallback.chooseBestFormat(formats));
    }

    @Override
    public String buildFormatIdentifier(SoundCloudTrackFormat format) {
        return fallback.buildFormatIdentifier(format);
    }

    @Override
    public SoundCloudM3uInfo getM3uInfo(String identifier) {
        return fallback.getM3uInfo(identifier);
    }

    @Override
    public String getMp3LookupUrl(String identifier) {
        return fallback.getMp3LookupUrl(identifier);
    }
}
