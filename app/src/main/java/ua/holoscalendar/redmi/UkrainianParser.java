package ua.holoscalendar.redmi;

import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.*;
import java.util.*;
import java.util.regex.*;

/** Consume whole phrases once. The remaining original text becomes the event title. */
final class UkrainianParser {
    private static final String MONTHS="січня|лютого|березня|квітня|травня|червня|липня|серпня|вересня|жовтня|листопада|грудня";
    private static final String WEEKDAY="понеділ(?:ок|ка)|вівтор(?:ок|ка)|серед(?:а|у|и)|четвер(?:га)?|п['’ʼ]ятниц(?:я|ю|і)|субот(?:а|у|и)|неділ(?:я|ю|і)";
    private static final String O=UkrainianNumbers.ORDINAL, Q=UkrainianNumbers.QUANTITY;
    private static final String PART="ранку|дня|вечора|ночі";
    private static final String TIME="(?:\\d{1,2}(?:[.:]\\d{1,2})?|"+O+")";
    private final ZonedDateTime now;
    private final StringBuilder text;
    private final List<String> warnings=new ArrayList<>();
    private LocalDate date,endDate;
    private LocalTime time;
    private Instant instant;
    private Integer duration,reminder;
    private String recurrence="";
    private boolean dateSeen,timeSeen,allDay,confirm,durationChoice,recurrenceChoice,offsetSeen,conflict;
    private final List<LocalTime> choices=new ArrayList<>();
    UkrainianParser(String original,ZonedDateTime now) {
        this.now=now; text=new StringBuilder(original.replaceAll("[\\s\\p{Z}]+"," ").trim());
    }
    private static final java.util.concurrent.ConcurrentMap<String,Pattern> PATTERNS=new java.util.concurrent.ConcurrentHashMap<>();
    private static Pattern p(String value) { return PATTERNS.computeIfAbsent(value,key->Pattern.compile(key,Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE)); }
    private Matcher match(String value) { return p(value).matcher(text); }
    private void erase(Matcher matcher) { for(int i=matcher.start();i<matcher.end();i++) text.setCharAt(i,' '); }
    private void warn(String value) { if(value!=null&&!warnings.contains(value)) warnings.add(value); }
    private static String lower(String value) { return UkrainianNumbers.normalize(value); }
    private static int weekday(String value) {
        String day=lower(value); return day.startsWith("пон")?1:day.startsWith("вів")?2:day.startsWith("сер")?3
                :day.startsWith("чет")?4:day.startsWith("п")?5:day.startsWith("суб")?6:7;
    }
    EventTextParser.Result parse() {
        String fallback="";
        Matcher command=match("^(?:будь\\s+ласка[, ]*\\s*)?(нагадай(?:те)?|заплануй(?:те)?|створи|створіть|додай(?:те)?|постав(?:те)?)(?:\\s+мені)?(?:\\s+(подію|нагадування))?\\b\\s*");
        if(command.find()) {
            boolean notify=lower(command.group(1)).startsWith("нагада")||"нагадування".equalsIgnoreCase(command.group(2));
            if(notify) { reminder=0; fallback="Нагадування"; }
            else if(command.group(2)!=null) fallback="Подія";
            erase(command);
        }
        DurationParser.Result parsedDuration=DurationParser.consume(text);
        duration=parsedDuration.minutes; allDay=parsedDuration.allDay;
        durationChoice=parsedDuration.needsChoice; confirm=parsedDuration.approximate; warn(parsedDuration.warning);
        if(parsedDuration.approximate) warn("Тривалість приблизна — підтвердьте розпізнане значення");
        recurrence(); dates(); offsets(); ranges(); times();
        LegacyParser.Result base=LegacyParser.parse(text.toString(),now);
        if(dateSeen&&base.date!=null || timeSeen&&base.time!=null) conflict=true;
        if(!dateSeen) date=base.date;
        if(!timeSeen) time=base.time;
        if(instant==null) instant=base.instant;
        if(base.warning.contains("кілька")) conflict=true;
        if(base.warning.startsWith("Некорект")) warn(base.warning);
        if(offsetSeen && (base.date!=null || base.time!=null)) conflict=true;
        try {RecurrenceRule.validate(recurrence);} catch(IllegalArgumentException bad) {recurrence="";recurrenceChoice=true;warn("Некоректне правило повторення — оберіть його вручну");}
        if(!recurrence.isEmpty() && date==null && !dateSeen) date=RecurrenceRule.next(recurrence,now.toLocalDate(),now.toLocalDate());
        if(allDay && date!=null && endDate==null) endDate=date.plusDays(1);
        if(allDay && (time!=null||instant!=null)) { confirm=true; warn("Подія на весь день: окремий час буде прибрано — перевірте тип події"); time=null;instant=null; }
        if(parsedDuration.untilEndOfDay) {
            if(date!=null&&time!=null) {
                ZonedDateTime start=instant==null?date.atTime(time).atZone(now.getZone()):instant.atZone(now.getZone());
                long value=java.time.Duration.between(start.toInstant(),date.plusDays(1).atStartOfDay(now.getZone()).toInstant()).toMinutes();
                if(value>0&&value<=1440) duration=(int)value; else { durationChoice=true;warn("Уточніть кінець події"); }
            } else { durationChoice=true;warn("Для «до кінця дня» оберіть дату й час початку"); }
        }
        if(conflict) { date=null;time=null;instant=null;choices.clear();warn("Знайдено кілька дат або часів — оберіть дату й час вручну"); }
        String title=base.title;
        if(title.isEmpty()&&!fallback.isEmpty()) title=fallback;
        if(date==null) warn("Оберіть дату");
        if(!allDay&&time==null) warn(choices.isEmpty()?"Оберіть час":"Уточніть час доби: ранковий чи вечірній варіант");
        if(title.isEmpty()) warn("Додайте назву події");
        if(warnings.isEmpty()) warn("Перевірте розпізнані дані");
        return new EventTextParser.Result(title,date,time,String.join(". ",warnings),instant,duration,allDay,endDate,recurrence,
                confirm,durationChoice,recurrenceChoice,choices,reminder);
    }
    private void recurrence() {
        int count=0;
        Matcher ambiguous=match("\\b(?:двічі\\s+на\\s+тиждень|кожного\\s+другого\\s+(?:"+WEEKDAY+")|через\\s+день)\\b");
        if(ambiguous.find()) { count++;recurrenceChoice=true;warn("Уточніть правило повторення та потрібні дні");erase(ambiguous); }
        Matcher monthly=match("\\bщомісяця\\s+("+O+"|\\d{1,2})\\s+числа\\b");
        if(monthly.find()) { count++;recurrence="M:"+UkrainianNumbers.ordinal(monthly.group(1));erase(monthly); }
        Matcher nth=match("\\bщомісяця\\s+(?:в|у)\\s+(останню|другу|першу|третю|четверту)\\s+("+WEEKDAY+")(?:\\s+місяця)?\\b");
        if(nth.find()) { count++;String n=lower(nth.group(1));recurrence="MN:"+(n.equals("останню")?-1:n.equals("першу")?1:n.equals("другу")?2:n.equals("третю")?3:4)+":"+weekday(nth.group(2));erase(nth); }
        Matcher annual=match("\\bщороку\\s+(\\d{1,2}|"+O+")\\s+("+MONTHS+")\\b");
        if(annual.find()) {
            count++;int day=UkrainianNumbers.ordinal(annual.group(1)),month=month(annual.group(2));
            try { LocalDate.of(2000,month,day);recurrence="Y:"+month+":"+day; }
            catch(DateTimeException bad) { recurrenceChoice=true;warn("Некоректна дата щорічного повторення"); }
            erase(annual);
        }
        Matcher interval=match("\\b(?:кожні|раз\\s+на)\\s+("+Q+")\\s+дн(?:і|ів)\\b");
        while(interval.find()) {
            count++;
            try {
                int days=UkrainianNumbers.quantity(interval.group(1)).intValueExact();
                if(days<1||days>366) throw new IllegalArgumentException();
                recurrence="D:"+days;
            } catch(IllegalArgumentException|ArithmeticException bad) { recurrenceChoice=true;warn("Некоректний інтервал повторення — оберіть його вручну"); }
            erase(interval);
        }
        SortedSet<Integer> days=new TreeSet<>();int previousEnd=-1;
        Matcher weekly=match("\\b(?:що|кожн(?:ої|ого|у|ий)\\s+|кожен\\s+|щотижня\\s+(?:(?:у|в)\\s+)?)("+WEEKDAY+")\\b");
        while(weekly.find()) {
            if(previousEnd>=0 && p("[\\s,]*(?:(?:і|й|та)[\\s,]*)?").matcher(text.substring(previousEnd,weekly.start())).matches())
                for(int i=previousEnd;i<weekly.start();i++) text.setCharAt(i,' ');
            days.add(weekday(weekly.group(1)));previousEnd=weekly.end();erase(weekly);
        }
        if(!days.isEmpty()) { count++;List<String> labels=new ArrayList<>();for(int day:days) labels.add(Integer.toString(day));recurrence="W:"+String.join(",",labels); }
        Matcher ordinary=match("\\b(щодня|щоденно|кожен\\s+день|раз\\s+на\\s+день|кожного\\s+ранку|щовечора|щобудня|по\\s+буднях|щовихідних|тільки\\s+у\\s+вихідні)\\b");
        while(ordinary.find()) {
            count++;String word=lower(ordinary.group());recurrence=word.contains("будн")?"W:1,2,3,4,5":word.contains("вихідн")?"W:6,7":"D:1";
            if(word.contains("ранку")||word.equals("щовечора")) warn("Для повторення вранці чи ввечері оберіть точний час");
            erase(ordinary);
        }
        if(count>1) { recurrence="";recurrenceChoice=true;warn("Знайдено кілька правил повторення — оберіть одне"); }
    }
    private static int month(String word) { return Arrays.asList(MONTHS.split("\\|")).indexOf(lower(word))+1; }
    private LocalDate futureDate(int day,int month,String year) {
        if(year!=null) {
            if(year.length()!=4||Integer.parseInt(year)==0) throw new DateTimeException("Invalid year");
            return LocalDate.of(Integer.parseInt(year),month,day);
        }
        for(int y=now.getYear();y<=now.getYear()+8;y++) {
            try { LocalDate candidate=LocalDate.of(y,month,day);if(!candidate.isBefore(now.toLocalDate())) return candidate; }
            catch(DateTimeException ignored) { }
        }
        throw new DateTimeException("Invalid date");
    }
    private void setDate(LocalDate value) { if(dateSeen) conflict=true;dateSeen=true;date=value; }
    private void dates() {
        Matcher range=match("\\b(?:з|із|від)\\s+(\\d+|"+O+")\\s+(?:по|до)\\s+(\\d+|"+O+")\\s+("+MONTHS+")(?:\\s+(\\d+))?\\b");
        if(range.find()) {
            try {
                int a=UkrainianNumbers.ordinal(range.group(1)),b=UkrainianNumbers.ordinal(range.group(2));
                LocalDate first=futureDate(a,month(range.group(3)),range.group(4));
                if(b<a) throw new DateTimeException("Reverse range");
                setDate(first);endDate=first.withDayOfMonth(b).plusDays(1);allDay=true;duration=null;
            } catch(IllegalArgumentException|DateTimeException bad) { setDate(null);warn("Некоректний діапазон дат — оберіть дати вручну"); }
            erase(range);
        }
        Matcher weekDate=match("\\b(?:(?:у|в|на)\\s+)?("+WEEKDAY+")\\s+(цього|наступного)\\s+тижня\\b");
        while(weekDate.find()) {
            LocalDate monday=now.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            if(lower(weekDate.group(2)).equals("наступного")) monday=monday.plusWeeks(1);
            setDate(monday.plusDays(weekday(weekDate.group(1))-1));erase(weekDate);
        }
        Matcher calendarOffset=match("\\b(?:через|за)\\s+(?:("+Q+")\\s+)?(місяць|місяця|місяці|місяців|рік|року|роки|років)\\b");
        while(calendarOffset.find()) {
            try {
                int amount=UkrainianNumbers.quantity(calendarOffset.group(1)).intValueExact();
                boolean months=lower(calendarOffset.group(2)).startsWith("місяц");
                if(amount<1||amount>(months?120:10)) throw new IllegalArgumentException();
                LocalDate target=months?now.toLocalDate().plusMonths(amount):now.toLocalDate().plusYears(amount);
                setDate(target);
                if(target.getDayOfMonth()!=now.getDayOfMonth()) { confirm=true;warn("У вибраному місяці немає такого числа — підтвердьте останній день місяця"); }
            } catch(IllegalArgumentException|ArithmeticException|DateTimeException bad) { setDate(null);warn("Некоректний проміжок місяців або років"); }
            erase(calendarOffset);
        }
        Matcher offset=match("\\b(?:через|за)\\s+(?:("+Q+")\\s+)?(день|дні|днів|тиждень|тижні|тижнів)\\b");
        while(offset.find()) {
            String prefix=text.substring(0,offset.start()).trim();
            if(p("(?iu)(?:"+WEEKDAY+")$").matcher(prefix).find()) continue;
            try {
                int amount=UkrainianNumbers.quantity(offset.group(1)).intValueExact();
                if(amount<1||amount>3660) throw new IllegalArgumentException("Offset out of range");
                setDate(now.toLocalDate().plusDays((long)amount*(lower(offset.group(2)).startsWith("тиж")?7:1)));
            } catch(IllegalArgumentException|ArithmeticException bad) { setDate(null);warn("Некоректний проміжок днів"); }
            erase(offset);
        }
        Matcher named=match("\\b(?:(?:у|в|на)\\s+)?("+O+")\\s+("+MONTHS+")(?:\\s+(\\d+))?\\b");
        while(named.find()) {
            try { setDate(futureDate(UkrainianNumbers.ordinal(named.group(1)),month(named.group(2)),named.group(3))); }
            catch(IllegalArgumentException|DateTimeException bad) { setDate(null);warn("Некоректна дата — оберіть її вручну"); }
            erase(named);
        }
        Matcher weekday=match("\\b(?:(?:у|в|на)\\s+)?(?:(цієї|цього|найближчу|найближчої|найближчий|наступний)\\s+)?("+WEEKDAY+")(\\s+через\\s+тиждень)?\\b");
        while(weekday.find()) {
            // Preserve the legacy meaning of «наступного понеділка»: one week beyond nearest.
            String prefix=lower(text.substring(0,weekday.start()).trim());
            if(prefix.endsWith("наступного")||prefix.endsWith("наступної")||prefix.endsWith("наступну")) continue;
            if(weekday.group(1)==null&&weekday.group(3)==null) continue;
            LocalDate target=now.toLocalDate().with(TemporalAdjusters.nextOrSame(DayOfWeek.of(weekday(weekday.group(2)))));
            if(weekday.group(3)!=null||"наступний".equalsIgnoreCase(weekday.group(1))) target=target.plusWeeks(1);
            setDate(target);erase(weekday);
        }
        Matcher lastDay=match("\\b(?:(?:в|у|на)\\s+)?останн(?:ій|ього)\\s+день\\s+(?:(наступного|цього)\\s+)?місяця\\b");
        if(lastDay.find()) { YearMonth m=YearMonth.from(now);if("наступного".equalsIgnoreCase(lastDay.group(1))) m=m.plusMonths(1);setDate(m.atEndOfMonth());erase(lastDay); }
        Matcher lastWeekday=match("\\b(?:в|у)\\s+останню\\s+("+WEEKDAY+")\\s+місяця\\b");
        if(lastWeekday.find()) {
            LocalDate target=now.toLocalDate().with(TemporalAdjusters.lastInMonth(DayOfWeek.of(weekday(lastWeekday.group(1)))));
            if(target.isBefore(now.toLocalDate())) target=target.plusMonths(1).with(TemporalAdjusters.lastInMonth(target.getDayOfWeek()));
            setDate(target);confirm=true;warn("Останній день тижня в місяці розпізнано як одну подію; для серії оберіть повторення");erase(lastWeekday);
        }
        Matcher vague=match("\\b(?:наприкінці\\s+(?:цього\\s+)?місяця|на\\s+початку\\s+наступного\\s+місяця|наступного\\s+року)\\b");
        while(vague.find()) { setDate(null);warn("Цей вираз не задає точної дати — оберіть її вручну");erase(vague); }
    }
    private void offsets() {
        Matcher offset=match("\\b(?:через|за)\\s+(?:("+Q+")\\s*)?(хвилин(?:к[ауи]|а|у|и)?|хв|годин(?:к[ауи]|а|у|и)?|год)\\b\\.?(?:\\s+(?:(?:і|та)\\s+)?("+Q+")\\s*(?:хвилин(?:а|у|и)?|хв)\\b\\.?)?");
        while(offset.find()) {
            if(offsetSeen||dateSeen||timeSeen) conflict=true;
            offsetSeen=true;dateSeen=true;timeSeen=true;
            try {
                boolean hours=lower(offset.group(2)).startsWith("год");
                BigDecimal total=UkrainianNumbers.quantity(offset.group(1)).multiply(BigDecimal.valueOf(hours?60:1));
                if(offset.group(3)!=null) {
                    if(!hours) throw new IllegalArgumentException("Repeated minute unit");
                    total=total.add(UkrainianNumbers.quantity(offset.group(3)));
                }
                int minutes=total.intValueExact();
                if(minutes<1||minutes>10080) throw new IllegalArgumentException("Invalid offset");
                ZonedDateTime target=now.plusMinutes(minutes);if(target.getSecond()!=0||target.getNano()!=0) target=target.plusMinutes(1);
                target=target.withSecond(0).withNano(0); date=target.toLocalDate();time=target.toLocalTime();instant=target.toInstant();
            } catch(IllegalArgumentException|ArithmeticException bad) { date=null;time=null;instant=null;warn("Некоректний проміжок часу — оберіть дату й час вручну"); }
            erase(offset);
        }
    }
    private Integer hour(String token,String part) {
        int h=token.matches("\\d+")?Integer.parseInt(token):UkrainianNumbers.ordinal(token);
        if(h<0||h>23) return null;
        if(part==null) return h;
        String period=lower(part);
        if(h>12) return period.equals("ранку")||period.equals("ночі")?null:h;
        if(period.equals("ранку")) return h==12?null:h;
        if(period.equals("дня")) return h==12?12:h>=1&&h<=6?h+12:null;
        if(period.equals("вечора")) return h==12?null:h>=1?h+12:null;
        return h==12?0:h<=5?h:h+12;
    }
    private LocalTime exact(String token,String part) {
        String[] pieces=token.split("[.:]");Integer hour=hour(pieces[0],part);
        if(pieces.length>1&&pieces[1].length()!=2) throw new DateTimeException("Use two minute digits");
        if(hour==null) return null;
        if(part==null && !token.matches("\\d.*") && hour<=12) return null;
        return LocalTime.of(hour,pieces.length>1?Integer.parseInt(pieces[1]):0);
    }
    private void setTime(LocalTime value) { if(timeSeen) conflict=true;timeSeen=true;time=value; }
    private void ranges() {
        Matcher window=match("\\b(?:між\\s+(?:"+TIME+")\\s+та\\s+(?:"+TIME+")|після\\s+(?:"+O+")\\s*,?\\s*але\\s+до\\s+(?:"+O+")|від\\s+обіду\\s+до\\s+вечора|з\\s+ранку\\s+до\\s+обіду)\\b");
        while(window.find()) { setTime(null);durationChoice=true;warn("Це вікно можливого часу — оберіть початок і тривалість");erase(window); }
        Matcher range=match("\\b(?:з|із|від)\\s+("+TIME+")(?:\\s+("+PART+"))?\\s+(?:до|по)\\s+("+TIME+")(?:\\s+("+PART+"))?\\b");
        while(range.find()) {
            String firstPart=range.group(2),lastPart=range.group(4);
            try {
                LocalTime start=exact(range.group(1),firstPart==null?lastPart:firstPart),end=exact(range.group(3),lastPart);
                if(start==null||end==null) { setTime(null);durationChoice=true;warn("Уточніть час доби початку й кінця проміжку"); }
                else {
                    int minutes=(int)java.time.Duration.between(start,end).toMinutes();if(minutes<=0) minutes+=1440;
                    if(duration!=null&&duration!=minutes) { duration=null;durationChoice=true;warn("Тривалість суперечить часовому проміжку — оберіть її вручну"); }
                    else duration=minutes;
                    setTime(start);
                }
            } catch(IllegalArgumentException|DateTimeException bad) { setTime(null);warn("Некоректний часовий проміжок"); }
            erase(range);
        }
        Matcher bound=match("\\b(?:не\\s+раніше|не\\s+пізніше|до)\\s+("+TIME+")(?:\\s+(?:"+PART+"))?\\b");
        while(bound.find()) { setTime(null);warn("Часова межа не задає початку події — оберіть точний час");erase(bound); }
    }
    private void fractional(String token,int minute,String part) {
        int h=UkrainianNumbers.ordinal(token);
        if(part==null && h<=12) {
            int previous=(h+11)%12;setTime(null);
            choices.add(LocalTime.of(previous,minute));choices.add(LocalTime.of(previous+12,minute));
        } else {
            Integer target=hour(token,part);
            setTime(target==null?null:LocalTime.of((target+23)%24,minute));
        }
    }

