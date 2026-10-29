package ua.holoscalendar.redmi;

import android.annotation.SuppressLint;
import android.media.*;
import java.io.IOException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;

final class MicrophoneRecorder {
    private MicrophoneRecorder() { }
    @SuppressLint("MissingPermission") // The foreground Activity checks RECORD_AUDIO before starting the worker.
    static float[] record(AtomicBoolean finish, AtomicBoolean cancelled, Runnable started) throws IOException, InterruptedException {
        int minimum = AudioRecord.getMinBufferSize(PcmAudio.RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (minimum <= 0) throw new IOException("Microphone format is unavailable");
        AudioRecord microphone = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, PcmAudio.RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, Math.max(minimum * 2, 4096));
        PcmAudio audio = new PcmAudio();
        try {
            if (microphone.getState() != AudioRecord.STATE_INITIALIZED) throw new IOException("Microphone is unavailable");
            if (cancelled.get()) throw new CancellationException();
            microphone.startRecording();
            if (microphone.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) throw new IOException("Microphone did not start");
            started.run();
            short[] buffer = new short[1600];
            while (!finish.get() && !cancelled.get() && !audio.full()) {
                int count = microphone.read(buffer, 0, buffer.length, AudioRecord.READ_NON_BLOCKING);
                if (count < 0) throw new IOException("Microphone read failed");
                if (count > 0) audio.append(buffer, count);
                else Thread.sleep(10);
            }
            if (cancelled.get()) throw new CancellationException();
            return audio.samples();
        } finally {
            try { if (microphone.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) microphone.stop(); }
            finally { microphone.release(); audio.clear(); }
        }
    }
}
