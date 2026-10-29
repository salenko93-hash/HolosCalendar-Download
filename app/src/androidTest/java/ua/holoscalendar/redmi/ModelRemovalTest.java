package ua.holoscalendar.redmi;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ModelRemovalTest {
    private Context context() { return InstrumentationRegistry.getInstrumentation().getTargetContext(); }
    @Test public void removesLegacyModelAndPartialImportsWithoutDeletingEvents() throws Exception {
        Context context=context();
        Path directory=context.getFilesDir().toPath().resolve("speech");
        Files.createDirectories(directory);
        Files.write(directory.resolve("whisper.bin"),new byte[]{1,2,3});
        Files.write(directory.resolve("import-old.part"),new byte[]{4,5});
        context.getSharedPreferences("speech",Context.MODE_PRIVATE).edit().putBoolean("offline",true).commit();
        long id;
        ZoneId zone=ZoneOffset.UTC;
        try(EventStore store=new EventStore(context)) {
            id=store.save(0,new CalendarEvent("Очищення "+UUID.randomUUID(),LocalDate.of(2027,1,1),LocalTime.NOON,null,zone),17);
        }
        try {
            assertTrue(LegacySpeechCleanup.clean(context));
            assertFalse(Files.exists(directory));
            assertTrue(context.getSharedPreferences("speech",Context.MODE_PRIVATE).getAll().isEmpty());
            try(EventStore store=new EventStore(context)) {
                assertNotNull(store.find(id));assertEquals(Integer.valueOf(17),store.find(id).reminderMinutes);
            }
        } finally {try(EventStore store=new EventStore(context)){store.delete(id);}}
    }
    @Test public void cleanupDoesNotFollowSymlinks() throws Exception {
        Context context=context();Path files=context.getFilesDir().toPath();
        Path outside=Files.createTempFile(files,"kept-event-",".txt");
        byte[] expected={7,8,9};Files.write(outside,expected);
        try {
            Path directory=files.resolve("speech");Files.createDirectories(directory);
            Files.createSymbolicLink(directory.resolve("old-link"),outside);
            assertTrue(LegacySpeechCleanup.clean(context));
            assertArrayEquals(expected,Files.readAllBytes(outside));
        } finally {Files.deleteIfExists(outside);}
    }
    @Test public void cleanupCanRunRepeatedly() {
        assertTrue(LegacySpeechCleanup.clean(context()));
        assertTrue(LegacySpeechCleanup.clean(context()));
    }
    @Test public void installedAppHasNoMicrophoneNetworkPermissionOrSpeechActivity() throws Exception {
        Context context=context();
        PackageInfo info=context.getPackageManager().getPackageInfo(context.getPackageName(),PackageManager.GET_PERMISSIONS|PackageManager.GET_ACTIVITIES);
        List<String> permissions=info.requestedPermissions==null?Collections.emptyList():Arrays.asList(info.requestedPermissions);
        assertFalse(permissions.contains("android.permission.RECORD_AUDIO"));
        assertFalse(permissions.contains("android.permission.INTERNET"));
        for(android.content.pm.ActivityInfo activity:info.activities) assertFalse(activity.name.contains("OfflineVoice"));
    }
}
