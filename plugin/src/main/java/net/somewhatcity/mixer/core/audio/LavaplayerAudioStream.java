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

import net.somewhatcity.mixer.core.MixerPlugin;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.IOException;
import java.io.InputStream;

public class LavaplayerAudioStream extends AudioInputStream {
    // Circular Buffer implementation within the stream
    private final byte[] buffer;
    private final int capacity;
    private int head = 0; // Write position
    private int tail = 0; // Read position
    private int count = 0; // Available bytes

    private volatile boolean closed = false;
    private final Object lock = new Object();

    public LavaplayerAudioStream(AudioFormat format) {
        super(new InputStream() {
            @Override
            public int read() { return -1; } // Stub, we override read(byte[], int, int)
        }, format, AudioSystem.NOT_SPECIFIED);

        int configBufferSize = MixerPlugin.getPlugin().getAudioBufferSize();
        this.capacity = Math.max(configBufferSize * 50, 65536);
        this.buffer = new byte[capacity];
    }

    public void appendData(byte[] newData) {
        if (closed) return;

        synchronized (lock) {
            int len = newData.length;
            int freeSpace = capacity - count;

            if (len > freeSpace) {
                len = freeSpace;
                if (len == 0) return; // Buffer full completely
            }

            // Write logic for Circular Buffer
            int firstChunk = Math.min(len, capacity - head);
            System.arraycopy(newData, 0, buffer, head, firstChunk);

            int secondChunk = len - firstChunk;
            if (secondChunk > 0) {
                System.arraycopy(newData, firstChunk, buffer, 0, secondChunk);
            }

            head = (head + len) % capacity;
            count += len;

            lock.notifyAll();
        }
    }

    @Override
    public int read() throws IOException {
        byte[] b = new byte[1];
        if (read(b, 0, 1) == -1) return -1;
        return b[0] & 0xFF;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        synchronized (lock) {
            while (count == 0) {
                if (closed) return -1;
                try {
                    lock.wait(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return -1;
                }
            }

            int bytesRead = Math.min(len, count);

            int firstChunk = Math.min(bytesRead, capacity - tail);
            System.arraycopy(buffer, tail, b, off, firstChunk);

            int secondChunk = bytesRead - firstChunk;
            if (secondChunk > 0) {
                System.arraycopy(buffer, 0, b, off + firstChunk, secondChunk);
            }

            tail = (tail + bytesRead) % capacity;
            count -= bytesRead;

            return bytesRead;
        }
    }

    @Override
    public int available() throws IOException {
        synchronized (lock) {
            return count;
        }
    }

    @Override
    public void close() throws IOException {
        synchronized (lock) {
            closed = true;
            lock.notifyAll();
        }
    }
}