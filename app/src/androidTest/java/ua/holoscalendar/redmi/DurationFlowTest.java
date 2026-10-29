package ua.holoscalendar.redmi;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.provider.CalendarContract;
import android.speech.RecognizerIntent;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class DurationFlowTest {
    private void speech(EventEditorActivity activity, String text) {
        Intent result = new Intent().putStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS,
                new ArrayList<>(Collections.singletonList(text)));
        activity.onActivityResult(100, Activity.RESULT_OK, result);
    }

    private void assertDuration(EventEditorActivity activity, Integer minutes) {
        String expected = minutes == null ? activity.getString(R.string.duration_unspecified)
                : activity.getString(R.string.duration_minutes, minutes);
        assertEquals(expected, ((TextView) activity.findViewById(R.id.duration)).getText().toString());
    }

    /** Capture the actual outgoing intent and prevent launching or writing to any real calendar. */
    private void assertCalendarHandoff(ActivityScenario<EventEditorActivity> scenario, Integer minutes) {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        AtomicReference<Intent> captured = new AtomicReference<>();
        Instrumentation.ActivityMonitor monitor = new Instrumentation.ActivityMonitor() {
            @Override public Instrumentation.ActivityResult onStartActivity(Intent intent) {
                if (!Intent.ACTION_INSERT.equals(intent.getAction())) return null;
                captured.set(new Intent(intent));
                return new Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null);
            }
        };
        instrumentation.addMonitor(monitor);
        try {
            scenario.onActivity(activity -> activity.findViewById(R.id.save).performClick());
            Intent intent = captured.get();
            assertNotNull("Calendar handoff must remain possible without a duration", intent);
            assertEquals(CalendarContract.Events.CONTENT_URI, intent.getData());
            assertTrue(intent.hasExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME));
            if (minutes == null) assertFalse(intent.hasExtra(CalendarContract.EXTRA_EVENT_END_TIME));
            else assertEquals(minutes * 60_000L,
                    intent.getLongExtra(CalendarContract.EXTRA_EVENT_END_TIME, -1)
                    - intent.getLongExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, -1));
        } finally { instrumentation.removeMonitor(monitor); }
    }

    @Test public void newAndRecreatedFormDoesNotHaveADefaultDuration() {
        try (ActivityScenario<EventEditorActivity> scenario = ActivityScenario.launch(EventEditorActivity.class)) {
            scenario.onActivity(activity -> assertDuration(activity, null));
            scenario.recreate();
            scenario.onActivity(activity -> assertDuration(activity, null));
        }
    }

    @Test public void originalUserPhraseDoesNotSendAnInventedEndTime() {
        try (ActivityScenario<EventEditorActivity> scenario = ActivityScenario.launch(EventEditorActivity.class)) {
            scenario.onActivity(activity -> {
                speech(activity, "Завтра о 15:00 зустріч з Оленою");
                assertDuration(activity, null);
            });
            scenario.recreate();
            scenario.onActivity(activity -> assertDuration(activity, null));
            assertCalendarHandoff(scenario, null);
        }
    }

    @Test public void spokenDurationSurvivesRecreationAndSetsTheActualEndTime() {
        try (ActivityScenario<EventEditorActivity> scenario = ActivityScenario.launch(EventEditorActivity.class)) {
            scenario.onActivity(activity -> {
                speech(activity, "Завтра о 15:00 зустріч з Оленою на 45 хвилин");
                assertDuration(activity, 45);
                assertEquals("зустріч з Оленою", ((TextView) activity.findViewById(R.id.name)).getText().toString());
            });
            scenario.recreate();
            scenario.onActivity(activity -> assertDuration(activity, 45));
            assertCalendarHandoff(scenario, 45);
        }
    }

    @Test public void newPhraseWithoutDurationClearsPreviouslyRecognizedValue() {
        try (ActivityScenario<EventEditorActivity> scenario = ActivityScenario.launch(EventEditorActivity.class)) {
            scenario.onActivity(activity -> {
                speech(activity, "Завтра о 15 Нарада на півтори години");
                assertDuration(activity, 90);
                speech(activity, "Завтра о 16 Дзвінок");
                assertDuration(activity, null);
            });
            assertCalendarHandoff(scenario, null);
        }
    }

    @Test public void cancelledSpeechPreservesRecognizedDuration() {
        try (ActivityScenario<EventEditorActivity> scenario = ActivityScenario.launch(EventEditorActivity.class)) {
            scenario.onActivity(activity -> {
                speech(activity, "Завтра о 15 Нарада на годину 30 хвилин");
                activity.onActivityResult(100, Activity.RESULT_CANCELED, null);
                assertDuration(activity, 90);
            });
        }
    }

    @Test public void relativeStartDoesNotBecomeMeetingDuration() {
        try (ActivityScenario<EventEditorActivity> scenario = ActivityScenario.launch(EventEditorActivity.class)) {
            scenario.onActivity(activity -> {
                speech(activity, "Через годину Зустріч");
                assertDuration(activity, null);
                speech(activity, "Через годину Зустріч на тридцять хвилин");
                assertDuration(activity, 30);
            });
            assertCalendarHandoff(scenario, 30);
        }
    }
}
