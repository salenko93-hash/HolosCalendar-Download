package ua.holoscalendar.redmi;

import java.time.*;

/** A persisted event. Unknown duration remains unknown, including in the calendar. */
public final class SavedEvent {
    public final long id;
    public final String title;
    public final Instant start;
    public final ZoneId zone;
    public final Integer durationMinutes;
    public final Integer reminderMinutes;
    final Long reminderDeliveredFor;
    public final boolean allDay;
    public final LocalDate endDateExclusive;
    public final String recurrence;
    final LocalTime reminderTime;

    public SavedEvent(long id, String title, Instant start, ZoneId zone, Integer durationMinutes) {
        this(id, title, start, zone, durationMinutes, null, null);
    }

    SavedEvent(long id, String title, Instant start, ZoneId zone, Integer durationMinutes,
               Integer reminderMinutes, Long reminderDeliveredFor) {
        this(id,title,start,zone,durationMinutes,reminderMinutes,reminderDeliveredFor,false,null,"",null);
    }

    SavedEvent(long id,String title,Instant start,ZoneId zone,Integer durationMinutes,Integer reminderMinutes,Long delivered,
               boolean allDay,LocalDate endDateExclusive,String recurrence,LocalTime reminderTime) {
        ZonedDateTime local = start.atZone(zone);
        CalendarEvent validated = new CalendarEvent(title, local.toLocalDate(), local.toLocalTime(),
                durationMinutes, zone, start,allDay,endDateExclusive,recurrence);
        this.id = id;
        this.title = validated.title;
        this.start = start;
        this.zone = zone;
        this.durationMinutes = validated.durationMinutes;
        this.allDay=allDay;this.endDateExclusive=validated.endDateExclusive;this.recurrence=validated.recurrence;this.reminderTime=reminderTime;
        if (reminderMinutes != null && (reminderMinutes < 0 || reminderMinutes > 1440))
            throw new IllegalArgumentException("Invalid reminder");
        this.reminderMinutes = reminderMinutes;
        this.reminderDeliveredFor = delivered;
    }

    public boolean occursOn(LocalDate date, ZoneId displayZone) {
        if(allDay) return !date.isBefore(start.atZone(zone).toLocalDate())&&date.isBefore(endDateExclusive);
        Instant dayStart = date.atStartOfDay(displayZone).toInstant();
        Instant dayEnd = date.plusDays(1).atStartOfDay(displayZone).toInstant();
        if (!start.isBefore(dayEnd)) return false;
        return !start.isBefore(dayStart) || durationMinutes != null
                && start.plusSeconds(durationMinutes * 60L).isAfter(dayStart);
    }
}
