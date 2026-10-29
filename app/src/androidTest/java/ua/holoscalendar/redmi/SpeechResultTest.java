package ua.holoscalendar.redmi;

import android.app.Activity;
import android.content.Intent;
import android.speech.RecognizerIntent;
import android.widget.EditText;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.TimeZone;
import java.time.LocalTime;
import java.time.LocalDateTime;
import static org.junit.Assert.*;

/** Runs on Android's actual regex implementation and Activity lifecycle. No speech service needed. */
@RunWith(AndroidJUnit4.class)
public class SpeechResultTest {
    private Intent result(String text) {
        return new Intent().putStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS,
                new ArrayList<>(Arrays.asList(text)));
    }

    @Test public void successfulSpeechResultPopulatesFormWithoutClosing() {
        try (ActivityScenario<EventEditorActivity> scenario = ActivityScenario.launch(EventEditorActivity.class)) {
            scenario.onActivity(activity -> {
                activity.onActivityResult(100, Activity.RESULT_OK, result("Завтра о 14:30 Зустріч у Києві"));
                assertEquals("Зустріч у Києві", ((EditText) activity.findViewById(R.id.name)).getText().toString());
                assertTrue(((TextView) activity.findViewById(R.id.time)).getText().toString().contains("14:30"));
                assertFalse(activity.isFinishing());
            });
        }
    }

    @Test public void cancelledSpeechPreservesExistingForm() {
        try (ActivityScenario<EventEditorActivity> scenario = ActivityScenario.launch(EventEditorActivity.class)) {
            scenario.onActivity(activity -> {
                ((EditText) activity.findViewById(R.id.name)).setText("Моя подія");
                activity.onActivityResult(100, Activity.RESULT_CANCELED, null);
                assertEquals("Моя подія", ((EditText) activity.findViewById(R.id.name)).getText().toString());
                assertFalse(activity.isFinishing());
            });
        }
    }

    @Test public void speechResultWorksAfterRecreation() {
        try (ActivityScenario<EventEditorActivity> scenario = ActivityScenario.launch(EventEditorActivity.class)) {
            scenario.recreate();
            scenario.onActivity(activity -> {
                activity.onActivityResult(100, Activity.RESULT_OK, result("Завтра о 9 Нарада"));
                assertEquals("Нарада", ((EditText) activity.findViewById(R.id.name)).getText().toString());
                assertFalse(activity.isFinishing());
            });
            scenario.recreate();
            scenario.onActivity(activity -> assertEquals("Нарада",
                    ((EditText) activity.findViewById(R.id.name)).getText().toString()));
        }
    }
    @Test public void emptySpeechResultPreservesExistingForm() {
        try (ActivityScenario<EventEditorActivity> scenario = ActivityScenario.launch(EventEditorActivity.class)) {
            scenario.onActivity(activity -> {
                ((EditText) activity.findViewById(R.id.name)).setText("Моя подія");
                activity.onActivityResult(100, Activity.RESULT_OK, result(" "));
                assertEquals("Моя подія", ((EditText) activity.findViewById(R.id.name)).getText().toString());
                assertFalse(activity.isFinishing());
            });
        }
    }
    private String repeated(char value, int count) {
        char[] text = new char[count]; Arrays.fill(text, value); return new String(text);
    }

    @Test public void longSpeechIsRejectedWithoutTruncatingExistingForm() {
        try (ActivityScenario<EventEditorActivity> scenario = ActivityScenario.launch(EventEditorActivity.class)) {
            scenario.onActivity(activity -> {
                EditText raw = activity.findViewById(R.id.raw);
                raw.setText("Попередній текст");
                ((EditText) activity.findViewById(R.id.name)).setText("Моя подія");
                activity.onActivityResult(100, Activity.RESULT_OK, result(repeated('а', 1001)));
                assertEquals("Попередній текст", raw.getText().toString());
                assertEquals("Моя подія", ((EditText) activity.findViewById(R.id.name)).getText().toString());
                assertEquals(activity.getString(R.string.voice_too_long), ((TextView) activity.findViewById(R.id.status)).getText().toString());
            });
        }
    }

    @Test public void recognizedTitleIsNotSilentlyTruncatedAtTwoHundredCharacters() {
        try (ActivityScenario<EventEditorActivity> scenario = ActivityScenario.launch(EventEditorActivity.class)) {
            scenario.onActivity(activity -> {
                String title = repeated('а', 300);
                activity.onActivityResult(100, Activity.RESULT_OK, result("Завтра о 9 " + title));
                assertEquals(title, ((EditText) activity.findViewById(R.id.name)).getText().toString());
            });
        }
    }

    @Test public void revertingSourceAfterRecreationKeepsManualEditsAndDoesNotRequireReparse() {
        try (ActivityScenario<EventEditorActivity> scenario = ActivityScenario.launch(EventEditorActivity.class)) {
            scenario.onActivity(activity -> {
                activity.onActivityResult(100, Activity.RESULT_OK, result("Завтра Нарада"));
                ((EditText) activity.findViewById(R.id.name)).setText("Моя виправлена назва");
            });
            scenario.recreate();
            scenario.onActivity(activity -> {
                EditText raw = activity.findViewById(R.id.raw);
                raw.setText("Інший текст"); raw.setText("Завтра Нарада");
                activity.findViewById(R.id.save).performClick();
                assertNull(raw.getError());
                assertEquals(activity.getString(R.string.choose_time), ((TextView) activity.findViewById(R.id.status)).getText().toString());
                assertEquals("Моя виправлена назва", ((EditText) activity.findViewById(R.id.name)).getText().toString());
            });
        }
    }

    @Test public void relativeInstantSurvivesRecreationAndTimezoneChange() {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            try (ActivityScenario<EventEditorActivity> scenario = ActivityScenario.launch(EventEditorActivity.class)) {
                String[] expected = new String[1];
                scenario.onActivity(activity -> {
                    activity.onActivityResult(100, Activity.RESULT_OK, result("Через годину Нарада"));
                    String displayed = ((TextView) activity.findViewById(R.id.time)).getText().toString();
                    LocalTime utc = LocalTime.parse(displayed.substring(displayed.length() - 5));
                    expected[0] = activity.getString(R.string.time_value, utc.plusHours(2).toString());
                });
                TimeZone.setDefault(TimeZone.getTimeZone("GMT+02:00"));
                scenario.recreate();
                scenario.onActivity(activity -> assertEquals(expected[0], ((TextView) activity.findViewById(R.id.time)).getText().toString()));
            }
        } finally { TimeZone.setDefault(original); }
    }

    @Test public void parserValidationAlsoWorksOnAndroidRuntime() {
        LocalDateTime now = LocalDateTime.of(2026,10,23,10,0);
        assertNull(VoiceParser.parse("125 жовтня о 9 Нарада", now).date);
        assertNull(VoiceParser.parse("25 жовтня 20270 о 9 Нарада", now).date);
        assertEquals("Нарада", VoiceParser.parse("Нарада на 05.11 о 9", now).title);
        assertEquals("Нарада", VoiceParser.parse("Завтра о\u00a014:30 Нарада", now).title);
        assertEquals(LocalTime.of(21,0),VoiceParser.parse("Завтра о 9 вечора Нарада", now).time);
    }
}
