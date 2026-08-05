package me.andromedov.mixer.api.playback;

import net.kyori.adventure.text.Component;

import java.util.Objects;
import java.util.Optional;

public final class MixerPlaybackDecision {
    private static final MixerPlaybackDecision ALLOW = new MixerPlaybackDecision(true, null);

    private final boolean allowed;
    private final Component denialMessage;

    private MixerPlaybackDecision(boolean allowed, Component denialMessage) {
        this.allowed = allowed;
        this.denialMessage = denialMessage;
    }

    public static MixerPlaybackDecision allow() {
        return ALLOW;
    }

    public static MixerPlaybackDecision deny(Component message) {
        return new MixerPlaybackDecision(false, Objects.requireNonNull(message, "message"));
    }

    public boolean allowed() {
        return allowed;
    }

    public Optional<Component> denialMessage() {
        return Optional.ofNullable(denialMessage);
    }
}
