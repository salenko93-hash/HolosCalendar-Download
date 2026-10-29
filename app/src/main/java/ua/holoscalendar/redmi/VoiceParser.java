package ua.holoscalendar.redmi;

import java.time.*;
import java.util.*;

/** Public parser result includes explicit clarification rather than guessed missing values. */
public final class VoiceParser {
    private VoiceParser() { }
    public static final class Result {
        public final String title,warning,recurrence;
        public final LocalDate date,endDateExclusive;
        public final LocalTime time;
        public final Instant instant;
        public final Integer durationMinutes,reminderMinutes;
        public final boolean allDay,confirmApproximation,durationNeedsChoice,recurrenceNeedsChoice;
        public final List<LocalTime> timeChoices;
        Result(String title,LocalDate date,LocalTime time,String warning,Instant instant,Integer duration,
               boolean allDay,LocalDate endDate,String recurrence,boolean confirm,boolean durationChoice,
               boolean recurrenceChoice,List<LocalTime> choices,Integer reminder) {
            this.title=title;this.date=date;this.time=time;this.warning=warning;this.instant=instant;
            this.durationMinutes=duration;this.allDay=allDay;this.endDateExclusive=endDate;
            this.recurrence=recurrence;this.confirmApproximation=confirm;this.durationNeedsChoice=durationChoice;
            this.recurrenceNeedsChoice=recurrenceChoice;this.timeChoices=Collections.unmodifiableList(new ArrayList<>(choices));
            this.reminderMinutes=reminder;
        }
    }
    public static Result parse(String text,LocalDateTime now) { return parse(text,now.atZone(ZoneOffset.UTC)); }
    public static Result parse(String text,ZonedDateTime now) { return new UkrainianParser(text,now).parse(); }
}
