package ua.holoscalendar.redmi;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Pattern;

/** Explicit Ukrainian number forms; no stemming of event titles. */
final class UkrainianNumbers {
    private static final Map<String,Integer> VALUES = new HashMap<>();
    private static final Map<String,Integer> ORDINALS = new HashMap<>();
    static {
        String[] cards={"нуль", "один|одна|одну|однієї", "два|дві|двох", "три|трьох", "чотири|чотирьох",
                "п'ять|п'яти", "шість|шести", "сім|семи", "вісім|восьми", "дев'ять|дев'яти", "десять|десяти",
                "одинадцять|одинадцяти", "дванадцять|дванадцяти", "тринадцять|тринадцяти", "чотирнадцять|чотирнадцяти",
                "п'ятнадцять|п'ятнадцяти", "шістнадцять|шістнадцяти", "сімнадцять|сімнадцяти", "вісімнадцять|вісімнадцяти", "дев'ятнадцять|дев'ятнадцяти"};
        for(int i=0;i<cards.length;i++) add(VALUES,cards[i],i);
        add(VALUES,"двадцять|двадцяти",20); add(VALUES,"тридцять|тридцяти",30);
        add(VALUES,"сорок|сорока",40); add(VALUES,"п'ятдесят|п'ятдесяти",50); add(VALUES,"шістдесят|шістдесяти",60);
        String[] stems={"перш", "друг", "трет", "четверт", "п'ят", "шост", "сьом", "восьм", "дев'ят", "десят",
                "одинадцят", "дванадцят", "тринадцят", "чотирнадцят", "п'ятнадцят", "шістнадцят", "сімнадцят", "вісімнадцят", "дев'ятнадцят", "двадцят"};
        for(int i=0;i<stems.length;i++) for(String ending:new String[]{"ій","ої","у","а","ого","е","ий","я","ою"}) ORDINALS.put(stems[i]+ending,i+1);
        add(ORDINALS,"третій|третьої|третю|третього|третє|третя|третьою",3);
        for(int n=21;n<=31;n++) {
            int tens=n<30?20:30, ones=n-tens;
            String prefix=tens==20?"двадцять ":"тридцять ";
            if(ones==0) for(String ending:new String[]{"ій","ої","у","а","ого","е","ий"}) ORDINALS.put("тридцят"+ending,30);
            else for(Map.Entry<String,Integer> entry:new ArrayList<>(ORDINALS.entrySet()))
                if(entry.getValue()==ones && !entry.getKey().contains(" ")) ORDINALS.put(prefix+entry.getKey(),n);
        }
    }
    private UkrainianNumbers() { }
    private static void add(Map<String,Integer> target,String forms,int n) { for(String form:forms.split("\\|")) target.put(form,n); }
    static String normalize(String text) { return text.toLowerCase(Locale.ROOT).replace('’','\'').replace('ʼ','\'').trim(); }
    private static String alternatives(Collection<String> values) {
        List<String> forms=new ArrayList<>(values); forms.sort(Comparator.comparingInt(String::length).reversed());
        List<String> escaped=new ArrayList<>();
        for(String form:forms) escaped.add(Pattern.quote(form).replace("'", "\\E['’ʼ]\\Q"));
        return "(?:"+String.join("|",escaped)+")";
    }
    static final String ORDINAL = alternatives(ORDINALS.keySet());
    static final String CARDINAL = alternatives(VALUES.keySet());
    static final String QUANTITY = "(?:[+-]?\\d+(?:[.,]\\d+)?|півтори|півтора|пів|(?:"+CARDINAL+")(?:\\s+(?:"+CARDINAL+"))?(?:\\s+з\\s+половиною)?)";
    static int ordinal(String text) {
        String value=normalize(text);
        if(value.matches("\\d+")) return Integer.parseInt(value);
        Integer n=ORDINALS.get(value); if(n==null) throw new IllegalArgumentException("Unknown ordinal"); return n;
    }
    static BigDecimal quantity(String text) {
        if(text==null) return BigDecimal.ONE;
        String value=normalize(text);
        if(value.matches("[+-]?\\d+(?:[.,]\\d+)?")) return new BigDecimal(value.replace(',','.'));
        if(value.equals("пів")) return new BigDecimal("0.5");
        if(value.equals("півтори")||value.equals("півтора")) return new BigDecimal("1.5");
        boolean half=value.endsWith(" з половиною"); if(half) value=value.substring(0,value.length()-12).trim();
        int total=0; for(String word:value.split("\\s+")) {
            Integer n=VALUES.get(word); if(n==null) throw new IllegalArgumentException("Unknown number"); total+=n;
        }
        return BigDecimal.valueOf(total).add(half?new BigDecimal("0.5"):BigDecimal.ZERO);
    }
}
