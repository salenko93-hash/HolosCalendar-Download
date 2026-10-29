package ua.holoscalendar.redmi;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.time.*;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ReminderStoreTest {
    private static final String DATABASE = "reminder-storage-test.db";
    private Context context;
    private final ZoneId zone = ZoneOffset.UTC;
    private final LocalDate day = LocalDate.of(2026,11,5);
    private final Instant morning = Instant.parse("2026-11-05T08:00:00Z");
    @Before public void prepare() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext(); context.deleteDatabase(DATABASE);
    }
    @After public void cleanup() { context.deleteDatabase(DATABASE); }
    private CalendarEvent event(String title, int hour) { return new CalendarEvent(title,day,LocalTime.of(hour,0),null,zone); }

    @Test public void migrationKeepsExistingMeetingsWithoutEnablingUnrequestedReminders() {
        try (SQLiteDatabase old = context.openOrCreateDatabase(DATABASE,Context.MODE_PRIVATE,null)) {
            old.execSQL("CREATE TABLE events (_id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, "
                    + "start_millis INTEGER NOT NULL, zone_id TEXT NOT NULL, duration_minutes INTEGER)");
            old.execSQL("INSERT INTO events(title,start_millis,zone_id,duration_minutes) VALUES(?,?,?,?)",
                    new Object[]{"Моя стара зустріч",event("Моя стара зустріч",12).startMillis(),"Z",45});
            old.setVersion(1);
        }
        try (EventStore store = new EventStore(context,DATABASE)) {
            SavedEvent saved = store.find(1);
            assertEquals("Моя стара зустріч",saved.title); assertEquals(Integer.valueOf(45),saved.durationMinutes);
            assertNull(saved.reminderMinutes); assertNull(store.nextReminder(morning));
            store.save(1,event("Оновлено",12),10);
            assertEquals(Integer.valueOf(10),store.find(1).reminderMinutes);
        }
    }
    @Test public void choosesTheNextReminderRatherThanTheNextMeeting() {
        try (EventStore store = new EventStore(context,DATABASE)) {
            store.save(0,event("Подія раніше",12),0);
            long earlierReminder = store.save(0,event("Нагадування раніше",13),120);
            assertEquals(earlierReminder,store.nextReminder(morning).id);
        }
    }
    @Test public void claimedReminderDoesNotRepeatAfterReopeningDatabase() {
        long id,planned;
        try (EventStore store = new EventStore(context,DATABASE)) {
            id=store.save(0,event("Нарада",12),10); planned=ReminderPolicy.plannedAt(store.find(id));
            assertNotNull(store.claimReminder(id,planned,Instant.ofEpochMilli(planned)));
            assertNull(store.claimReminder(id,planned,Instant.ofEpochMilli(planned+1000)));
        }
        try (EventStore store = new EventStore(context,DATABASE)) {
            assertNull(store.nextReminder(morning)); assertEquals(Long.valueOf(planned),store.find(id).reminderDeliveredFor);
        }
    }
    @Test public void movingMeetingRejectsStaleAlarmAndEnablesNewReminder() {
        try (EventStore store = new EventStore(context,DATABASE)) {
            long id=store.save(0,event("Нарада",12),10);
            long old=ReminderPolicy.plannedAt(store.find(id));
            store.claimReminder(id,old,Instant.ofEpochMilli(old));
            store.save(id,event("Перенесено",14),10);
            assertNull(store.find(id).reminderDeliveredFor);
            assertNull(store.claimReminder(id,old,Instant.ofEpochMilli(old)));
            assertEquals(id,store.nextReminder(morning).id);
        }
    }
    @Test public void renamingAlreadyAnnouncedMeetingDoesNotRepeatItsReminder() {
        try (EventStore store = new EventStore(context,DATABASE)) {
            long id=store.save(0,event("Нарада",12),15);
            long planned=ReminderPolicy.plannedAt(store.find(id));
            store.claimReminder(id,planned,Instant.ofEpochMilli(planned));
            store.save(id,event("Нова назва",12),15);
            assertEquals(Long.valueOf(planned),store.find(id).reminderDeliveredFor);
            assertNull(store.nextReminder(morning));
        }
    }
    @Test public void disablingOrDeletingReminderRemovesItFromQueue() {
        try (EventStore store = new EventStore(context,DATABASE)) {
            long id=store.save(0,event("Нарада",12),10); long planned=ReminderPolicy.plannedAt(store.find(id));
            store.save(id,event("Нарада",12),null);
            assertNull(store.nextReminder(morning)); assertNull(store.claimReminder(id,planned,Instant.ofEpochMilli(planned)));
            store.save(id,event("Нарада",12),10); store.delete(id);
            assertNull(store.nextReminder(morning)); assertNull(store.claimReminder(id,planned,Instant.ofEpochMilli(planned)));
        }
    }
    @Test public void failedNotificationCanReleaseClaimForRetry() {
        try (EventStore store = new EventStore(context,DATABASE)) {
            long id=store.save(0,event("Нарада",12),10); long planned=ReminderPolicy.plannedAt(store.find(id));
            store.claimReminder(id,planned,Instant.ofEpochMilli(planned)); store.releaseReminder(id,planned);
            assertNotNull(store.claimReminder(id,planned,Instant.ofEpochMilli(planned+1000)));
        }
    }
    @Test public void expiredMeetingIsNotReplayedAfterBoot() {
        try (EventStore store = new EventStore(context,DATABASE)) {
            store.save(0,event("Стара нарада",9),0);
            long future=store.save(0,event("Нова нарада",12),30);
            assertEquals(future,store.nextReminder(Instant.parse("2026-11-05T09:06:00Z")).id);
        }
    }
}
