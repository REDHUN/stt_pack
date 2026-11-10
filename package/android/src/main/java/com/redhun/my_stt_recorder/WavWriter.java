package com.redhun.my_stt_recorder;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Single-worker PCM16 mono WAV writer. Never buffers an entire recording. */
final class WavWriter implements Closeable {
    private final RandomAccessFile file;
    private final int sampleRate;
    private long dataBytes;
    private boolean closed;

    WavWriter(File destination, int sampleRate) throws IOException {
        if (sampleRate <= 0) throw new IllegalArgumentException("Invalid sample rate");
        this.sampleRate = sampleRate;
        file = new RandomAccessFile(destination, "rw");
        try {
            file.setLength(0);
            file.write(new byte[44]);
        } catch (IOException error) {
            file.close();
            throw error;
        }
    }

    void write(byte[] bytes, int count) throws IOException {
        if (closed) throw new IOException("WAV already closed");
        if (count < 0 || count > bytes.length || (count & 1) != 0) {
            throw new IllegalArgumentException("Expected complete PCM16 samples");
        }
        if (dataBytes + count > 0xffffffffL - 36) {
            throw new IOException("WAV size limit reached");
        }
        file.write(bytes, 0, count);
        dataBytes += count;
    }

    long durationMs() { return dataBytes * 1000L / (sampleRate * 2L); }

    @Override public void close() throws IOException {
        if (closed) return;
        closed = true;
        try {
            // Truncate a possible partially written failed chunk to complete samples.
            file.setLength(44 + dataBytes);
            ByteBuffer h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
            h.put(new byte[] {'R','I','F','F'}).putInt((int)(36 + dataBytes));
            h.put(new byte[] {'W','A','V','E','f','m','t',' '}).putInt(16);
            h.putShort((short)1).putShort((short)1).putInt(sampleRate);
            h.putInt(sampleRate * 2).putShort((short)2).putShort((short)16);
            h.put(new byte[] {'d','a','t','a'}).putInt((int)dataBytes);
            file.seek(0);
            file.write(h.array());
        } finally { file.close(); }
    }
}
