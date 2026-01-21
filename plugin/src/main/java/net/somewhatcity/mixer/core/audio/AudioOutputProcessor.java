package net.somewhatcity.mixer.core.audio;

import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.AudioProcessor;

public class AudioOutputProcessor implements AudioProcessor {
    private final DataListener listener;

    public AudioOutputProcessor(DataListener listener) {
        this.listener = listener;
    }

    @Override
    public boolean process(AudioEvent audioEvent) {
        listener.onData(audioEvent.getByteBuffer());
        return true;
    }

    @Override
    public void processingFinished() {
        // Called when audio processing is finished
    }

    interface DataListener {
        void onData(byte[] data);
    }
}