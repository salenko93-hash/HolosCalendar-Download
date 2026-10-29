package ua.holoscalendar.redmi;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.*;
import java.nio.file.Files;
import java.util.concurrent.atomic.*;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class OfflineSpeechFlowTest {
    private Context context;private boolean originalMode;
    @Before public void before(){context=InstrumentationRegistry.getInstrumentation().getTargetContext();originalMode=SpeechSettings.offline(context);}
    @After public void after(){SpeechSettings.offline(context,originalMode);}
    @Test public void localVoiceButtonOpensThePrivateLocalScreenRatherThanTheGoogleIntent() {
        SpeechSettings.offline(context,true);AtomicReference<Intent> opened=new AtomicReference<>();
        Instrumentation.ActivityMonitor monitor=new Instrumentation.ActivityMonitor(){
            @Override public Instrumentation.ActivityResult onStartActivity(Intent intent){
                if(intent.getComponent()!=null&&intent.getComponent().getClassName().equals(OfflineVoiceActivity.class.getName())) {
                    opened.set(intent);return new Instrumentation.ActivityResult(Activity.RESULT_CANCELED,null);
                }
                return null;
            }
        };
        Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();instrumentation.addMonitor(monitor);
        try(ActivityScenario<EventEditorActivity> screen=ActivityScenario.launch(EventEditorActivity.class)) {
            screen.onActivity(activity->activity.findViewById(R.id.voice).performClick());
            assertNotNull(opened.get());assertEquals(OfflineVoiceActivity.class.getName(),opened.get().getComponent().getClassName());
        } finally {instrumentation.removeMonitor(monitor);}
    }
    @Test public void localUkrainianResultFillsTheExistingParserWithoutInventingDuration() {
        try(ActivityScenario<EventEditorActivity> screen=ActivityScenario.launch(EventEditorActivity.class)) {
            screen.recreate();screen.onActivity(activity->{
                activity.onActivityResult(EventEditorActivity.LOCAL_VOICE,Activity.RESULT_OK,new Intent().putExtra(OfflineVoiceActivity.TEXT,"Завтра о другій дня Зустріч з Оленою"));
                assertEquals("Зустріч з Оленою",((TextView)activity.findViewById(R.id.name)).getText().toString());
                assertEquals(activity.getString(R.string.time_value,"14:00"),((TextView)activity.findViewById(R.id.time)).getText().toString());
                assertEquals(activity.getString(R.string.duration_unspecified),((TextView)activity.findViewById(R.id.duration)).getText().toString());
            });
        }
    }
    @Test public void cancellingLocalSpeechPreservesTheEditedEvent() {
        try(ActivityScenario<EventEditorActivity> screen=ActivityScenario.launch(EventEditorActivity.class)) {
            screen.onActivity(activity->{
                ((TextView)activity.findViewById(R.id.raw)).setText("Завтра о 15 Нарада на пів години");activity.findViewById(R.id.parse).performClick();
                ((TextView)activity.findViewById(R.id.name)).setText("Моя назва");
                activity.onActivityResult(EventEditorActivity.LOCAL_VOICE,Activity.RESULT_CANCELED,null);
                assertEquals("Моя назва",((TextView)activity.findViewById(R.id.name)).getText().toString());
                assertEquals(activity.getString(R.string.duration_minutes,30),((TextView)activity.findViewById(R.id.duration)).getText().toString());
            });
        }
    }
    @Test public void oversizedLocalResultCannotSilentlyTruncateOrReplaceTheForm() {
        try(ActivityScenario<EventEditorActivity> screen=ActivityScenario.launch(EventEditorActivity.class)) {
            screen.onActivity(activity->{
                ((TextView)activity.findViewById(R.id.name)).setText("Залишити назву");
                activity.onActivityResult(EventEditorActivity.LOCAL_VOICE,Activity.RESULT_OK,new Intent().putExtra(OfflineVoiceActivity.TEXT,new String(new char[1001]).replace('\0','а')));
                assertEquals("Залишити назву",((TextView)activity.findViewById(R.id.name)).getText().toString());
                assertEquals(activity.getString(R.string.voice_too_long),((TextView)activity.findViewById(R.id.status)).getText().toString());
            });
        }
    }
    @Test public void invalidModelImportFinishesWithoutDamagingTheExistingModel() throws Exception {
        File model=SpeechSettings.model(context);boolean existed=model.exists();long size=model.length(),modified=model.lastModified();
        File bad=File.createTempFile("bad-speech-",".bin",context.getCacheDir());Files.write(bad.toPath(),new byte[]{1,2,3,4});
        try(ActivityScenario<OfflineVoiceActivity> screen=ActivityScenario.launch(OfflineVoiceActivity.class)) {
            screen.onActivity(activity->{
                activity.onActivityResult(201,Activity.RESULT_OK,new Intent().setData(Uri.fromFile(bad)));
                assertTrue((activity.getWindow().getAttributes().flags&android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)!=0);
            });
            AtomicBoolean rejected=new AtomicBoolean();long until=System.currentTimeMillis()+30000;
            while(!rejected.get()&&System.currentTimeMillis()<until) {
                screen.onActivity(activity->rejected.set(((TextView)activity.findViewById(R.id.offline_status)).getText().toString().equals(activity.getString(R.string.offline_invalid_model))));
                if(!rejected.get())Thread.sleep(100);
            }
            assertTrue("Invalid model should be rejected",rejected.get());
            screen.onActivity(activity->assertEquals(0,activity.getWindow().getAttributes().flags&android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON));
            assertEquals(existed,model.exists());assertEquals(size,model.length());assertEquals(modified,model.lastModified());
        } finally {bad.delete();}
    }
    @Test public void realJniLibraryLoadsAndRejectsInvalidWeightsGracefully() throws Exception {
        File file=File.createTempFile("invalid-whisper-",".bin",context.getCacheDir());
        try {assertThrows(IOException.class,()->{try(WhisperEngine ignored=new WhisperEngine(file)){fail("Invalid model accepted");}});}
        finally {file.delete();}
    }
}
