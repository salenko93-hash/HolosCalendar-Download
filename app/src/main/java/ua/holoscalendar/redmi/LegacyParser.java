package ua.holoscalendar.redmi;

import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.regex.*;

/** Local parser: consume temporal spans once and preserve the remaining title. */
final class LegacyParser {
    private LegacyParser() { }

    public static final class Result {
        public final String title;
        public final LocalDate date;
        public final LocalTime time;
        public final String warning;
        /** Null when the phrase does not explicitly specify a valid duration. */
        public final Integer durationMinutes;
        /** Known instant for a relative command; preserves the correct side of a DST overlap. */
        public final Instant instant;

        Result(String title, LocalDate date, LocalTime time, String warning) {
            this(title, date, time, warning, null);
        }
        Result(String title, LocalDate date, LocalTime time, String warning, Instant instant) {
            this(title, date, time, warning, instant, null);
        }
        private Result(String title, LocalDate date, LocalTime time, String warning, Instant instant, Integer durationMinutes) {
            this.title = title; this.date = date; this.time = time; this.warning = warning;
            this.durationMinutes = durationMinutes;
            this.instant = instant;
        }
    }

    // Android rejects UNICODE_CHARACTER_CLASS. Normalize separators explicitly for both runtimes.
    private static Pattern pattern(String value) {
        return Pattern.compile(value, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }
    private static final String START = "(?<![\\p{L}\\d./:])";
    private static final String PREPOSITION = "(?:(?:у|в|на)\\s+)?";
    // "на 05.11" is a date, whereas "о 05.11" and "на 05:11" are clock times.
    private static final Pattern CLOCK = pattern(START + "(?:(?:о|об)\\s+\\d{1,2}[.:]\\d{1,2}|(?:на\\s+)?\\d{1,2}:\\d{1,2})(?![\\p{L}\\d.:/])");
    private static final Pattern DATE = pattern(START + PREPOSITION + "(\\d+)[./](\\d+)(?:[./](\\d+))?+(?![\\p{L}\\d./:])");
    private static final String[] MONTHS = {"січня", "лютого", "березня", "квітня", "травня", "червня", "липня", "серпня", "вересня", "жовтня", "листопада", "грудня"};
    private static final Pattern NAMED = pattern(START + PREPOSITION + "(\\d+)\\s+(" + String.join("|", MONTHS) + ")(?:\\s+(\\d+))?+(?![\\p{L}\\d])");
    private static final Pattern RELATIVE = pattern("\\b" + PREPOSITION + "(післязавтра|завтра|сьогодні)\\b");
    private static final Pattern WEEKDAY = pattern("\\b" + PREPOSITION + "(?:(наступного|наступної|наступну)\\s+)?(понеділ(?:ок|ка)|вівтор(?:ок|ка)|серед(?:а|у|и)|четвер(?:га)?|п['’ʼ]ятниц(?:я|ю|і)|субот(?:а|у|и)|неділ(?:я|ю|і))\\b");
    private static final Pattern HOUR = pattern("\\b(?:о|об)\\s+(\\d{1,2})(?:\\s+годин(?:і|у)?)?(?![\\d.:])\\b");
    private static final Pattern OFFSET = pattern("\\bчерез\\s+(?:(\\d{1,3}|одну|дві|три|пів)\\s+)?(хвилин(?:у|и)?|годин(?:у|и)?)\\b");

    private static final Pattern DAY_PART = pattern("\\s+(ранку|дня|вечора|ночі)\\b");

    private static boolean consumeDayPart(StringBuilder remaining, int start) {
        Matcher part = DAY_PART.matcher(remaining).region(start, remaining.length());
        if (!part.lookingAt()) return false;
        consume(remaining, part); return true;
    }

    private static String title(StringBuilder remaining) {
        return remaining.toString().replaceAll("\\s+", " ").replaceAll("^[,.:;\\s]+|[,.:;\\s]+$", "");
    }
    private static void consume(StringBuilder value, Matcher match) {
        for (int i = match.start(); i < match.end(); i++) value.setCharAt(i, ' ');
    }
    private static boolean hasTemporalSpan(StringBuilder value) {
        return OFFSET.matcher(value).find() || CLOCK.matcher(value).find() || DATE.matcher(value).find()
                || NAMED.matcher(value).find() || RELATIVE.matcher(value).find()
                || WEEKDAY.matcher(value).find() || HOUR.matcher(value).find();
    }
    private static int dayNumber(String text) {
        if (text.length() > 2) throw new DateTimeException("Invalid day");
        return Integer.parseInt(text);
    }
    private static LocalDate date(int day, int month, String year, LocalDate now) {
        if (year != null) {
            if (year.length() != 4 || Integer.parseInt(year) == 0) throw new DateTimeException("Invalid year");
            return LocalDate.of(Integer.parseInt(year), month, day);
        }
        for (int candidate = now.getYear(); candidate <= now.getYear() + 8; candidate++) {
            try {
                LocalDate value = LocalDate.of(candidate, month, day);
                if (!value.isBefore(now)) return value;
            } catch (DateTimeException ignored) { }
        }
        throw new DateTimeException("Invalid date");
    }

    /** Deterministic local-time overload for callers without a timezone. */
    public static Result parse(String original, LocalDateTime now) {
        return parse(original, now.atZone(ZoneOffset.UTC));
    }

    public static Result parse(String original, ZonedDateTime zonedNow) {
        StringBuilder remaining = new StringBuilder(original.replaceAll("[\\s\\p{Z}]+", " ").trim());
        DurationParser.Result duration = DurationParser.consume(remaining);
        Result temporal = parseDateTime(remaining, zonedNow);
        String warning = duration.warning == null ? temporal.warning : temporal.warning + ". " + duration.warning;
        return new Result(temporal.title, temporal.date, temporal.time, warning, temporal.instant, duration.minutes);
    }

    private static Result parseDateTime(StringBuilder remaining, ZonedDateTime zonedNow) {
        LocalDateTime now = zonedNow.toLocalDateTime();
        LocalDate day = null; LocalTime time = null;
        boolean badDate = false, badTime = false, dayPart = false;
        Matcher offset = OFFSET.matcher(remaining);
        if (offset.find()) {
            String amount = offset.group(1), unit = offset.group(2).toLowerCase(Locale.ROOT);
            double quantity = amount == null || amount.equalsIgnoreCase("одну") ? 1
                    : amount.equalsIgnoreCase("дві") ? 2 : amount.equalsIgnoreCase("три") ? 3
                    : amount.equalsIgnoreCase("пів") ? 0.5 : Double.parseDouble(amount);
            long minutes = (long) (quantity * (unit.startsWith("год") ? 60 : 1));
            consume(remaining, offset);
            if (hasTemporalSpan(remaining)) return new Result(title(remaining), null, null,
                    "Знайдено кілька вказівок часу — оберіть дату й час вручну");
            if (minutes < 1) return new Result(title(remaining), null, null,
                    "Некоректний проміжок часу — оберіть дату й час вручну");
            ZonedDateTime target = zonedNow.plusMinutes(minutes);
            // The form edits minutes, so round up rather than schedule earlier than requested.
            if (target.getSecond() != 0 || target.getNano() != 0) target = target.plusMinutes(1);
            target = target.withSecond(0).withNano(0);
            String name = title(remaining);
            return new Result(name, target.toLocalDate(), target.toLocalTime(),
                    name.isEmpty() ? "Додайте назву події" : "Перевірте розпізнані дані", target.toInstant());
        }
        Matcher clock = CLOCK.matcher(remaining);
        if (clock.find()) {
            String[] parts = clock.group().replaceFirst("(?iu)^(?:о|об|на)\\s+", "").split("[.:]");
            try {
                if (parts[1].length() != 2) throw new DateTimeException("Use two minute digits");
                time = LocalTime.of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
            } catch (DateTimeException e) { badTime = true; }
            if (consumeDayPart(remaining, clock.end())) { time = null; badTime = true; dayPart = true; }
            consume(remaining, clock);
        }
        Matcher numeric = DATE.matcher(remaining), named = NAMED.matcher(remaining);
        if (numeric.find()) {
            try {
                if (numeric.group(2).length() > 2) throw new DateTimeException("Invalid month");
                day = date(dayNumber(numeric.group(1)), Integer.parseInt(numeric.group(2)), numeric.group(3), now.toLocalDate());
            } catch (DateTimeException | NumberFormatException e) { badDate = true; }
            consume(remaining, numeric);
        } else if (named.find()) {
            int month = Arrays.asList(MONTHS).indexOf(named.group(2).toLowerCase(Locale.ROOT)) + 1;
            try { day = date(dayNumber(named.group(1)), month, named.group(3), now.toLocalDate()); }
            catch (DateTimeException | NumberFormatException e) { badDate = true; }
            consume(remaining, named);
        } else {
            Matcher relative = RELATIVE.matcher(remaining), weekday = WEEKDAY.matcher(remaining);
            if (relative.find()) {
                String word = relative.group(1).toLowerCase(Locale.ROOT);
                day = now.toLocalDate().plusDays(word.equals("сьогодні") ? 0 : word.equals("завтра") ? 1 : 2);
                consume(remaining, relative);
            } else if (weekday.find()) {
                String word = weekday.group(2).toLowerCase(Locale.ROOT);
                int number = word.startsWith("пон") ? 1 : word.startsWith("вів") ? 2 : word.startsWith("сер") ? 3
                        : word.startsWith("чет") ? 4 : word.startsWith("п") ? 5 : word.startsWith("суб") ? 6 : 7;
                day = now.toLocalDate().with(TemporalAdjusters.nextOrSame(DayOfWeek.of(number)));
                if (weekday.group(1) != null) day = day.plusWeeks(1);
                consume(remaining, weekday);
            }
        }
        if (time == null && !badTime) {
            Matcher hour = HOUR.matcher(remaining);
            if (hour.find()) {
                try { time = LocalTime.of(Integer.parseInt(hour.group(1)), 0); }
                catch (DateTimeException e) { badTime = true; }
                if (consumeDayPart(remaining, hour.end())) { time = null; badTime = true; dayPart = true; }
                consume(remaining, hour);
            }
        }
        if (hasTemporalSpan(remaining)) return new Result(title(remaining), null, null,
                "Знайдено кілька дат або часів — оберіть дату й час вручну");
        String name = title(remaining);
        String warning = badDate ? "Некоректна дата — оберіть її вручну" : dayPart
                ? "Уточніть час доби — оберіть час вручну" : badTime ? "Некоректний час — оберіть його вручну"
                : day == null ? "Оберіть дату" : time == null ? "Оберіть час"
                : name.isEmpty() ? "Додайте назву події" : "Перевірте розпізнані дані";
        return new Result(name, day, time, warning);
    }
}
