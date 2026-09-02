package me.andromedov.mixer.core.security;

public final class AudioSourcePolicyException extends IllegalArgumentException {
    public AudioSourcePolicyException(String message) {
        super(message);
    }

    public AudioSourcePolicyException(String message, Throwable cause) {
        super(message, cause);
    }
}
