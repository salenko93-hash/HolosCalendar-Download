package ua.holoscalendar.redmi;

import org.junit.Test;
import static org.junit.Assert.*;
import java.time.*;

public class VoiceParserReviewTest {
    private final LocalDateTime now = LocalDateTime.of(2026, 10, 23, 10, 0);
    private VoiceParser.Result parse(String text) { return VoiceParser.parse(text, now); }

    @Test public void threeDigitDayIsRejectedInsteadOfTruncated() {
        var r = parse("125 жовтня о 9 Нарада");
        assertNull(r.date); assertTrue(r.warning.contains("Некоректна дата")); assertEquals("Нарада", r.title);
    }
    @Test public void fiveDigitNamedYearIsRejectedInsteadOfTruncated() {
        var r = parse("25 жовтня 20270 о 9 Нарада");
        assertNull(r.date); assertTrue(r.warning.contains("Некоректна дата")); assertEquals("Нарада", r.title);
    }
    @Test public void shortYearIsNotTreatedAsMissing() { assertNull(parse("25 жовтня 27 о 9 Нарада").date); }
    @Test public void zeroYearIsRejected() { assertNull(parse("25 жовтня 0000 о 9 Нарада").date); }
    @Test public void fiveDigitNumericYearIsRejected() { assertNull(parse("25.10.20270 о 9 Нарада").date); }
    @Test public void yearFirstDateIsNotPartiallyParsed() { assertNull(parse("2027.05.11 о 9 Нарада").date); }
    @Test public void prepositionBelongsToRelativeDate() { assertEquals("Нарада", parse("Нарада на завтра о 9").title); }
    @Test public void dottedDateAfterNaDoesNotBecomeClock() {
        var r = parse("Нарада на 05.11");
        assertEquals(LocalDate.of(2026, 11, 5), r.date); assertNull(r.time); assertEquals("Нарада", r.title);
    }
    @Test public void colonClockAfterNaIsStillSupported() { assertEquals(LocalTime.of(14,30), parse("Завтра на 14:30 Нарада").time); }
    @Test public void dateAndClockWithPrepositionsRemainDistinct() {
        var r = parse("Нарада на 05.11 о 9");
        assertEquals(LocalDate.of(2026,11,5), r.date); assertEquals(LocalTime.of(9,0), r.time); assertEquals("Нарада", r.title);
    }
    @Test public void namedDatePrepositionIsConsumed() { assertEquals("Нарада", parse("Нарада на 25 жовтня о 9").title); }
    @Test public void modifierLetterApostropheIsRecognized() { assertEquals(now.toLocalDate(), parse("У пʼятницю о 14 Нарада").date); }
    @Test public void nonBreakingSpaceDoesNotRemainInTitle() {
        var r = parse("Завтра о\u00a014:30 Нарада у\u202fКиєві");
        assertEquals(LocalTime.of(14,30), r.time); assertEquals("Нарада у Києві", r.title);
    }
    @Test public void malformedDottedClockDoesNotBecomeDate() {
        var r = parse("Завтра о 9.5 Нарада");
        assertEquals(now.toLocalDate().plusDays(1), r.date); assertNull(r.time); assertTrue(r.warning.contains("Некоректний час"));
    }
    @Test public void relativeMinuteIsNeverScheduledOneSecondLater() {
        var r = VoiceParser.parse("Через хвилину Нарада", now.withSecond(59));
        assertEquals(LocalTime.of(10,2), r.time);
    }
    @Test public void fractionalSecondRoundsUpAcrossMidnight() {
        var r = VoiceParser.parse("Через хвилину Нарада", LocalDateTime.of(2026,10,23,23,58,0,1));
        assertEquals(LocalDate.of(2026,10,24), r.date); assertEquals(LocalTime.MIDNIGHT, r.time);
    }
    @Test public void relativeHourCrossesSpringGapInRealTime() {
        ZoneId zone = ZoneId.of("Europe/Kyiv");
        var current = ZonedDateTime.of(2026,3,29,2,30,0,0,zone);
        var r = VoiceParser.parse("Через годину Нарада", current);
        assertEquals(LocalTime.of(4,30), r.time); assertEquals(current.toInstant().plusSeconds(3600), r.instant);
    }
    @Test public void relativeHourRetainsSecondAutumnOccurrence() {
        ZoneId zone = ZoneId.of("Europe/Kyiv");
        var current = ZonedDateTime.ofLocal(LocalDateTime.of(2026,10,25,3,30),zone,ZoneOffset.ofHours(3));
        var r = VoiceParser.parse("Через годину Нарада", current);
        var event = new CalendarEvent(r.title,r.date,r.time,60,zone,r.instant);
        assertEquals(current.toInstant().plusSeconds(3600).toEpochMilli(), event.startMillis());
        assertFalse(event.requiresOffsetConfirmation());
    }
    @Test public void conflictingOffsetHasNoExactInstant() { assertNull(parse("Через годину завтра Нарада").instant); }
    @Test public void eveningHourIsResolvedToTwentyOne() {
        var r = parse("Завтра о 9 вечора Нарада");
        assertEquals(LocalTime.of(21,0),r.time); assertEquals("Нарада",r.title);
    }
    @Test public void clockDayPartResolvesAndDoesNotRemainInTitle() {
        var r = parse("Завтра о 9:30 вечора Нарада");
        assertEquals(LocalTime.of(21,30),r.time); assertEquals("Нарада",r.title);
    }
}
