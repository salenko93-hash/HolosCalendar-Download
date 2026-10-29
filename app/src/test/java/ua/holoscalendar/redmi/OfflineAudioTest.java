package ua.holoscalendar.redmi;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.*;
import static org.junit.Assert.*;

public class OfflineAudioTest {
    @Test public void convertsSignedPcmWithoutClippingOrChangingSampleCount() {
        short[] data=new short[16000];Arrays.fill(data,(short)1000);data[0]=Short.MIN_VALUE;data[1]=Short.MAX_VALUE;
        PcmAudio audio=new PcmAudio();audio.append(data,data.length);float[] actual=audio.samples();
        assertEquals(data.length,actual.length);assertEquals(-1f,actual[0],0f);assertTrue(actual[1]<1f);assertTrue(actual[1]>.999f);
    }
    @Test public void capsAudioAtThirtySecondsAndDiscardsAllOldSamplesOnClear() {
        PcmAudio audio=new PcmAudio();short[] block=new short[500000];Arrays.fill(block,(short)500);
        audio.append(block,block.length);assertTrue(audio.full());assertEquals(480000,audio.samples().length);
        audio.clear();assertEquals(0,audio.size());audio.append(new short[16000],16000);
        assertThrows(IllegalStateException.class,audio::samples);
    }
    @Test public void refusesSilentAndTooShortRecordings() {
        PcmAudio audio=new PcmAudio();audio.append(new short[15999],15999);assertThrows(IllegalStateException.class,audio::samples);
        audio.append(new short[]{0},1);assertThrows(IllegalStateException.class,audio::samples);
    }
    @Test public void validatesPcmReadBounds() {
        PcmAudio audio=new PcmAudio();assertThrows(IllegalArgumentException.class,()->audio.append(new short[2],3));
        assertThrows(IllegalArgumentException.class,()->audio.append(new short[2],-1));
    }
    @Test public void rejectsEnglishOnlyModelsAndUnknownContent() throws Exception {
        assertEquals("Whisper Tiny",SpeechModel.name(SpeechModel.TINY_SHA1));assertEquals("Whisper Base",SpeechModel.name(SpeechModel.BASE_SHA1));assertEquals("Whisper Base",SpeechModel.name(SpeechModel.CONVERTED_BASE_SHA1));
        assertThrows(IOException.class,()->SpeechModel.name("c78c86eb1a8faa21b369bcd33207cc90d64ae9df"));
        assertThrows(IOException.class,()->SpeechModel.copyAndVerify(new ByteArrayInputStream("not weights".getBytes()),null,new AtomicBoolean()));
    }
    @Test public void failedImportLeavesThePreviouslyInstalledFileUntouched() throws Exception {
        File directory=Files.createTempDirectory("speech-import-").toFile();File destination=new File(directory,"whisper.bin");
        byte[] original="previous model must survive".getBytes();Files.write(destination.toPath(),original);
        try {
            assertThrows(IOException.class,()->SpeechModel.install(new ByteArrayInputStream(new byte[8192]),destination,new AtomicBoolean()));
            assertArrayEquals(original,Files.readAllBytes(destination.toPath()));assertEquals(1,Objects.requireNonNull(directory.list()).length);
        } finally {destination.delete();directory.delete();}
    }
    @Test public void cancelledImportDoesNotReplaceTheOldModelOrLeavePartialFiles() throws Exception {
        File directory=Files.createTempDirectory("speech-cancel-").toFile();File destination=new File(directory,"whisper.bin");
        byte[] original={1,2,3};Files.write(destination.toPath(),original);
        try {
            assertThrows(CancellationException.class,()->SpeechModel.install(new ByteArrayInputStream(new byte[1024]),destination,new AtomicBoolean(true)));
            assertArrayEquals(original,Files.readAllBytes(destination.toPath()));assertEquals(1,Objects.requireNonNull(directory.list()).length);
        } finally {destination.delete();directory.delete();}
    }
    @Test public void rejectsOversizedInputBeforeItCanReplaceInstalledWeights() {
        InputStream enormous=new InputStream(){long left=SpeechModel.MAX_BYTES+1;
            @Override public int read(){return left-->0?0:-1;}
            @Override public int read(byte[] bytes,int offset,int length){if(left<=0)return -1;int n=(int)Math.min(left,length);Arrays.fill(bytes,offset,offset+n,(byte)0);left-=n;return n;}
        };
        assertThrows(IOException.class,()->SpeechModel.copyAndVerify(enormous,null,new AtomicBoolean()));
    }
}
