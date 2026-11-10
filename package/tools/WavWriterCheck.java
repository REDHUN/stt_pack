package com.redhun.my_stt_recorder;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.Arrays;

/** No Android/JUnit dependency: checks the actual production WAV writer. */
public final class WavWriterCheck {
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        File directory = new File(args[0]);
        directory.mkdirs();
        File target = new File(directory, "sample.wav");
        byte[] pcm = new byte[32000];
        // Exact deterministic PCM byte pattern: catches accidental data/header shifts.
        for (int i = 0; i < pcm.length; i++) pcm[i] = (byte) (i * 17);
        WavWriter writer = new WavWriter(target, 16000);
        writer.write(Arrays.copyOfRange(pcm, 0, 640), 640);
        writer.write(Arrays.copyOfRange(pcm, 640, pcm.length), pcm.length - 640);
        require(writer.durationMs() == 1000, "sample duration");
        writer.close();
        writer.close();
        byte[] bytes = Files.readAllBytes(target.toPath());
        ByteBuffer header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        require(bytes.length == 32044, "file size");
        require(new String(bytes, 0, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("RIFF"), "RIFF magic");
        require(new String(bytes, 8, 8, java.nio.charset.StandardCharsets.US_ASCII).equals("WAVEfmt "), "WAVE fmt magic");
        require(header.getInt(4) == 32036, "RIFF size");
        require(header.getShort(20) == 1 && header.getShort(22) == 1, "PCM mono");
        require(header.getInt(24) == 16000 && header.getInt(28) == 32000, "rates");
        require(header.getShort(32) == 2 && header.getShort(34) == 16, "sample alignment");
        require(header.getInt(40) == pcm.length, "data size");
        require(Arrays.equals(pcm, Arrays.copyOfRange(bytes, 44, bytes.length)), "unaltered audio payload");
        try { writer.write(pcm, 2); throw new AssertionError("accepted write after close"); }
        catch (java.io.IOException expected) {}
        WavWriter empty = new WavWriter(new File(directory, "empty.wav"), 16000);
        try { empty.write(new byte[3], 3); throw new AssertionError("accepted incomplete PCM sample"); }
        catch (IllegalArgumentException expected) {}
        empty.close();
        require(new File(directory, "empty.wav").length() == 44, "empty WAV header");
        // Reusing an existing destination must truncate stale bytes.
        WavWriter truncate = new WavWriter(target, 16000);
        truncate.write(new byte[320], 320);
        truncate.close();
        require(target.length() == 364, "truncation");
        System.out.println("PASS: WAV header, PCM bytes, duration, close, empty file, alignment and truncation");
    }
}
