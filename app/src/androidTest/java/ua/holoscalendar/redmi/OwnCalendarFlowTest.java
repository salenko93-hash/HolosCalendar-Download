package ua.holoscalendar.redmi;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.speech.RecognizerIntent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Creates and removes only records owned by these tests; never clears the user's database. */
@RunWith(AndroidJUnit4.class)
public class OwnCalendarFlowTest {
    private Context context;
    private final List<Long> created = new ArrayList<>();
    @Before public void prepare() { context = InstrumentationRegistry.getInstrumentation().getTargetContext(); }
    @After public void cleanup() throws Exception {
        try (EventStore store = new EventStore(context)) {
            for (long id : created) { store.delete(id); ReminderScheduler.cancelNotification(context,id); }
        }
        ReminderScheduler.refresh(context).get(20,java.util.concurrent.TimeUnit.SECONDS);
    }
    private long seed(String title, Integer duration) {
        try (EventStore store = new EventStore(context)) {
            long id = store.save(0,new CalendarEvent(title,LocalDate.now().plusDays(7),LocalTime.NOON,duration,ZoneId.systemDefault()));
            created.add(id); return id;
        }
    }
    private String unique() { return "Тест " + UUID.randomUUID().toString(); }
    @Test public void existingLauncherShortcutOpensTheOwnCalendar() throws Exception {
        android.content.pm.ActivityInfo launcher = context.getPackageManager().getActivityInfo(
                new android.content.ComponentName(context.getPackageName(),context.getPackageName()+".MainActivity"),0);
        assertEquals(CalendarActivity.class.getName(),launcher.targetActivity);
        assertTrue(launcher.exported);
    }
    private Instrumentation.ActivityMonitor blockCalendarLaunch() {
        Instrumentation.ActivityMonitor monitor = new Instrumentation.ActivityMonitor() {
            @Override public Instrumentation.ActivityResult onStartActivity(Intent intent) {
                if (intent.getComponent() != null && intent.getComponent().getClassName().equals(CalendarActivity.class.getName()))
                    return new Instrumentation.ActivityResult(Activity.RESULT_CANCELED,null);
                return null;
            }
        };
        InstrumentationRegistry.getInstrumentation().addMonitor(monitor); return monitor;
    }
    @Test public void savingAndSavingAgainAfterRecreationDoesNotDuplicateTheMeeting() {
        String title = unique();
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Instrumentation.ActivityMonitor monitor = blockCalendarLaunch();
        try (ActivityScenario<EventEditorActivity> scenario = ActivityScenario.launch(EventEditorActivity.class)) {
            scenario.onActivity(activity -> {
                Intent speech = new Intent().putStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS,
                        new ArrayList<>(Collections.singletonList("Через годину " + title)));
                activity.onActivityResult(100,Activity.RESULT_OK,speech);
                activity.findViewById(R.id.save_local).performClick();
            });
            long id;
            try (EventStore store = new EventStore(context)) {
                List<SavedEvent> events = store.between(Instant.now().minusSeconds(60),Instant.now().plusSeconds(86400));
                List<SavedEvent> matching = new ArrayList<>();
                for (SavedEvent event : events) if (event.title.equals(title)) matching.add(event);
                assertEquals(1,matching.size()); id = matching.get(0).id; created.add(id);
                assertNull(matching.get(0).durationMinutes);
            }
            scenario.recreate();
            scenario.onActivity(activity -> {
                ((EditText)activity.findViewById(R.id.name)).setText(title + " оновлено");
                activity.findViewById(R.id.save_local).performClick();
            });
            try (EventStore store = new EventStore(context)) {
                assertEquals(title + " оновлено",store.find(id).title);
                assertNull(store.find(id).durationMinutes);
                long count = store.between(Instant.now().minusSeconds(60),Instant.now().plusSeconds(86400))
                        .stream().filter(event -> event.title.startsWith(title)).count();
                assertEquals(1,count);
            }
        } finally { instrumentation.removeMonitor(monitor); }
    }
    @Test public void editingLoadsStoredFieldsAndUpdatesTheSameRecord() {
        String title = unique(); long id = seed(title,45);
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Instrumentation.ActivityMonitor monitor = blockCalendarLaunch();
        Intent edit = new Intent(context,EventEditorActivity.class).putExtra(EventEditorActivity.EVENT_ID,id);
        try (ActivityScenario<EventEditorActivity> scenario = ActivityScenario.launch(edit)) {
            scenario.onActivity(activity -> {
                assertEquals(title,((TextView)activity.findViewById(R.id.name)).getText().toString());
                assertEquals(activity.getString(R.string.duration_minutes,45),((TextView)activity.findViewById(R.id.duration)).getText().toString());
                ((EditText)activity.findViewById(R.id.name)).setText(title + " нова назва");
                activity.findViewById(R.id.save_local).performClick();
            });
            try (EventStore store = new EventStore(context)) {
                assertEquals(title + " нова назва",store.find(id).title);
                assertEquals(Integer.valueOf(45),store.find(id).durationMinutes);
            }
        } finally { instrumentation.removeMonitor(monitor); }
    }
    @Test public void reminderSettingSurvivesRecreationAndUpdatingTheMeeting() {
        String title=unique(); long id=seed(title,null);
        try (EventStore store=new EventStore(context)) {
            SavedEvent old=store.find(id); ZonedDateTime local=old.start.atZone(old.zone);
            store.save(id,new CalendarEvent(old.title,local.toLocalDate(),local.toLocalTime(),null,old.zone,old.start),15);
        }
        Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
        Instrumentation.ActivityMonitor monitor=blockCalendarLaunch();
        try (ActivityScenario<EventEditorActivity> scenario=ActivityScenario.launch(new Intent(context,EventEditorActivity.class)
                .putExtra(EventEditorActivity.EVENT_ID,id))) {
            scenario.recreate();
            scenario.onActivity(activity -> {
                assertEquals(activity.getString(R.string.reminder_label,ReminderAccess.label(activity,15)),
                        ((TextView)activity.findViewById(R.id.reminder)).getText().toString());
                activity.findViewById(R.id.save_local).performClick();
            });
            try (EventStore store=new EventStore(context)) { assertEquals(Integer.valueOf(15),store.find(id).reminderMinutes); }
        } finally { instrumentation.removeMonitor(monitor); }
    }
    @Test public void missingRecordIsNotSilentlyCreatedAgain() {
        long id = seed(unique(),null);
        try (EventStore store = new EventStore(context)) { store.delete(id); }
        try (ActivityScenario<EventEditorActivity> scenario = ActivityScenario.launch(new Intent(context,EventEditorActivity.class).putExtra(EventEditorActivity.EVENT_ID,id))) {
            scenario.onActivity(activity -> assertFalse(activity.findViewById(R.id.save_local).isEnabled()));
            scenario.recreate();
            scenario.onActivity(activity -> assertFalse(activity.findViewById(R.id.save_local).isEnabled()));
        }
    }
    private boolean containsText(View view, String text) {
        if (view instanceof TextView && ((TextView)view).getText().toString().equals(text)) return true;
        if (view instanceof ViewGroup) for (int i=0;i<((ViewGroup)view).getChildCount();i++)
            if (containsText(((ViewGroup)view).getChildAt(i),text)) return true;
        return false;
    }
    @Test public void localCalendarShowsTheStoredMeetingAndRestoresItsMonth() {
        String title = unique(); seed(title,null);
        LocalDate day = LocalDate.now().plusDays(7);
        Intent intent = new Intent(context,CalendarActivity.class).putExtra(CalendarActivity.SELECTED_DATE,day.toString());
        try (ActivityScenario<CalendarActivity> scenario = ActivityScenario.launch(intent)) {
            AtomicBoolean found = new AtomicBoolean();
            long until = SystemClock.uptimeMillis() + 60_000;
            while (!found.get() && SystemClock.uptimeMillis() < until) {
                scenario.onActivity(activity -> found.set(containsText(activity.findViewById(R.id.calendar_events),title)));
                if (!found.get()) SystemClock.sleep(500);
            }
            assertTrue("Stored event must be rendered on the selected day",found.get());
            String[] nextMonth = new String[1];
            scenario.onActivity(activity -> {
                activity.findViewById(R.id.month_next).performClick();
                nextMonth[0]=((TextView)activity.findViewById(R.id.month_title)).getText().toString();
            });
            scenario.recreate();
            scenario.onActivity(activity -> assertEquals(nextMonth[0],((TextView)activity.findViewById(R.id.month_title)).getText().toString()));
        }
    }
}
