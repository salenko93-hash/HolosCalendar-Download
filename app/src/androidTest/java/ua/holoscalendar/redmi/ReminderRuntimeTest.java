package ua.holoscalendar.redmi;

import android.Manifest;
import android.app.Activity;
import android.app.Instrumentation;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.os.SystemClock;
import android.service.notification.StatusBarNotification;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Uses the real Android notification service; only test-created events/notifications are removed. */
@RunWith(AndroidJUnit4.class)
public class ReminderRuntimeTest {
    private Context context;
    private NotificationManager notifications;
    private final List<Long> created = new ArrayList<>();
    @Before public void prepare() throws Exception {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        if (Build.VERSION.SDK_INT >= 33) InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .grantRuntimePermission(context.getPackageName(), Manifest.permission.POST_NOTIFICATIONS);
        notifications = context.getSystemService(NotificationManager.class);
        ReminderAccess.ensureChannel(context);
        ReminderScheduler.refresh(context).get(20,TimeUnit.SECONDS);
    }
    @After public void cleanup() throws Exception {
        try (EventStore store = new EventStore(context)) {
            for (long id : created) { store.delete(id); ReminderScheduler.cancelNotification(context,id); }
        }
        ReminderScheduler.refresh(context).get(20,TimeUnit.SECONDS);
    }
    private SavedEvent seed(Instant start, int minutes) {
        ZoneId zone = ZoneId.systemDefault(); ZonedDateTime local = start.atZone(zone);
        try (EventStore store = new EventStore(context)) {
            long id = store.save(0,new CalendarEvent("Нагадування " + UUID.randomUUID(),local.toLocalDate(),local.toLocalTime(),null,zone,start),minutes);
            created.add(id); return store.find(id);
        }
    }
    private StatusBarNotification active(long id) {
        for (StatusBarNotification notification : notifications.getActiveNotifications())
            if (ReminderScheduler.tag(id).equals(notification.getTag())) return notification;
        return null;
    }
    private StatusBarNotification awaitNotification(long id, boolean present) {
        long until=SystemClock.uptimeMillis()+10_000;
        StatusBarNotification found=active(id);
        while ((found!=null)!=present && SystemClock.uptimeMillis()<until) {
            SystemClock.sleep(200); found=active(id);
        }
        return found;
    }
    @Test public void postsTheMeetingOnceAndDoesNotInventAnEndTime() {
        SavedEvent event = seed(Instant.now().plusSeconds(7200),10);
        long planned = ReminderPolicy.plannedAt(event); Instant due = Instant.ofEpochMilli(planned+1);
        assertTrue(ReminderScheduler.deliver(context,event.id,planned,due));
        StatusBarNotification posted = awaitNotification(event.id,true); assertNotNull(posted);
        assertEquals(event.title,posted.getNotification().extras.getString(Notification.EXTRA_TITLE));
        assertFalse(posted.getNotification().extras.getString(Notification.EXTRA_TEXT).contains("Тривалість"));
        assertEquals(Notification.VISIBILITY_PRIVATE,posted.getNotification().visibility);
        assertNotNull(posted.getNotification().publicVersion);
        assertFalse(ReminderScheduler.deliver(context,event.id,planned,due));
        ReminderScheduler.cancelNotification(context,event.id); assertNull(awaitNotification(event.id,false));
    }
    @Test public void staleOrDeletedMeetingDoesNotPostANotification() {
        SavedEvent event = seed(Instant.now().plusSeconds(7200),10);
        long old = ReminderPolicy.plannedAt(event);
        try (EventStore store = new EventStore(context)) {
            ZonedDateTime local=event.start.atZone(event.zone);
            store.save(event.id,new CalendarEvent(event.title,local.toLocalDate(),local.toLocalTime(),null,event.zone,event.start),5);
            assertFalse(ReminderScheduler.deliver(context,event.id,old,Instant.ofEpochMilli(old+1)));
            store.delete(event.id);
        }
        assertFalse(ReminderScheduler.deliver(context,event.id,old,Instant.ofEpochMilli(old+1)));
        assertNull(active(event.id));
    }
    @Test public void eachNotificationHasItsOwnMeetingIntent() {
        SavedEvent first=seed(Instant.now().plusSeconds(7200),10);
        SavedEvent second=seed(Instant.now().plusSeconds(7300),10);
        assertNotEquals(ReminderScheduler.notification(context,first).contentIntent,
                ReminderScheduler.notification(context,second).contentIntent);
    }
    @Test public void tappingNotificationIntentOpensTheMatchingMeeting() throws Exception {
        SavedEvent event=seed(Instant.now().plusSeconds(7200),10);
        Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
        Instrumentation.ActivityMonitor monitor=instrumentation.addMonitor(EventEditorActivity.class.getName(),null,false);
        try (ActivityScenario<CalendarActivity> home=ActivityScenario.launch(CalendarActivity.class)) {
            ReminderScheduler.notification(context,event).contentIntent.send();
            Activity opened=monitor.waitForActivityWithTimeout(60_000);
            assertNotNull(opened);
            assertEquals(event.id,opened.getIntent().getLongExtra(EventEditorActivity.EVENT_ID,0));
            instrumentation.runOnMainSync(opened::finish);
        } finally { instrumentation.removeMonitor(monitor); }
    }
    @Test public void realSystemAlarmDeliversWithoutAnOpenEditor() {
        // A timing assertion is meaningful only when the OS permits exact scheduling.
        Assume.assumeTrue(ReminderAccess.exactAllowed(context));
        SavedEvent event=seed(Instant.now().plusSeconds(20),0);
        ReminderScheduler.scheduleNext(context,Instant.now());
        long until=SystemClock.uptimeMillis()+60_000;
        while (active(event.id)==null && SystemClock.uptimeMillis()<until) SystemClock.sleep(500);
        assertNotNull("AlarmManager must deliver to the manifest receiver",active(event.id));
    }
}
