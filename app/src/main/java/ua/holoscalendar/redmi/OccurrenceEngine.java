package ua.holoscalendar.redmi;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Virtual occurrences keep one editable series record and a bounded calendar window. */
final class OccurrenceEngine {
    private OccurrenceEngine() { }
    static SavedEvent onDate(SavedEvent master,LocalDate date) {
        ZonedDateTime anchor=master.start.atZone(master.zone);
        if(!RecurrenceRule.matches(master.recurrence,anchor.toLocalDate(),date)) return null;
        LocalDateTime local=date.atTime(anchor.toLocalTime());
        if(!master.allDay && master.zone.getRules().getValidOffsets(local).isEmpty()) return null;
        Instant start=master.allDay?date.atStartOfDay(master.zone).toInstant()
                :local.equals(anchor.toLocalDateTime())?master.start:local.atZone(master.zone).toInstant();
        LocalDate end=master.allDay?date.plusDays(ChronoUnit.DAYS.between(anchor.toLocalDate(),master.endDateExclusive)):null;
        return new SavedEvent(master.id,master.title,start,master.zone,master.durationMinutes,master.reminderMinutes,
                master.reminderDeliveredFor,master.allDay,end,master.recurrence,master.reminderTime);
    }
    static List<SavedEvent> between(SavedEvent master,Instant begin,Instant end,ZoneId displayZone) {
        List<SavedEvent> result=new ArrayList<>();
        LocalDate first=master.start.atZone(master.zone).toLocalDate();
        if(master.recurrence.isEmpty()) { if(overlaps(master,begin,end,displayZone)) result.add(master); return result; }
        long span=master.allDay?ChronoUnit.DAYS.between(first,master.endDateExclusive):1;
        LocalDate from=master.allDay?begin.atZone(displayZone).toLocalDate().minusDays(span-1):begin.atZone(master.zone).toLocalDate().minusDays(1);
        LocalDate until=master.allDay?end.atZone(displayZone).toLocalDate():end.atZone(master.zone).toLocalDate().plusDays(1);
        LocalDate day=RecurrenceRule.next(master.recurrence,first,from);
        while(day!=null&&!day.isAfter(until)) {
            SavedEvent event=onDate(master,day);if(event!=null&&overlaps(event,begin,end,displayZone)) result.add(event);
            day=RecurrenceRule.next(master.recurrence,first,day.plusDays(1));
        }
        return result;
    }
    private static boolean overlaps(SavedEvent event,Instant begin,Instant end,ZoneId zone) {
        if(event.allDay) {
            LocalDate a=begin.atZone(zone).toLocalDate(),b=end.atZone(zone).toLocalDate();
            if(!end.equals(b.atStartOfDay(zone).toInstant())) b=b.plusDays(1);
            return event.start.atZone(event.zone).toLocalDate().isBefore(b)&&event.endDateExclusive.isAfter(a);
        }
        return event.start.isBefore(end)&&(!event.start.isBefore(begin)||event.durationMinutes!=null
                &&event.start.plusSeconds(event.durationMinutes*60L).isAfter(begin));
    }
    static SavedEvent nextReminder(SavedEvent master,Instant now) {
        if(master.reminderMinutes==null||master.allDay&&master.reminderTime==null) return null;
        if(master.recurrence.isEmpty()) return ReminderPolicy.eligible(master,now)?master:null;
        LocalDate anchor=master.start.atZone(master.zone).toLocalDate();
        LocalDate from=now.atZone(master.zone).toLocalDate().minusDays(1);
        if(master.reminderDeliveredFor!=null) {
            LocalDate last=Instant.ofEpochMilli(master.reminderDeliveredFor+master.reminderMinutes*60_000L).atZone(master.zone).toLocalDate();
            if(last.isAfter(from)) from=last;
        }
        LocalDate day=RecurrenceRule.next(master.recurrence,anchor,from);
        for(int i=0;i<10&&day!=null;i++) {
            SavedEvent next=onDate(master,day);
            if(next!=null&&ReminderPolicy.eligible(next,now)) return next;
            day=RecurrenceRule.next(master.recurrence,anchor,day.plusDays(1));
        }
        return null;
    }
    static SavedEvent expected(SavedEvent master,long planned) {
        if(master.reminderMinutes==null) return null;
        if(master.recurrence.isEmpty()) return ReminderPolicy.plannedAt(master)==planned?master:null;
        Instant at=Instant.ofEpochMilli(planned+master.reminderMinutes*60_000L);
        SavedEvent occurrence=onDate(master,at.atZone(master.zone).toLocalDate());
        return occurrence!=null&&ReminderPolicy.plannedAt(occurrence)==planned?occurrence:null;
    }
}
