package ua.holoscalendar.redmi;

import java.time.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ReminderPolicyTest {
    private final Instant start = Instant.parse("2026-11-05T13:00:00Z");
    private SavedEvent event(Integer minutes, Long delivered) {
        return new SavedEvent(1,"Нарада",start,ZoneId.of("Europe/Kyiv"),null,minutes,delivered);
    }
    @Test public void disabledReminderIsNeverInvented() {
        assertFalse(ReminderPolicy.eligible(event(null,null), start.minusSeconds(3600)));
        assertThrows(IllegalStateException.class,()->ReminderPolicy.plannedAt(event(null,null)));
    }
    @Test public void calculatesMinutesBeforeTheActualInstant() {
        assertEquals(start.minusSeconds(900).toEpochMilli(), ReminderPolicy.plannedAt(event(15,null)));
        assertEquals(start.toEpochMilli(), ReminderPolicy.plannedAt(event(0,null)));
    }
    @Test public void doesNotDeliverBeforeTheSelectedTime() {
        long expected = ReminderPolicy.plannedAt(event(10,null));
        assertFalse(ReminderPolicy.ready(event(10,null),expected,Instant.ofEpochMilli(expected-1)));
        assertTrue(ReminderPolicy.ready(event(10,null),expected,Instant.ofEpochMilli(expected)));
    }
    @Test public void catchesUpMissedReminderWhileMeetingIsStillUpcoming() {
        Instant now = start.minusSeconds(60);
        assertTrue(ReminderPolicy.eligible(event(15,null),now));
        assertEquals(now.toEpochMilli()+1000,ReminderPolicy.nextAlarmAt(event(15,null),now));
    }
    @Test public void allowsBriefSystemDelayButDoesNotAnnounceOldMeetings() {
        assertTrue(ReminderPolicy.eligible(event(0,null),start.plusSeconds(300)));
        assertFalse(ReminderPolicy.eligible(event(0,null),start.plusSeconds(301)));
    }
    @Test public void deliveredReminderDoesNotRepeatAfterRestart() {
        long expected = ReminderPolicy.plannedAt(event(10,null));
        assertFalse(ReminderPolicy.eligible(event(10,expected),start.minusSeconds(60)));
        assertThrows(IllegalStateException.class,()->ReminderPolicy.nextAlarmAt(event(10,expected),start));
    }
    @Test public void staleAlarmForAnEditedMeetingIsRejected() {
        long old = ReminderPolicy.plannedAt(event(10,null));
        assertFalse(ReminderPolicy.ready(event(5,null),old,start));
    }
    @Test public void offsetIsElapsedTimeAcrossTheAutumnClockChange() {
        SavedEvent repeated = new SavedEvent(1,"Зустріч",Instant.parse("2026-10-25T01:30:00Z"),
                ZoneId.of("Europe/Kyiv"),null,60,null);
        assertEquals(Instant.parse("2026-10-25T00:30:00Z").toEpochMilli(),ReminderPolicy.plannedAt(repeated));
        assertThrows(IllegalArgumentException.class,()->event(-1,null));
        assertThrows(IllegalArgumentException.class,()->event(1441,null));
    }
}