    private void times() {
        Matcher vague=match("\\b(?:після\\s+обіду|під\\s+вечір)\\b");
        while(vague.find()) { setTime(null);warn("Оберіть точний час для цієї частини дня");erase(vague); }
        Matcher special=match("\\b(опівдні|опівночі)\\b");
        while(special.find()) { setTime(lower(special.group()).equals("опівдні")?LocalTime.NOON:LocalTime.MIDNIGHT);erase(special); }
        Matcher half=match("\\b(?:о\\s+)?(пів|чверть)\\s+на\\s+("+O+")(?:\\s+("+PART+"))?\\b");
        while(half.find()) { fractional(half.group(2),lower(half.group(1)).equals("пів")?30:15,half.group(3));erase(half); }
        Matcher halfOf=match("\\b(?:о\\s+)?пів\\s+("+O+")(?:\\s+("+PART+"))?\\b");
        while(halfOf.find()) { fractional(halfOf.group(1),30,halfOf.group(2));erase(halfOf); }
        Matcher before=match("\\b(?:о\\s+)?(?:за\\s+чверть|без\\s+десяти)\\s+("+O+")(?:\\s+("+PART+"))?\\b");
        while(before.find()) { fractional(before.group(1),lower(before.group()).contains("чверть")?45:50,before.group(2));erase(before); }
        Matcher minute=match("\\bо\\s+("+Q+")\\s+хвилин\\s+на\\s+("+O+")(?:\\s+("+PART+"))?\\b");
        while(minute.find()) {
            try { int value=UkrainianNumbers.quantity(minute.group(1)).intValueExact();if(value<1||value>59) throw new IllegalArgumentException();fractional(minute.group(2),value,minute.group(3)); }
            catch(IllegalArgumentException|ArithmeticException bad) { setTime(null);warn("Некоректна кількість хвилин"); }
            erase(minute);
        }
        Matcher approximate=match("\\b(близько\\s+(?:"+O+")|десь\\s+(?:о|об)\\s+(?:"+TIME+"))\\b");
        while(approximate.find()) {
            String token=approximate.group().replaceFirst("(?iu)^(?:близько|десь\\s+(?:о|об))\\s+","");
            try {
                LocalTime value=exact(token,null);setTime(value);
                if(value==null) { int h=UkrainianNumbers.ordinal(token)%12;choices.add(LocalTime.of(h,0));choices.add(LocalTime.of(h+12,0)); }
            } catch(IllegalArgumentException|DateTimeException bad) { setTime(null); }
            confirm=true;warn("Час приблизний — перевірте й підтвердьте його");erase(approximate);
        }
        Matcher direct=match("\\b(?:о|об)\\s+("+TIME+")(\\s+годині)?(?:\\s+("+PART+"))?\\b");
        while(direct.find()) {
            String token=direct.group(1),part=direct.group(3);
            boolean numeric=token.matches("\\d.*");
            if(part==null && direct.group(2)==null && numeric) continue; // Strict legacy numeric validation.
            try {
                LocalTime value=exact(token,part);setTime(value);
                if(value==null&&part==null&&!numeric) { int h=UkrainianNumbers.ordinal(token)%12;choices.add(LocalTime.of(h,0));choices.add(LocalTime.of(h+12,0)); }
                else if(value==null) warn("Час суперечить частині доби або має некоректне значення — уточніть його");
            } catch(IllegalArgumentException|DateTimeException bad) { setTime(null);warn("Некоректний час — оберіть його вручну"); }
            erase(direct);
        }
    }
}
