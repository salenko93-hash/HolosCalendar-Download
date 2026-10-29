package ua.holoscalendar.redmi;

import java.util.Arrays;

/** Bounded mono PCM16 at 16 kHz, held in RAM only. */
final class PcmAudio {
    static final int RATE = 16000;
    static final int MAX_SECONDS = 30;
    static final int MAX_SAMPLES = RATE * MAX_SECONDS;
    private final short[] buffer = new short[MAX_SAMPLES];
    private int count;
    int size() { return count; }
    boolean full() { return count == buffer.length; }
    void append(short[] source, int length) {
        if (length < 0 || length > source.length) throw new IllegalArgumentException("Invalid PCM length");
        int keep = Math.min(length, buffer.length - count);
        System.arraycopy(source, 0, buffer, count, keep);
        count += keep;
    }
    float[] samples() {
        if (count < RATE) throw new IllegalStateException("Recording is too short");
        double energy = 0;
        for (int i = 0; i < count; i++) energy += (double) buffer[i] * buffer[i];
        if (Math.sqrt(energy / count) < 32) throw new IllegalStateException("No audible speech");
        float[] result = new float[count];
        for (int i = 0; i < count; i++) result[i] = buffer[i] / 32768.0f;
        return result;
    }
    void clear() { Arrays.fill(buffer, (short) 0); count = 0; }
}
