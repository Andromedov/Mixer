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

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.*;

public class LavaplayerAudioStream extends AudioInputStream {
    private volatile PipedOutputStream outputStream;
    private volatile PipedInputStream inputStream;
    private volatile boolean closed = false;

    public LavaplayerAudioStream(AudioFormat format) throws IOException {
        super(null, format, AudioSystem.NOT_SPECIFIED);
        outputStream = new PipedOutputStream();
        inputStream = new PipedInputStream(outputStream);
    }

    public synchronized void appendData(byte[] newData) throws IOException {
        if (closed || outputStream == null) {
            return; // Ignore
        }

        try {
            outputStream.write(newData);
            outputStream.flush();
        } catch (IOException e) {
            if (!e.getMessage().contains("Write end dead")) {
                throw e;
            }
            System.err.println("Audio stream write end dead - stream likely closed");
        }
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (closed || inputStream == null) {
            return -1;
        }
        return inputStream.read(b, off, len);
    }

    @Override
    public void close() throws IOException {
        closed = true;
        try {
            if (outputStream != null) {
                outputStream.close();
            }
        } catch (IOException e) {
            // Ignore
        }

        try {
            if (inputStream != null) {
                inputStream.close();
            }
        } catch (IOException e) {
            // Ignore
        }
    }
}
