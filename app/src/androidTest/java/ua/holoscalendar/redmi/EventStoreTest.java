package ua.holoscalendar.redmi;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.time.*;
import java.util.List;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class EventStoreTest {
    private static final String DATABASE = "calendar-storage-test.db";
    private Context context;
    private final ZoneId zone = ZoneId.of("Europe/Kiev");
    private final LocalDate day = LocalDate.of(2026,11,5);
    @Before public void prepare() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.deleteDatabase(DATABASE);
    }
    @After public void cleanup() { context.deleteDatabase(DATABASE); }
    private CalendarEvent event(String title, LocalDate date, LocalTime time, Integer duration) {
        return new CalendarEvent(title,date,time,duration,zone);
    }
    private List<SavedEvent> dayEvents(EventStore store, LocalDate day) {
        return store.between(day.atStartOfDay(zone).toInstant(), day.plusDays(1).atStartOfDay(zone).toInstant());
    }

    @Test public void eventAndUnknownDurationSurviveClosingTheDatabase() {
        long id;
        try (EventStore store = new EventStore(context,DATABASE)) {
            id = store.save(0,event("Зустріч з Оленою",day,LocalTime.of(15,0),null));
        }
        try (EventStore store = new EventStore(context,DATABASE)) {
            SavedEvent saved = store.find(id);
            assertNotNull(saved); assertEquals("Зустріч з Оленою",saved.title);
            assertNull(saved.durationMinutes);
            assertEquals(day.atTime(15,0).atZone(zone).toInstant(),saved.start);
        }
    }
    @Test public void editingUpdatesOneEventAndCanRemoveItsDuration() {
        try (EventStore store = new EventStore(context,DATABASE)) {
            long id = store.save(0,event("Нарада",day,LocalTime.NOON,30));
            assertEquals(id,store.save(id,event("Нова назва",day,LocalTime.of(13,0),null)));
            assertEquals(1,dayEvents(store,day).size());
            assertEquals("Нова назва",store.find(id).title);
            assertNull(store.find(id).durationMinutes);
        }
    }
    @Test public void deletedEventDoesNotComeBackOnUpdate() {
        try (EventStore store = new EventStore(context,DATABASE)) {
            CalendarEvent event = event("Нарада",day,LocalTime.NOON,null);
            long id = store.save(0,event);
            assertTrue(store.delete(id)); assertNull(store.find(id));
            assertThrows(IllegalStateException.class,()->store.save(id,event));
            assertEquals(0,dayEvents(store,day).size());
        }
    }
    @Test public void queryIncludesOvernightEventsButExcludesEndsAtMidnight() {
        try (EventStore store = new EventStore(context,DATABASE)) {
            store.save(0,event("Продовжується",day.minusDays(1),LocalTime.of(23,45),60));
            store.save(0,event("Закінчується опівночі",day.minusDays(1),LocalTime.of(23,0),60));
            store.save(0,event("Тривалість невідома",day.minusDays(1),LocalTime.of(23,45),null));
            List<SavedEvent> selected = dayEvents(store,day);
            assertEquals(1,selected.size()); assertEquals("Продовжується",selected.get(0).title);
        }
    }
    @Test public void meetingsAreOrderedByStartAndId() {
        try (EventStore store = new EventStore(context,DATABASE)) {
            store.save(0,event("Пізніше",day,LocalTime.of(18,0),null));
            store.save(0,event("Раніше",day,LocalTime.of(9,0),45));
            List<SavedEvent> events = dayEvents(store,day);
            assertEquals("Раніше",events.get(0).title); assertEquals("Пізніше",events.get(1).title);
        }
    }
    @Test public void secondAutumnOccurrenceIsPreservedExactly() {
        try (EventStore store = new EventStore(context,DATABASE)) {
            Instant instant = Instant.parse("2026-10-25T01:30:00Z");
            ZonedDateTime local = instant.atZone(zone);
            long id = store.save(0,new CalendarEvent("Перехід годинника",local.toLocalDate(),local.toLocalTime(),30,zone,instant));
            assertEquals(instant,store.find(id).start);
        }
    }
}
