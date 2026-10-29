package ua.holoscalendar.redmi;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Real JNI, real weights, public Ukrainian reference audio; no Android emulation. */
public final class HostWhisperSmoke {
    public static void main(String[] args) throws Exception {
        if(args.length!=2)throw new IllegalArgumentException("model.bin 16k-mono.wav");
        File model=new File(args[0]);SpeechModel.verify(model,new AtomicBoolean());
        float[] samples=wave(new File(args[1]));
        try(WhisperEngine engine=new WhisperEngine(model)) {
            String text=engine.transcribe(samples);String value=text.toLowerCase(Locale.ROOT);
            if(!value.contains("веселка")||!value.contains("атмосфер"))throw new AssertionError("Unexpected reference transcription: "+text);
            System.out.println("PASS actual JNI Ukrainian transcription: "+text);
        }
        try(WhisperEngine engine=new WhisperEngine(model)) {
            long start=System.nanoTime();
            try{engine.transcribe(samples,1);throw new AssertionError("Timeout did not fire");}
            catch(InterruptedIOException expected){if(System.nanoTime()-start>TimeUnit.SECONDS.toNanos(10))throw new AssertionError("Timeout was not responsive");}
            System.out.println("PASS JNI inference timeout");
        }
        ExecutorService worker=Executors.newSingleThreadExecutor();
        try(WhisperEngine engine=new WhisperEngine(model)) {
            CountDownLatch started=new CountDownLatch(1);
            Future<?> result=worker.submit(()->{
                started.countDown();
                try{engine.transcribe(samples);throw new AssertionError("Cancellation did not fire");}
                catch(IOException expected){return;}
            });
            if(!started.await(10,TimeUnit.SECONDS))throw new AssertionError("Worker did not start");
            Thread.sleep(50);engine.cancel();engine.close();result.get(10,TimeUnit.SECONDS);
            System.out.println("PASS JNI concurrent cancellation and close");
        } finally{worker.shutdownNow();}
    }
    private static float[] wave(File file) throws IOException {
        try(DataInputStream input=new DataInputStream(new FileInputStream(file))) {
            if(!tag(input).equals("RIFF"))throw new IOException("RIFF");input.readInt();if(!tag(input).equals("WAVE"))throw new IOException("WAVE");boolean pcm=false;
            while(true){String chunk=tag(input);int bytes=Integer.reverseBytes(input.readInt());if(bytes<0||bytes>2000000)throw new IOException("chunk");
                if(chunk.equals("fmt ")){if(bytes<16)throw new IOException("format");int format=Short.reverseBytes(input.readShort())&65535,channels=Short.reverseBytes(input.readShort())&65535,rate=Integer.reverseBytes(input.readInt());input.readInt();input.readShort();int bits=Short.reverseBytes(input.readShort())&65535;pcm=format==1&&channels==1&&rate==16000&&bits==16;skip(input,bytes-16+(bytes&1));}
                else if(chunk.equals("data")){if(!pcm||bytes%2!=0||bytes<32000||bytes>960000)throw new IOException("PCM");float[] audio=new float[bytes/2];for(int i=0;i<audio.length;i++)audio[i]=Short.reverseBytes(input.readShort())/32768f;return audio;}
                else skip(input,bytes+(bytes&1));
            }
        }
    }
    private static String tag(DataInputStream input)throws IOException{byte[] value=new byte[4];input.readFully(value);return new String(value,java.nio.charset.StandardCharsets.US_ASCII);}
    private static void skip(DataInputStream input,int bytes)throws IOException{while(bytes>0){int n=input.skipBytes(bytes);if(n==0)throw new EOFException();bytes-=n;}}
}
