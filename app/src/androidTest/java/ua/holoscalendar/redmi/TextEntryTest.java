package ua.holoscalendar.redmi;

import android.widget.EditText;
import android.widget.TextView;
import android.database.DatabaseUtils;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.Arrays;
import java.util.TimeZone;
import java.time.LocalTime;
import java.time.LocalDateTime;
import static org.junit.Assert.*;

/** Exercises the same text input and button used by people, on Android's regex runtime. */
@RunWith(AndroidJUnit4.class)
public class TextEntryTest {
    private void enter(EventEditorActivity activity,String text) {
        ((EditText)activity.findViewById(R.id.raw)).setText(text);
        activity.findViewById(R.id.parse).performClick();
    }
    private String repeated(char value,int count) {
        char[] text=new char[count]; Arrays.fill(text,value); return new String(text);
    }
    private long eventCount(EventEditorActivity activity) {
        try (EventStore store=new EventStore(activity)) {
            return DatabaseUtils.queryNumEntries(store.getReadableDatabase(),"events");
        }
    }
    @Test public void textPopulatesFormWithoutClosing() {
        try(ActivityScenario<EventEditorActivity> screen=ActivityScenario.launch(EventEditorActivity.class)) {
            screen.onActivity(activity->{
                enter(activity,"Завтра о 14:30 Зустріч у Києві");
                assertEquals("Зустріч у Києві",((EditText)activity.findViewById(R.id.name)).getText().toString());
                assertTrue(((TextView)activity.findViewById(R.id.time)).getText().toString().contains("14:30"));
                assertFalse(activity.isFinishing());
            });
        }
    }
    @Test public void parsedFormSurvivesRecreation() {
        try(ActivityScenario<EventEditorActivity> screen=ActivityScenario.launch(EventEditorActivity.class)) {
            screen.onActivity(activity->enter(activity,"Завтра о 9 Нарада"));
            screen.recreate();
            screen.onActivity(activity->assertEquals("Нарада",((EditText)activity.findViewById(R.id.name)).getText().toString()));
        }
    }
    @Test public void emptyInputDoesNotReplaceManualDetails() {
        try(ActivityScenario<EventEditorActivity> screen=ActivityScenario.launch(EventEditorActivity.class)) {
            screen.onActivity(activity->{
                ((EditText)activity.findViewById(R.id.name)).setText("Моя подія");
                enter(activity," ");
                assertEquals("Моя подія",((EditText)activity.findViewById(R.id.name)).getText().toString());
                assertNotNull(((EditText)activity.findViewById(R.id.raw)).getError());
            });
        }
    }
    @Test public void longPasteIsPreservedAndCannotReplaceOrSavePreviousDetails() {
        try(ActivityScenario<EventEditorActivity> screen=ActivityScenario.launch(EventEditorActivity.class)) {
            screen.onActivity(activity->{
                enter(activity,"Завтра о 14:30 Нарада на 45 хвилин");
                ((EditText)activity.findViewById(R.id.name)).setText("Моя подія");
                String complete="Завтра о 15:00 "+repeated('а',1001)+" на годину";
                enter(activity,complete);
                assertEquals(complete,((EditText)activity.findViewById(R.id.raw)).getText().toString());
                assertNotNull(((EditText)activity.findViewById(R.id.raw)).getError());
                assertEquals("Моя подія",((EditText)activity.findViewById(R.id.name)).getText().toString());
                assertTrue(((TextView)activity.findViewById(R.id.time)).getText().toString().contains("14:30"));
                assertTrue(((TextView)activity.findViewById(R.id.duration)).getText().toString().contains("45"));
                long previousCount=eventCount(activity);
                activity.findViewById(R.id.save_local).performClick();
                assertEquals(previousCount,eventCount(activity));
                assertEquals(activity.getString(R.string.description_too_long,1000),
                        ((TextView)activity.findViewById(R.id.status)).getText().toString());
                assertFalse(activity.isFinishing());
            });
        }
    }
    @Test public void descriptionOfExactlyOneThousandCharactersCanBeParsed() {
        try(ActivityScenario<EventEditorActivity> screen=ActivityScenario.launch(EventEditorActivity.class)) {
            screen.onActivity(activity->{
                String prefix="Завтра о 14:30 ";String title=repeated('а',1000-prefix.length());
                enter(activity,prefix+title);
                assertNull(((EditText)activity.findViewById(R.id.raw)).getError());
                assertEquals(title,((EditText)activity.findViewById(R.id.name)).getText().toString());
            });
        }
    }
    @Test public void longDescriptionSurvivesRecreationAndCanBeShortened() {
        String complete="Завтра о 15:00 "+repeated('а',1001)+" кінець";
        try(ActivityScenario<EventEditorActivity> screen=ActivityScenario.launch(EventEditorActivity.class)) {
            screen.onActivity(activity->enter(activity,complete));
            screen.recreate();
            screen.onActivity(activity->{
                assertEquals(complete,((EditText)activity.findViewById(R.id.raw)).getText().toString());
                enter(activity,"Завтра о 15:00 Нова подія");
                assertNull(((EditText)activity.findViewById(R.id.raw)).getError());
                assertEquals("Нова подія",((EditText)activity.findViewById(R.id.name)).getText().toString());
            });
        }
    }
    @Test public void longTitleIsNotTruncatedAtTwoHundredCharacters() {
        try(ActivityScenario<EventEditorActivity> screen=ActivityScenario.launch(EventEditorActivity.class)) {
            screen.onActivity(activity->{
                String title=repeated('а',300); enter(activity,"Завтра о 9 "+title);
                assertEquals(title,((EditText)activity.findViewById(R.id.name)).getText().toString());
            });
        }
    }
    @Test public void revertingSourceKeepsManualEditsAfterRecreation() {
        try(ActivityScenario<EventEditorActivity> screen=ActivityScenario.launch(EventEditorActivity.class)) {
            screen.onActivity(activity->{enter(activity,"Завтра Нарада");((EditText)activity.findViewById(R.id.name)).setText("Моя виправлена назва");});
            screen.recreate();
            screen.onActivity(activity->{
                EditText raw=activity.findViewById(R.id.raw);raw.setText("Інший текст");raw.setText("Завтра Нарада");
                activity.findViewById(R.id.save).performClick();
                assertNull(raw.getError());
                assertEquals(activity.getString(R.string.choose_time),((TextView)activity.findViewById(R.id.status)).getText().toString());
                assertEquals("Моя виправлена назва",((EditText)activity.findViewById(R.id.name)).getText().toString());
            });
        }
    }
    @Test public void relativeInstantSurvivesRecreationAndTimezoneChange() {
        TimeZone original=TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            try(ActivityScenario<EventEditorActivity> screen=ActivityScenario.launch(EventEditorActivity.class)) {
                String[] expected=new String[1];
                screen.onActivity(activity->{
                    enter(activity,"Через годину Нарада");
                    String displayed=((TextView)activity.findViewById(R.id.time)).getText().toString();
                    LocalTime utc=LocalTime.parse(displayed.substring(displayed.length()-5));
                    expected[0]=activity.getString(R.string.time_value,utc.plusHours(2).toString());
                });
                TimeZone.setDefault(TimeZone.getTimeZone("GMT+02:00"));screen.recreate();
                screen.onActivity(activity->assertEquals(expected[0],((TextView)activity.findViewById(R.id.time)).getText().toString()));
            }
        } finally {TimeZone.setDefault(original);}
    }
    @Test public void parserValidationWorksOnAndroid() {
        LocalDateTime now=LocalDateTime.of(2026,10,23,10,0);
        assertNull(EventTextParser.parse("125 жовтня о 9 Нарада",now).date);
        assertNull(EventTextParser.parse("25 жовтня 20270 о 9 Нарада",now).date);
        assertEquals("Нарада",EventTextParser.parse("Нарада на 05.11 о 9",now).title);
        assertEquals("Нарада",EventTextParser.parse("Завтра о\u00a014:30 Нарада",now).title);
        assertEquals(LocalTime.of(21,0),EventTextParser.parse("Завтра о 9 вечора Нарада",now).time);
    }
}
