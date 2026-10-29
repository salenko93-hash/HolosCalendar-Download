package ua.holoscalendar.redmi;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.time.*;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class SeriesStoreTest {
    private static final String DATABASE="series-test.db";
    private Context context;private final ZoneId zone=ZoneId.of("Europe/Kiev");
    @Before public void before(){context=InstrumentationRegistry.getInstrumentation().getTargetContext();context.deleteDatabase(DATABASE);}
    @After public void after(){context.deleteDatabase(DATABASE);}
    private CalendarEvent daily(String title) {return new CalendarEvent(title,LocalDate.of(2026,10,26),LocalTime.of(9,0),null,zone,null,false,null,"D:1");}
    @Test public void migrationFromVersionTwoKeepsTheMeetingAndReminder() {
        try(SQLiteDatabase old=context.openOrCreateDatabase(DATABASE,0,null)) {
            old.execSQL("CREATE TABLE events (_id INTEGER PRIMARY KEY AUTOINCREMENT,title TEXT NOT NULL,start_millis INTEGER NOT NULL,zone_id TEXT NOT NULL,duration_minutes INTEGER,reminder_minutes INTEGER,reminder_delivered_for INTEGER)");
            old.execSQL("INSERT INTO events(title,start_millis,zone_id,reminder_minutes,reminder_delivered_for) VALUES(?,?,?,?,?)",new Object[]{"Стара подія",daily("Стара подія").startMillis(),zone.getId(),10,123L});old.setVersion(2);
        }
        try(EventStore store=new EventStore(context,DATABASE)) {
            SavedEvent event=store.find(1);assertEquals("Стара подія",event.title);assertEquals(Integer.valueOf(10),event.reminderMinutes);
            assertEquals(Long.valueOf(123),event.reminderDeliveredFor);assertFalse(event.allDay);assertEquals("",event.recurrence);
        }
    }
    @Test public void oneSeriesRecordProducesFutureOccurrencesAndDeletionRemovesThemAll() {
        Instant a=LocalDate.of(2026,11,1).atStartOfDay(zone).toInstant(),b=LocalDate.of(2026,11,4).atStartOfDay(zone).toInstant();
        try(EventStore store=new EventStore(context,DATABASE)) {
            long id=store.save(0,daily("Серія"),null);
            List<SavedEvent> days=store.between(a,b,zone);assertEquals(3,days.size());assertEquals(id,days.get(2).id);
            store.save(id,daily("Оновлена серія"),null);assertEquals("Оновлена серія",store.between(a,b,zone).get(2).title);
            store.delete(id);assertTrue(store.between(a,b,zone).isEmpty());
        }
    }
    @Test public void allDayVacationIncludesItsLastDayAndHasNoInventedClockOrDuration() {
        try(EventStore store=new EventStore(context,DATABASE)) {
            long id=store.save(0,new CalendarEvent("Відпустка",LocalDate.of(2026,11,5),null,null,zone,null,true,LocalDate.of(2026,11,11),""),null);
            SavedEvent event=store.find(id);assertTrue(event.allDay);assertNull(event.durationMinutes);assertNull(event.reminderTime);
            Instant a=LocalDate.of(2026,11,10).atStartOfDay(zone).toInstant(),b=LocalDate.of(2026,11,11).atStartOfDay(zone).toInstant();
            assertEquals(1,store.between(a,b,zone).size());assertTrue(store.between(b,b.plusSeconds(86400),zone).isEmpty());
        }
    }
    @Test public void recurringReminderAdvancesAfterAnAtomicClaim() {
        try(EventStore store=new EventStore(context,DATABASE)) {
            long id=store.save(0,daily("Нагадування"),10);
            Instant now=LocalDate.of(2026,10,26).atTime(8,50).atZone(zone).toInstant();
            SavedEvent first=store.nextReminder(now);long at=ReminderPolicy.plannedAt(first);
            assertNotNull(store.claimReminder(id,at,now));assertNull(store.claimReminder(id,at,now));
            SavedEvent next=store.nextReminder(now);assertEquals(LocalDate.of(2026,10,27),next.start.atZone(zone).toLocalDate());
            assertNotNull(store.claimReminder(id,ReminderPolicy.plannedAt(next),Instant.ofEpochMilli(ReminderPolicy.plannedAt(next))));
        }
    }
    @Test public void allDayReminderRequiresClockSelectionAndPersistsIt() {
        CalendarEvent event=new CalendarEvent("Весь день",LocalDate.of(2026,11,5),null,null,zone,null,true,LocalDate.of(2026,11,6),"");
        try(EventStore store=new EventStore(context,DATABASE)) {
            assertThrows(IllegalArgumentException.class,()->store.save(0,event,0));
            long id=store.save(0,event,0,LocalTime.of(9,0));
            assertEquals(LocalTime.of(9,0),store.find(id).reminderTime);
            assertEquals(LocalDate.of(2026,11,5).atTime(9,0).atZone(zone).toInstant().toEpochMilli(),ReminderPolicy.plannedAt(store.find(id)));
        }
    }
}
