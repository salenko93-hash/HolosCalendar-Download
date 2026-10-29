package ua.holoscalendar.redmi;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.*;

/** Duration is consumed separately from start offsets such as «за пів години». */
final class DurationParser {
    private DurationParser() { }
    static final class Result {
        final Integer minutes;
        final String warning;
        final boolean approximate, allDay, untilEndOfDay, needsChoice;
        Result(Integer minutes,String warning,boolean approximate,boolean allDay,boolean untilEndOfDay,boolean needsChoice) {
            this.minutes=minutes; this.warning=warning; this.approximate=approximate;
            this.allDay=allDay; this.untilEndOfDay=untilEndOfDay; this.needsChoice=needsChoice;
        }
    }
    private static Pattern pattern(String value) { return Pattern.compile(value,Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE); }
    private static final String MINUTE="(?:хвилин(?:к[ауи]|а|у|и)?|хв)";
    private static final String HOUR="(?:годин(?:к[ауи]|а|у|и)?|год)";
    private static final String Q=UkrainianNumbers.QUANTITY;
    private static final Pattern DURATION=pattern("(?<![\\p{L}\\d])(?:(приблизно|десь)\\s+)?(?:на|тривалістю|триватиме|протягом|тривалість\\s*:?)\\s+"
            +"(?:("+Q+")\\s*)?("+HOUR+"|"+MINUTE+")\\b\\.?(?:\\s+(?:і\\s+)?("+Q+")\\s*("+MINUTE+")\\b\\.?)?");
    private static final Pattern REVERSED=pattern("\\bхвилин\\s+на\\s+("+Q+")\\b");
    private static final Pattern VAGUE=pattern("\\b(?:на\\s+кілька\\s+хвилин|до\\s+кінця\\s+дня)\\b");
    private static final Pattern ALL_DAY=pattern("\\bна\\s+весь\\s+день\\b");
    private static final Pattern BARE=pattern("(?<![\\p{L}\\d])("+Q+")\\s*("+HOUR+"|"+MINUTE+")\\b\\.?");
    private static void erase(StringBuilder text,Matcher match) { for(int i=match.start();i<match.end();i++) text.setCharAt(i,' '); }
    static Result consume(StringBuilder text) {
        Integer minutes=null; int count=0; boolean invalid=false,approximate=false,allDay=false,until=false,choice=false;
        Matcher special=ALL_DAY.matcher(text);
        if(special.find()) { allDay=true; erase(text,special); count++; }
        Matcher vague=VAGUE.matcher(text);
        if(vague.find()) {
            until=UkrainianNumbers.normalize(vague.group()).startsWith("до"); choice=!until;
            erase(text,vague); count++;
        }
        Matcher normal=DURATION.matcher(text);
        while(normal.find()) {
            count++; approximate|=normal.group(1)!=null;
            try {
                boolean hours=normal.group(3).toLowerCase(Locale.ROOT).startsWith("год");
                BigDecimal total=UkrainianNumbers.quantity(normal.group(2)).multiply(BigDecimal.valueOf(hours?60:1));
                if(normal.group(4)!=null) {
                    if(!hours) throw new IllegalArgumentException("Repeated minute unit");
                    total=total.add(UkrainianNumbers.quantity(normal.group(4)));
                }
                minutes=total.intValueExact(); if(minutes<1||minutes>1440) invalid=true;
            } catch(IllegalArgumentException|ArithmeticException bad) { invalid=true; }
            erase(text,normal);
        }
        Matcher reverse=REVERSED.matcher(text);
        while(reverse.find()) {
            count++; approximate=true;
            try { minutes=UkrainianNumbers.quantity(reverse.group(1)).intValueExact(); if(minutes<1||minutes>1440) invalid=true; }
            catch(IllegalArgumentException|ArithmeticException bad) { invalid=true; }
            erase(text,reverse);
        }
        Matcher bare=BARE.matcher(text);
        while(bare.find()) {
            String prefix=text.substring(0,bare.start()).trim().toLowerCase(Locale.ROOT);
            if(prefix.matches("(?s).*\\b(?:через|за|о|об)$")) continue;
            count++;
            try { minutes=UkrainianNumbers.quantity(bare.group(1)).multiply(BigDecimal.valueOf(bare.group(2).toLowerCase(Locale.ROOT).startsWith("год")?60:1)).intValueExact();
                if(minutes<1||minutes>1440) invalid=true;
            } catch(IllegalArgumentException|ArithmeticException bad) { invalid=true; }
            erase(text,bare);
        }
        if(count>1) return new Result(null,"Знайдено кілька тривалостей — оберіть одну вручну",false,false,false,true);
        if(invalid) return new Result(null,"Некоректна тривалість — оберіть від 1 до 1440 хвилин вручну",false,false,false,true);
        return new Result(minutes,choice?"Уточніть тривалість: «кілька хвилин» не задає точного значення":null,approximate,allDay,until,choice);
    }
}
