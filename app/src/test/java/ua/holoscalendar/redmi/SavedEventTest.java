package ua.holoscalendar.redmi;

import java.time.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class SavedEventTest {
    private final ZoneId kyiv = ZoneId.of("Europe/Kyiv");
    private SavedEvent event(String start, Integer duration) {
        return new SavedEvent(1, "Зустріч", Instant.parse(start), kyiv, duration);
    }
    @Test public void unknownDurationOnlyBelongsToItsStartDay() {
        SavedEvent event = event("2026-11-05T21:45:00Z", null);
        assertTrue(event.occursOn(LocalDate.of(2026,11,5), kyiv));
        assertFalse(event.occursOn(LocalDate.of(2026,11,6), kyiv));
    }
    @Test public void overnightMeetingAppearsOnBothDays() {
        SavedEvent event = event("2026-11-05T21:45:00Z", 60);
        assertTrue(event.occursOn(LocalDate.of(2026,11,5), kyiv));
        assertTrue(event.occursOn(LocalDate.of(2026,11,6), kyiv));
        assertFalse(event.occursOn(LocalDate.of(2026,11,7), kyiv));
    }
    @Test public void endingAtMidnightDoesNotAppearOnTheNextDay() {
        SavedEvent event = event("2026-11-05T21:00:00Z", 60);
        assertFalse(event.occursOn(LocalDate.of(2026,11,6), kyiv));
    }
    @Test public void displayUsesTheCurrentTimezoneWithoutMovingTheInstant() {
        SavedEvent event = event("2026-11-05T23:30:00Z", null);
        assertTrue(event.occursOn(LocalDate.of(2026,11,6), kyiv));
        assertTrue(event.occursOn(LocalDate.of(2026,11,5), ZoneOffset.UTC));
        assertEquals(Instant.parse("2026-11-05T23:30:00Z"), event.start);
    }
    @Test public void springDayHasTwentyThreeRealHours() {
        SavedEvent event = event("2026-03-28T22:00:00Z", 1440);
        assertTrue(event.occursOn(LocalDate.of(2026,3,29), kyiv));
        assertTrue(event.occursOn(LocalDate.of(2026,3,30), kyiv));
    }
    @Test public void autumnDayHasTwentyFiveRealHours() {
        SavedEvent event = event("2026-10-24T21:00:00Z", 1440);
        assertTrue(event.occursOn(LocalDate.of(2026,10,25), kyiv));
        assertFalse(event.occursOn(LocalDate.of(2026,10,26), kyiv));
    }
}
