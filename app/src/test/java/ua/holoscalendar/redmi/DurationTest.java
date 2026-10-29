package ua.holoscalendar.redmi;

import java.time.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class DurationTest {
    private final LocalDateTime now = LocalDateTime.of(2026, 10, 25, 10, 0);
    private VoiceParser.Result parse(String text) { return VoiceParser.parse(text, now); }
    private void duration(String phrase, int minutes) {
        VoiceParser.Result result = parse("Завтра о 15:00 Зустріч з Оленою " + phrase);
        assertEquals(phrase, Integer.valueOf(minutes), result.durationMinutes);
        assertEquals("Зустріч з Оленою", result.title);
        assertEquals(LocalDate.of(2026, 10, 26), result.date);
        assertEquals(LocalTime.of(15, 0), result.time);
    }

    @Test public void absentDurationIsNotInvented() {
        assertNull(parse("Завтра о 15:00 Зустріч з Оленою").durationMinutes);
        assertNull(parse("Зустріч").durationMinutes);
    }
    @Test public void numericMinutes() { duration("на 30 хвилин", 30); duration("на 45 хв", 45); }
    @Test public void hoursWithoutQuantity() { duration("на годину", 60); }
    @Test public void halfHours() {
        duration("на пів години", 30); duration("на півгодини", 30); duration("на півтори години", 90);
    }
    @Test public void hoursAndMinutes() {
        duration("на 1 годину 30 хвилин", 90); duration("на годину і 15 хвилин", 75);
        duration("на дві години тридцять хвилин", 150);
    }
    @Test public void commonSpokenQuantitiesAndApostrophes() {
        duration("на сорок п’ять хвилин", 45); duration("на пʼятнадцять хвилин", 15);
        duration("на тридцять хвилин", 30);
    }
    @Test public void alternativeDurationCues() {
        duration("тривалістю 20 хвилин", 20); duration("триватиме 2 години", 120);
        duration("протягом 10 хвилин", 10); duration("тривалість: 45 хвилин", 45);
    }
    @Test public void unicodeWhitespaceAndCase() { duration("НА\u00a030\u202fХВИЛИН", 30); }
    @Test public void decimalHoursRequireAnExactMinute() {
        duration("на 1,5 години", 90); duration("на 0.5 години", 30);
        assertNull(parse("Завтра о 15 Нарада на 0.5 хвилини").durationMinutes);
    }
    @Test public void relativeStartIsSeparateFromDuration() {
        VoiceParser.Result result = parse("Через 30 хвилин Нарада на 45 хвилин");
        assertEquals(LocalTime.of(10, 30), result.time);
        assertEquals(Integer.valueOf(45), result.durationMinutes);
        assertEquals("Нарада", result.title);
        assertNull(parse("Через годину Нарада").durationMinutes);
    }
    @Test public void dateAndClockPrepositionsAreNotDurations() {
        VoiceParser.Result result = parse("Нарада на 05.11 на 15:00");
        assertNull(result.durationMinutes);
        assertEquals(LocalDate.of(2026, 11, 5), result.date);
        assertEquals(LocalTime.of(15, 0), result.time);
        assertEquals("Нарада", result.title);
    }
    @Test public void invalidAndOverflowingDurationsRemainUnset() {
        for (String text : new String[]{"на 0 хвилин", "на -30 хвилин", "на 1441 хвилину", "на 25 годин",
                "на 9999999999999999999999999 хвилин", "на 1 хвилину 30 хвилин"}) {
            VoiceParser.Result result = parse("Завтра о 15 Нарада " + text);
            assertNull(text, result.durationMinutes);
            assertTrue(text, result.warning.contains("Некоректна тривалість"));
            assertEquals("Нарада", result.title);
        }
        duration("на 24 години", 1440);
        duration("на хвилину", 1);
    }
    @Test public void conflictingDurationsRequireManualChoice() {
        VoiceParser.Result result = parse("Завтра о 15 Нарада на 30 хвилин або на годину");
        assertNull(result.durationMinutes);
        assertTrue(result.warning.contains("кілька тривалостей"));
    }
    @Test public void calendarModelDoesNotInventAnEnd() {
        CalendarEvent event = new CalendarEvent("Нарада", now.toLocalDate(), now.toLocalTime(), null, ZoneOffset.UTC);
        assertNull(event.durationMinutes);
        assertEquals(now.toInstant(ZoneOffset.UTC).toEpochMilli(), event.startMillis());
        assertThrows(IllegalStateException.class, event::endMillis);
    }
}
