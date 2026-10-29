package ua.holoscalendar.redmi;

import android.app.AlertDialog;
import android.content.Context;
import android.widget.EditText;
import android.widget.TextView;
import android.view.View;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.time.Instant;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ReminderEditorFlowTest {
    private void custom(EventEditorActivity activity,String text) {
        AlertDialog dialog=activity.pickCustomReminder();
        ((EditText)dialog.findViewById(R.id.reminder_custom_minutes)).setText(text);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        assertFalse(dialog.isShowing());
    }
    @Test public void validatesCustomOffsetAndKeepsItAfterRecreation() {
        try (ActivityScenario<EventEditorActivity> scenario=ActivityScenario.launch(EventEditorActivity.class)) {
            scenario.onActivity(activity->{
                AlertDialog dialog=activity.pickCustomReminder();
                EditText field=dialog.findViewById(R.id.reminder_custom_minutes);
                for(String invalid:new String[]{"", "1441", "99999"}) {
                    field.setText(invalid); dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                    assertTrue(dialog.isShowing()); assertNotNull(field.getError());
                }
                field.setText("17"); dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                assertFalse(dialog.isShowing());
                assertEquals(activity.getString(R.string.reminder_preview_missing),
                        ((TextView)activity.findViewById(R.id.reminder_preview)).getText().toString());
            });
            scenario.recreate();
            scenario.onActivity(activity->{
                assertTrue(((TextView)activity.findViewById(R.id.reminder)).getText().toString().contains("17"));
                AlertDialog list=activity.pickReminder();
                int index=list.getListView().getCheckedItemPosition();
                assertTrue(index>=0);
                assertTrue(list.getListView().getAdapter().getItem(index).toString().contains("17"));
                list.dismiss();
                AlertDialog cancel=activity.pickCustomReminder();
                ((EditText)cancel.findViewById(R.id.reminder_custom_minutes)).setText("70");
                cancel.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
                assertTrue(((TextView)activity.findViewById(R.id.reminder)).getText().toString().contains("17"));
            });
        }
    }
    @Test public void zeroAndMaximumOffsetsAreAcceptedAndCanBeDisabled() {
        try (ActivityScenario<EventEditorActivity> scenario=ActivityScenario.launch(EventEditorActivity.class)) {
            scenario.onActivity(activity->{
                custom(activity,"0");
                assertTrue(((TextView)activity.findViewById(R.id.reminder)).getText().toString()
                        .contains(activity.getString(R.string.reminder_at_start)));
                custom(activity,"1440");
                assertTrue(((TextView)activity.findViewById(R.id.reminder)).getText().toString()
                        .contains(activity.getString(R.string.reminder_day_before)));
                AlertDialog list=activity.pickReminder();
                list.getListView().performItemClick(null,0,0);
                assertEquals(View.GONE,activity.findViewById(R.id.reminder_preview).getVisibility());
            });
        }
    }
    @Test public void customReminderIsSavedWithoutInventingDuration() {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String title="Перевірка "+UUID.randomUUID();
        try (ActivityScenario<EventEditorActivity> scenario=ActivityScenario.launch(EventEditorActivity.class)) {
            scenario.onActivity(activity->{
                ((EditText)activity.findViewById(R.id.raw)).setText("Через дві години "+title);
                activity.findViewById(R.id.parse).performClick();
                custom(activity,"17");
                TextView preview=activity.findViewById(R.id.reminder_preview);
                assertTrue(preview.getText().toString().contains("Застосується після збереження"));
                activity.findViewById(R.id.save_local).performClick();
            });
            try (EventStore store=new EventStore(context)) {
                SavedEvent saved=store.between(Instant.now(),Instant.now().plusSeconds(86400)).stream()
                        .filter(event->event.title.equals(title)).findFirst().orElse(null);
                assertNotNull(saved);
                try {
                    assertEquals(Integer.valueOf(17),saved.reminderMinutes);
                    assertNull(saved.durationMinutes);
                } finally { store.delete(saved.id); ReminderScheduler.cancelNotification(context,saved.id); }
            }
        } finally { ReminderScheduler.refresh(context); }
    }
}
