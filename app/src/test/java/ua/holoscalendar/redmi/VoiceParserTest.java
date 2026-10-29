package ua.holoscalendar.redmi;
import org.junit.Test;
import static org.junit.Assert.*;
import java.time.*;
public class VoiceParserTest {
 private final LocalDateTime now=LocalDateTime.of(2026,10,23,10,0);
 private VoiceParser.Result parse(String s) { return VoiceParser.parse(s,now); }
 @Test public void dateIsNotTime() { var r=parse("Зустріч 05.11"); assertEquals(LocalDate.of(2026,11,5),r.date); assertNull(r.time); }
 @Test public void explicitTimeWins() { var r=parse("Зустріч 05.11 о 14:30"); assertEquals(LocalTime.of(14,30),r.time); assertEquals("Зустріч",r.title); }
 @Test public void dottedClockWithPreposition() { var r=parse("Завтра о 14.30 Зустріч"); assertEquals(now.toLocalDate().plusDays(1),r.date); assertEquals(LocalTime.of(14,30),r.time); }
 @Test public void titleKeepsSpellingAndPrepositions() { assertEquals("Зустріч у Києві",parse("25 жовтня о 14:30 Зустріч у Києві").title); }
 @Test public void titleMustNotBeInvented() { assertEquals("",parse("Завтра о 14:30").title); }
 @Test public void invalidClockIsNotPartiallyParsed() { var r=parse("Завтра о 25:00 Зустріч"); assertNull(r.time); assertTrue(r.warning.contains("Некоректний час")); }
 @Test public void invalidDateRequiresCorrection() { var r=parse("31.02 о 9 Зустріч"); assertNull(r.date); assertTrue(r.warning.contains("Некоректна дата")); }
 @Test public void leapDayFindsNextOccurrence() { assertEquals(LocalDate.of(2028,2,29),parse("29.02 о 9 Зустріч").date); }
 @Test public void weekdayIsConsumed() { var r=parse("У понеділок о 9 Нарада"); assertEquals(LocalDate.of(2026,10,26),r.date); assertEquals("Нарада",r.title); }
 @Test public void nextWeekIsExplicit() { assertEquals(LocalDate.of(2026,11,2),parse("Наступного понеділка о 9 Нарада").date); }
 @Test public void missingFieldsStayMissing() { var r=parse("Зустріч у Києві"); assertNull(r.date); assertNull(r.time); assertEquals("Зустріч у Києві",r.title); }
 @Test public void namedDateIncludesYear() { assertEquals(LocalDate.of(2027,1,25),parse("25 січня 2027 о 9 Зустріч").date); }
 @Test public void relativeHour() { var r=parse("Через годину Зустріч"); assertEquals(now.toLocalDate(),r.date); assertEquals(LocalTime.of(11,0),r.time); assertEquals("Зустріч",r.title); }
 @Test public void relativeMinutes() { assertEquals(LocalTime.of(10,30),parse("Через 30 хвилин Дзвінок").time); }
 @Test public void relativeHalfHour() { assertEquals(LocalTime.of(10,30),parse("Через пів години Дзвінок").time); }
 @Test public void relativeTimeCrossesMidnight() { var r=VoiceParser.parse("Через дві години Дзвінок",LocalDateTime.of(2026,10,23,23,30)); assertEquals(LocalDate.of(2026,10,24),r.date); assertEquals(LocalTime.of(1,30),r.time); }
 @Test public void relativeAndAbsoluteTimeConflict() { var r=parse("Завтра через годину Дзвінок"); assertNull(r.date); assertNull(r.time); }
 @Test public void multipleClocksRequireCorrection() { var r=parse("Завтра о 9 та о 14:30 Зустріч"); assertNull(r.time); assertNull(r.date); }
 @Test public void multipleDatesRequireCorrection() { var r=parse("05.11 та 06.11 о 9 Зустріч"); assertNull(r.date); }
 @Test public void zeroOffsetRequiresCorrection() { assertNull(parse("Через 0 хвилин Зустріч").time); }
 @Test public void bareDottedDateKeepsExplicitHour() { assertEquals(LocalTime.of(9,0),parse("05.11 о 9 Зустріч").time); }
 @Test public void apostropheWeekday() { var r=parse("У п’ятницю о 14 Зустріч"); assertEquals(now.toLocalDate(),r.date); assertEquals("Зустріч",r.title); }
 @Test public void invalidMinutesDoNotBecomeAnHour() { assertNull(parse("Завтра о 14:99 Зустріч").time); }
 @Test public void explicitPastYearIsPreserved() { assertEquals(LocalDate.of(2025,11,5),parse("05.11.2025 о 9 Зустріч").date); }
}
