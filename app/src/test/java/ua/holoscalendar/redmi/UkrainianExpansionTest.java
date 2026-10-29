package ua.holoscalendar.redmi;

import java.time.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class UkrainianExpansionTest {
    private final ZoneId kyiv=ZoneId.of("Europe/Kyiv");
    @Test public void calendarMonthUsesLastValidDayWithConfirmation() {
        var result=EventTextParser.parse("Через місяць Перевірка",LocalDateTime.of(2027,1,31,10,0));
        assertEquals(LocalDate.of(2027,2,28),result.date);
        assertTrue(result.confirmApproximation);assertNull(result.time);assertNull(result.durationMinutes);
    }
    @Test public void leapDayPlusOneYearRequiresConfirmation() {
        var result=EventTextParser.parse("Через рік Перевірка",LocalDateTime.of(2028,2,29,10,0));
        assertEquals(LocalDate.of(2029,2,28),result.date);assertTrue(result.confirmApproximation);
    }
    @Test public void compoundOffsetPreservesElapsedTimeAcrossClockChange() {
        ZonedDateTime now=ZonedDateTime.of(2026,3,29,2,30,0,0,kyiv);
        var result=EventTextParser.parse("Через годину і двадцять хвилин Дзвінок",now);
        assertEquals(now.toInstant().plusSeconds(80*60),result.instant);
        assertNull(result.durationMinutes);assertEquals("Дзвінок",result.title);
    }
    @Test public void conflictingDailyAndWeeklyRulesStillRequireClarification() {
        var result=EventTextParser.parse("Щодня щопонеділка Нарада",LocalDateTime.of(2026,10,26,10,0));
        assertTrue(result.recurrenceNeedsChoice);assertEquals("",result.recurrence);
    }
    @Test public void titleWordsBetweenWeeklyRulesArePreserved() {
        var result=EventTextParser.parse("Щопонеділка зустріч із мамою щосереди",LocalDateTime.of(2026,10,26,10,0));
        assertEquals("W:1,3",result.recurrence);assertEquals("зустріч із мамою",result.title);
    }
    @Test public void politeReminderStillEnablesOnlyTheStartReminder() {
        var result=EventTextParser.parse("Будь ласка, нагадайте мені завтра о дев’ятій ранку зателефонувати мамі",LocalDateTime.of(2026,10,26,10,0));
        assertEquals(Integer.valueOf(0),result.reminderMinutes);assertNull(result.durationMinutes);
        assertEquals("зателефонувати мамі",result.title);
    }
}
