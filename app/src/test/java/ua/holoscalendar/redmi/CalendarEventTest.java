package ua.holoscalendar.redmi;

import org.junit.Test;
import static org.junit.Assert.*;
import java.time.*;

public class CalendarEventTest {
    private final ZoneId kyiv = ZoneId.of("Europe/Kyiv");
    private CalendarEvent event(LocalDate date, LocalTime time, int duration) {
        return new CalendarEvent("  Зустріч у Києві  ", date, time, duration, kyiv);
    }
    @Test public void usesPhoneTimezoneAndTrimsTitle() {
        CalendarEvent e = event(LocalDate.of(2026, 11, 5), LocalTime.of(14, 30), 30);
        assertEquals("Зустріч у Києві", e.title);
        assertEquals(Instant.parse("2026-11-05T12:30:00Z").toEpochMilli(), e.startMillis());
        assertEquals(30 * 60_000L, e.endMillis() - e.startMillis());
    }
    @Test public void detectsMissingHourDuringSpringTransition() {
        assertTrue(event(LocalDate.of(2026, 3, 29), LocalTime.of(3, 30), 60).isMissingLocalTime());
    }
    @Test public void missingHourCannotBeExported() {
        assertThrows(IllegalStateException.class, () -> event(LocalDate.of(2026, 3, 29), LocalTime.of(3, 30), 60).startMillis());
    }
    @Test public void detectsRepeatedHourDuringAutumnTransition() {
        CalendarEvent e = event(LocalDate.of(2026, 10, 25), LocalTime.of(3, 30), 120);
        assertTrue(e.isRepeatedLocalTime());
        assertEquals(Instant.parse("2026-10-25T00:30:00Z").toEpochMilli(), e.startMillis());
        assertEquals(120 * 60_000L, e.endMillis() - e.startMillis());
    }
    @Test public void endCanCrossMidnight() {
        CalendarEvent e = event(LocalDate.of(2026, 11, 5), LocalTime.of(23, 45), 60);
        assertEquals(LocalDate.of(2026, 11, 6), Instant.ofEpochMilli(e.endMillis()).atZone(kyiv).toLocalDate());
    }
    @Test public void refusesMissingTitle() { assertThrows(IllegalArgumentException.class, () -> eventWithTitle(" ")); }
    private CalendarEvent eventWithTitle(String title) { return new CalendarEvent(title, LocalDate.now(), LocalTime.NOON, 60, kyiv); }
    @Test public void refusesMissingDate() { assertThrows(IllegalArgumentException.class, () -> new CalendarEvent("Зустріч", null, LocalTime.NOON, 60, kyiv)); }
    @Test public void refusesInvalidDuration() { assertThrows(IllegalArgumentException.class, () -> event(LocalDate.now(), LocalTime.NOON, 0)); }
    @Test public void explicitOverlapStillRequiresConfirmation() {
        assertTrue(event(LocalDate.of(2026,10,25),LocalTime.of(3,30),60).requiresOffsetConfirmation());
    }
    @Test public void exactInstantMustMatchEditableFields() {
        assertThrows(IllegalArgumentException.class, () -> new CalendarEvent("Нарада",LocalDate.of(2026,11,5),LocalTime.NOON,60,kyiv,Instant.parse("2026-11-05T12:00:00Z")));
    }
}
