package ua.holoscalendar.redmi;

import android.content.Context;
import android.os.SystemClock;
import android.util.Log;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Opt-in, real weights and PCM fixture required; never silently skips a missing model. */
@RunWith(AndroidJUnit4.class)
public class OfflineModelDeviceTest {
    @Test public void importsRealWeightsAndRecognizesUkrainianAudioOnTheAndroidCpu() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        File staged=new File(context.getFilesDir(),"speech-test/ggml-base.bin");
        File wave=new File(context.getFilesDir(),"speech-test/uk-natural.wav");
        assertTrue("Stage real model before opting in",staged.isFile());assertTrue("Stage Ukrainian PCM fixture",wave.isFile());
        File directory=new File(context.getCacheDir(),"real-speech-test");assertTrue(directory.isDirectory()||directory.mkdirs());
        File imported=new File(directory,"whisper.bin");
        try {
            try(InputStream input=new FileInputStream(staged)) {
                assertEquals("Whisper Base",SpeechModel.install(input,imported,new AtomicBoolean()));
            }
            float[] samples=readWave(wave);long began=SystemClock.elapsedRealtime();String text;
            try(WhisperEngine engine=new WhisperEngine(imported)){text=engine.transcribe(samples);}
            Log.i("OfflineDeviceCheck","Ukrainian CPU inference completed in "+(SystemClock.elapsedRealtime()-began)+" ms");
            String normalized=text.toLowerCase(Locale.ROOT);
            assertTrue("Expected Ukrainian reference word in: "+text,normalized.contains("веселка"));
            assertTrue("Expected reference word in: "+text,normalized.contains("атмосфер"));
            assertTrue(text.length()<=1000);
        } finally {imported.delete();directory.delete();}
    }
    private static float[] readWave(File file) throws IOException {
        try(DataInputStream input=new DataInputStream(new FileInputStream(file))) {
            if(!tag(input).equals("RIFF"))throw new IOException("Expected RIFF");input.readInt();
            if(!tag(input).equals("WAVE"))throw new IOException("Expected WAVE");boolean pcm=false;
            while(true) {
                String chunk=tag(input);int bytes=Integer.reverseBytes(input.readInt());
                if(bytes<0||bytes>2_000_000)throw new IOException("Invalid WAV chunk");
                if(chunk.equals("fmt ")) {
                    if(bytes<16)throw new IOException("Short format");
                    int format=Short.reverseBytes(input.readShort())&65535,channels=Short.reverseBytes(input.readShort())&65535;
                    int rate=Integer.reverseBytes(input.readInt());input.readInt();input.readShort();int bits=Short.reverseBytes(input.readShort())&65535;
                    pcm=format==1&&channels==1&&rate==16000&&bits==16;skip(input,bytes-16+(bytes&1));
                } else if(chunk.equals("data")) {
                    if(!pcm||bytes%2!=0||bytes<32000||bytes>960000)throw new IOException("Expected bounded 16kHz mono PCM16");
                    float[] samples=new float[bytes/2];for(int i=0;i<samples.length;i++)samples[i]=Short.reverseBytes(input.readShort())/32768f;
                    return samples;
                } else skip(input,bytes+(bytes&1));
            }
        }
    }
    private static String tag(DataInputStream input) throws IOException {byte[] tag=new byte[4];input.readFully(tag);return new String(tag,java.nio.charset.StandardCharsets.US_ASCII);}
    private static void skip(DataInputStream input,int length) throws IOException {while(length>0){int skipped=input.skipBytes(length);if(skipped==0)throw new EOFException();length-=skipped;}}
}
