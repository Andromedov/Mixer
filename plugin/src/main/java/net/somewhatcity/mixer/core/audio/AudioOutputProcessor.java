/*
 * Copyright (c) 2024 mrmrmystery
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice (including the next paragraph) shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package net.somewhatcity.mixer.core.audio;

import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.AudioProcessor;
import net.somewhatcity.mixer.core.MixerPlugin;

public class AudioOutputProcessor implements AudioProcessor {
    private DataListener listener;
    private float volumeMultiplier;

    public AudioOutputProcessor(DataListener listener) {
        this.listener = listener;
        this.volumeMultiplier = MixerPlugin.getPlugin().getVolumeMultiplier();
    }

    public AudioOutputProcessor(DataListener listener, float customVolumeMultiplier) {
        this.listener = listener;
        this.volumeMultiplier = customVolumeMultiplier;
    }

    @Override
    public boolean process(AudioEvent audioEvent) {
        byte[] originalData = audioEvent.getByteBuffer();
        if (volumeMultiplier != 1.0f) {
            byte[] processedData = applyVolumeToAudioData(originalData, volumeMultiplier);
            listener.onData(processedData);
        } else {
            listener.onData(originalData);
        }

        return true;
    }

    @Override
    public void processingFinished() {
    }

    private byte[] applyVolumeToAudioData(byte[] audioData, float volumeMultiplier) {
        byte[] result = new byte[audioData.length];

        for (int i = 0; i < audioData.length - 1; i += 2) {
            short sample = (short) ((audioData[i + 1] << 8) | (audioData[i] & 0xFF));

            int newSample = Math.round(sample * volumeMultiplier);

            if (newSample > Short.MAX_VALUE) {
                newSample = Short.MAX_VALUE;
            } else if (newSample < Short.MIN_VALUE) {
                newSample = Short.MIN_VALUE;
            }

            result[i] = (byte) (newSample & 0xFF);
            result[i + 1] = (byte) ((newSample >> 8) & 0xFF);
        }

        return result;
    }

    interface DataListener {
        void onData(byte[] data);
    }
}