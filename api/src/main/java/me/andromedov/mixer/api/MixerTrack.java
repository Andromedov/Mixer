package me.andromedov.mixer.api;

public record MixerTrack(
        String title,
        String author,
        String uri,
        long durationMillis,
        boolean stream
) { }
