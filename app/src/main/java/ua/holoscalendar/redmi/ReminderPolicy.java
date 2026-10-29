package ua.holoscalendar.redmi;

import java.time.Instant;

/** Absolute instants keep reminders stable across timezone and daylight-saving changes. */
final class ReminderPolicy {
    private ReminderPolicy() { }
    static long plannedAt(SavedEvent event) {
        if (event.reminderMinutes == null) throw new IllegalStateException("No reminder");
        return start(event).toEpochMilli() - event.reminderMinutes * 60_000L;
    }
    static Instant start(SavedEvent event) {
        return event.allDay&&event.reminderTime!=null?event.start.atZone(event.zone).toLocalDate().atTime(event.reminderTime).atZone(event.zone).toInstant():event.start;
    }
    static boolean eligible(SavedEvent event, Instant now) {
        return event.reminderMinutes != null && (!event.allDay||event.reminderTime!=null&&!event.zone.getRules().getValidOffsets(event.start.atZone(event.zone).toLocalDate().atTime(event.reminderTime)).isEmpty())
                && (event.reminderDeliveredFor == null || (event.recurrence.isEmpty()?event.reminderDeliveredFor != plannedAt(event):event.reminderDeliveredFor < plannedAt(event)))
                && !now.isAfter(start(event).plusSeconds(300));
    }
    static boolean ready(SavedEvent event, long expectedTime, Instant now) {
        return eligible(event, now) && plannedAt(event) == expectedTime && now.toEpochMilli() >= expectedTime;
    }
    static long nextAlarmAt(SavedEvent event, Instant now) {
        if (!eligible(event, now)) throw new IllegalStateException("No pending reminder");
        return Math.max(plannedAt(event), now.toEpochMilli() + 1000);
    }
}
