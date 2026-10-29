package ua.holoscalendar.redmi;

import java.time.*;
import java.time.temporal.*;
import java.util.*;

/** Bounded date expansion, preserving local wall time; invalid monthly dates are skipped. */
final class RecurrenceRule {
    static final String NONE="";
    private RecurrenceRule() { }
    static void validate(String rule) {
        if(rule==null||rule.isEmpty()) return;
        String[] p=rule.split(":");
        try {
            if(p[0].equals("D") && p.length==2 && Integer.parseInt(p[1])>=1 && Integer.parseInt(p[1])<=366) return;
            if((p[0].equals("W")||p[0].equals("W2")) && p.length==2) {
                for(String day:p[1].split(",",-1)) if(Integer.parseInt(day)<1||Integer.parseInt(day)>7) throw new IllegalArgumentException();
                return;
            }
            if(p[0].equals("M") && p.length==2 && Integer.parseInt(p[1])>=1 && Integer.parseInt(p[1])<=31) return;
            if(p[0].equals("MN") && p.length==3 && (Integer.parseInt(p[1])==-1||Integer.parseInt(p[1])>=1&&Integer.parseInt(p[1])<=4)
                    && Integer.parseInt(p[2])>=1 && Integer.parseInt(p[2])<=7) return;
            if(p[0].equals("Y") && p.length==3) { LocalDate.of(2000,Integer.parseInt(p[1]),Integer.parseInt(p[2])); return; }
        } catch (RuntimeException invalid) { throw new IllegalArgumentException("Invalid recurrence",invalid); }
        throw new IllegalArgumentException("Invalid recurrence");
    }
    static boolean matches(String rule,LocalDate anchor,LocalDate day) {
        if(day.isBefore(anchor)) return false;
        if(rule==null||rule.isEmpty()) return anchor.equals(day);
        String[] p=rule.split(":");
        switch(p[0]) {
            case "D": return ChronoUnit.DAYS.between(anchor,day)%Integer.parseInt(p[1])==0;
            case "W": case "W2":
                boolean included=Arrays.asList(p[1].split(",")).contains(Integer.toString(day.getDayOfWeek().getValue()));
                LocalDate week=anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                return included && (!p[0].equals("W2")||ChronoUnit.WEEKS.between(week,day)%2==0);
            case "M": return day.getDayOfMonth()==Integer.parseInt(p[1]);
            case "MN":
                DayOfWeek weekday=DayOfWeek.of(Integer.parseInt(p[2]));
                LocalDate target=Integer.parseInt(p[1])==-1?day.with(TemporalAdjusters.lastInMonth(weekday))
                        :day.with(TemporalAdjusters.dayOfWeekInMonth(Integer.parseInt(p[1]),weekday));
                return day.equals(target);
            case "Y": return day.getMonthValue()==Integer.parseInt(p[1])&&day.getDayOfMonth()==Integer.parseInt(p[2]);
            default: return false;
        }
    }
    static LocalDate next(String rule,LocalDate anchor,LocalDate from) {
        validate(rule);
        LocalDate day=from.isBefore(anchor)?anchor:from;
        if(rule==null||rule.isEmpty()) return anchor.isBefore(from)?null:anchor;
        // Includes leap-day series and every supported interval, with a hard upper bound.
        for(int i=0;i<8*366;i++,day=day.plusDays(1)) if(matches(rule,anchor,day)) return day;
        return null;
    }
    static String rrule(String rule) {
        validate(rule);if(rule==null||rule.isEmpty()) return "";
        String[] p=rule.split(":");String[] weekdays={"MO","TU","WE","TH","FR","SA","SU"};
        if(p[0].equals("D")) return "FREQ=DAILY;INTERVAL="+p[1];
        if(p[0].equals("W")||p[0].equals("W2")) {List<String> days=new ArrayList<>();for(String day:p[1].split(",")) days.add(weekdays[Integer.parseInt(day)-1]);return "FREQ=WEEKLY;INTERVAL="+(p[0].equals("W2")?2:1)+";BYDAY="+String.join(",",days);}
        if(p[0].equals("M")) return "FREQ=MONTHLY;BYMONTHDAY="+p[1];
        if(p[0].equals("MN")) return "FREQ=MONTHLY;BYDAY="+p[1]+weekdays[Integer.parseInt(p[2])-1];
        return "FREQ=YEARLY;BYMONTH="+p[1]+";BYMONTHDAY="+p[2];
    }
    static String label(String rule) {
        if(rule==null||rule.isEmpty()) return "Не повторювати";
        if(rule.equals("D:1")) return "Щодня";
        if(rule.equals("D:2")) return "Кожні два дні";
        if(rule.equals("W:1,2,3,4,5")) return "По буднях";
        if(rule.equals("W:6,7")) return "У вихідні";
        String[] p=rule.split(":");
        if(p[0].equals("D")) return "Кожні "+p[1]+" днів";
        if(p[0].equals("W")||p[0].equals("W2")) {
            String[] days={"пн","вт","ср","чт","пт","сб","нд"}; List<String> names=new ArrayList<>();
            for(String value:p[1].split(",")) names.add(days[Integer.parseInt(value)-1]);
            return (p[0].equals("W2")?"Кожні 2 тижні: ":"Щотижня: ")+String.join(", ",names);
        }
        if(p[0].equals("M")) return "Щомісяця, "+p[1]+" числа";
        if(p[0].equals("MN")) {
            int day=Integer.parseInt(p[2]);boolean female=day==3||day>=5;
            String[] male={"перший","другий","третій","четвертий","останній"},fem={"перша","друга","третя","четверта","остання"};
            String[] names={"понеділок","вівторок","середа","четвер","п’ятниця","субота","неділя"};
            int position=p[1].equals("-1")?4:Integer.parseInt(p[1])-1;
            return "Щомісяця: "+(female?fem:male)[position]+" "+names[day-1];
        }
        return "Щороку: "+p[2]+"."+p[1];
    }
}
