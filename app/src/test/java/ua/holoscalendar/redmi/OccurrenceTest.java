package ua.holoscalendar.redmi;

import java.time.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class OccurrenceTest {
    private final ZoneId zone=ZoneId.of("Europe/Kyiv");
    private SavedEvent master(String date,String time,String rule,Integer reminder) {
        return new SavedEvent(1,"Серія",LocalDate.parse(date).atTime(LocalTime.parse(time)).atZone(zone).toInstant(),zone,
                null,reminder,null,false,null,rule,null);
    }
    private List<SavedEvent> window(SavedEvent event,String start,String end) {
        return OccurrenceEngine.between(event,LocalDate.parse(start).atStartOfDay(zone).toInstant(),LocalDate.parse(end).atStartOfDay(zone).toInstant(),zone);
    }
    @Test public void dailySeriesUsesOneMasterAndOnlyExpandsTheRequestedWindow() {
        SavedEvent event=master("2020-01-01","09:00","D:1",null);
        List<SavedEvent> days=window(event,"2026-10-26","2026-10-29");
        assertEquals(3,days.size()); assertEquals(1,days.get(2).id);
        assertEquals(LocalDate.of(2026,10,28),days.get(2).start.atZone(zone).toLocalDate());
        assertNull(days.get(0).durationMinutes);
    }
    @Test public void weekdayAndWeekendRulesSelectTheRightDays() {
        assertEquals(5,window(master("2026-10-26","09:00","W:1,2,3,4,5",null),"2026-10-26","2026-11-02").size());
        assertEquals(2,window(master("2026-10-26","09:00","W:6,7",null),"2026-10-26","2026-11-02").size());
    }
    @Test public void fortnightIsAnchoredToTheSeriesWeek() {
        List<SavedEvent> days=window(master("2026-10-26","09:00","W2:2",null),"2026-10-26","2026-11-16");
        assertEquals(2,days.size());assertEquals(LocalDate.of(2026,11,10),days.get(1).start.atZone(zone).toLocalDate());
    }
    @Test public void everySecondDayCrossesMonthBoundaries() {
        List<SavedEvent> days=window(master("2026-10-26","09:00","D:2",null),"2026-10-26","2026-11-02");
        assertEquals(4,days.size());assertEquals(LocalDate.of(2026,11,1),days.get(3).start.atZone(zone).toLocalDate());
    }
    @Test public void monthlyAndLeapDayRulesSkipInvalidDatesWithoutDrifting() {
        assertEquals(LocalDate.of(2026,12,31),RecurrenceRule.next("M:31",LocalDate.of(2026,10,31),LocalDate.of(2026,11,1)));
        assertEquals(LocalDate.of(2104,2,29),RecurrenceRule.next("Y:2:29",LocalDate.of(2096,2,29),LocalDate.of(2097,1,1)));
        assertEquals(LocalDate.of(2026,11,29),RecurrenceRule.next("MN:-1:7",LocalDate.of(2026,10,26),LocalDate.of(2026,11,1)));
    }
    @Test public void localWallTimeSurvivesAutumnClockChange() {
        List<SavedEvent> days=window(master("2026-10-24","09:00","D:1",null),"2026-10-24","2026-10-27");
        assertEquals(3,days.size());
        assertEquals(LocalTime.of(9,0),days.get(1).start.atZone(zone).toLocalTime());
        assertEquals(25*3600,java.time.Duration.between(days.get(0).start,days.get(1).start).getSeconds());
    }
    @Test public void nonexistentSpringTimeIsSkippedRatherThanMoved() {
        List<SavedEvent> days=window(master("2026-03-28","03:30","D:1",null),"2026-03-28","2026-03-31");
        assertEquals(2,days.size());assertEquals(LocalDate.of(2026,3,30),days.get(1).start.atZone(zone).toLocalDate());
    }
    @Test public void allDayRangeKeepsDatesAcrossTimezoneChangesAndIncludesTheLastDay() {
        SavedEvent event=new SavedEvent(1,"Відпустка",LocalDate.of(2026,11,5).atStartOfDay(zone).toInstant(),zone,
                null,null,null,true,LocalDate.of(2026,11,11),"",null);
        assertTrue(event.occursOn(LocalDate.of(2026,11,5),ZoneId.of("America/Los_Angeles")));
        assertTrue(event.occursOn(LocalDate.of(2026,11,10),zone));assertFalse(event.occursOn(LocalDate.of(2026,11,11),zone));
        assertEquals(1,window(event,"2026-11-10","2026-11-11").size());
    }
    @Test public void allDayReminderRequiresAnExplicitClockTime() {
        Instant begin=LocalDate.of(2026,11,5).atStartOfDay(zone).toInstant();
        SavedEvent missing=new SavedEvent(1,"Весь день",begin,zone,null,0,null,true,LocalDate.of(2026,11,6),"",null);
        assertNull(OccurrenceEngine.nextReminder(missing,begin));
        SavedEvent chosen=new SavedEvent(1,"Весь день",begin,zone,null,0,null,true,LocalDate.of(2026,11,6),"",LocalTime.of(9,0));
        assertEquals(LocalDate.of(2026,11,5).atTime(9,0).atZone(zone).toInstant().toEpochMilli(),ReminderPolicy.plannedAt(chosen));
    }
    @Test public void dailyReminderAdvancesAfterDeliveryAndDoesNotReplayThePast() {
        SavedEvent event=master("2026-10-26","09:00","D:1",10);
        long planned=ReminderPolicy.plannedAt(event);
        SavedEvent delivered=new SavedEvent(event.id,event.title,event.start,zone,null,10,planned,false,null,event.recurrence,null);
        SavedEvent next=OccurrenceEngine.nextReminder(delivered,event.start);
        assertEquals(LocalDate.of(2026,10,27),next.start.atZone(zone).toLocalDate());
        assertFalse(ReminderPolicy.eligible(delivered,Instant.ofEpochMilli(planned)));
    }
    @Test public void staleOrOffRuleOccurrenceIsRejected() {
        SavedEvent event=master("2026-10-26","09:00","W:1",10);
        assertNull(OccurrenceEngine.expected(event,ReminderPolicy.plannedAt(event)+86_400_000L));
        assertNotNull(OccurrenceEngine.expected(event,ReminderPolicy.plannedAt(event)));
    }
    @Test public void calendarExportUsesStandardRecurrenceRules() {
        assertEquals("FREQ=WEEKLY;INTERVAL=2;BYDAY=TU",RecurrenceRule.rrule("W2:2"));
        assertEquals("FREQ=MONTHLY;BYDAY=-1SU",RecurrenceRule.rrule("MN:-1:7"));
        assertThrows(IllegalArgumentException.class,()->RecurrenceRule.validate("D:0"));
        assertThrows(IllegalArgumentException.class,()->RecurrenceRule.validate("W:9"));
    }
}
