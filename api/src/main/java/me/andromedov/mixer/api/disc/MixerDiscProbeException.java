package me.andromedov.mixer.api.disc;

/** Raised when Mixer cannot resolve or inspect a submitted audio source. */
public class MixerDiscProbeException extends RuntimeException {
    public MixerDiscProbeException(String message) {
        super(message);
    }

    public MixerDiscProbeException(String message, Throwable cause) {
        super(message, cause);
    }
}
