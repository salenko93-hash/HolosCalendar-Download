package ua.holoscalendar.redmi;

import java.io.*;
import java.nio.charset.StandardCharsets;

/** CPU-only JNI engine; each utterance gets a fresh context and explicitly uses Ukrainian. */
final class WhisperEngine implements AutoCloseable {
    static { System.loadLibrary("holos_whisper"); }
    private long handle;
    WhisperEngine(File model) throws IOException {
        handle = nativeCreate(model.getAbsolutePath());
        if (handle == 0) throw new IOException("Cannot load speech model");
    }
    String transcribe(float[] samples) throws IOException { return transcribe(samples,120000); }
    String transcribe(float[] samples,int timeoutMillis) throws IOException {
        if(timeoutMillis<1||timeoutMillis>120000) throw new IllegalArgumentException("Invalid speech timeout");
        long current;
        synchronized (this) { current = handle; }
        if (current == 0) throw new IOException("Speech session is closed");
        byte[] text = nativeTranscribe(current, samples, Math.min(4, Runtime.getRuntime().availableProcessors()),timeoutMillis);
        if (text == null) throw new IOException("Empty speech response");
        return new String(text, StandardCharsets.UTF_8).trim();
    }
    synchronized void cancel() { if (handle != 0) nativeCancel(handle); }
    @Override public synchronized void close() {
        if (handle != 0) { nativeClose(handle); handle = 0; }
    }
    private static native long nativeCreate(String modelPath) throws IOException;
    private static native byte[] nativeTranscribe(long handle, float[] samples, int threads,int timeoutMillis) throws IOException;
    private static native void nativeCancel(long handle);
    private static native void nativeClose(long handle);
}
