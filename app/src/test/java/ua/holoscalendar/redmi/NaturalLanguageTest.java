package ua.holoscalendar.redmi;

import org.junit.*;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(Parameterized.class)
public class NaturalLanguageTest {
    @Parameterized.Parameters(name="{index}: {0}") public static Collection<Object[]> data() throws IOException {
        List<Object[]> cases=new ArrayList<>();
        try(BufferedReader reader=new BufferedReader(new InputStreamReader(NaturalLanguageTest.class.getResourceAsStream("/ukrainian-phrases.tsv"),StandardCharsets.UTF_8))) {
            reader.readLine();String line;while((line=reader.readLine())!=null) {String[] fields=line.split("\t",-1);cases.add(new Object[]{fields[1],fields});}
        }
        return cases;
    }
    private final String[] row;
    public NaturalLanguageTest(String label,String[] row) {this.row=row;}
    @Test public void understandsOrRequestsTheRequiredClarification() {
        VoiceParser.Result result=VoiceParser.parse(row[2],LocalDateTime.of(2026,10,26,10,0));
        assertEquals("date",row[3].equals("-")?null:LocalDate.parse(row[3]),result.date);
        assertEquals("time",row[4].equals("-")?null:LocalTime.parse(row[4]),result.time);
        assertEquals("duration",row[5].equals("-")?null:Integer.valueOf(row[5]),result.durationMinutes);
        assertEquals("recurrence",row[6].equals("-")?"":row[6],result.recurrence);
        assertEquals("all day",Boolean.parseBoolean(row[7]),result.allDay);
        assertEquals("end exclusive",row[8].equals("-")?null:LocalDate.parse(row[8]),result.endDateExclusive);
        assertEquals("confirmation",row[9].contains("C"),result.confirmApproximation);
        assertEquals("duration choice",row[9].contains("D"),result.durationNeedsChoice);
        assertEquals("recurrence choice",row[9].contains("R"),result.recurrenceNeedsChoice);
        assertEquals("time choices",row[9].contains("T")?2:0,result.timeChoices.size());
        assertEquals("title",row[10],result.title);
    }
}
