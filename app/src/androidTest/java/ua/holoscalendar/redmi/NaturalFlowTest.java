package ua.holoscalendar.redmi;

import android.app.*;
import android.content.*;
import android.speech.RecognizerIntent;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class NaturalFlowTest {
    private Context context;private final List<Long> created=new ArrayList<>();private Instrumentation.ActivityMonitor monitor;
    private final AtomicInteger opened=new AtomicInteger();
    @Before public void before() {
        context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        if(android.os.Build.VERSION.SDK_INT>=33) InstrumentationRegistry.getInstrumentation().getUiAutomation()
            .grantRuntimePermission(context.getPackageName(),android.Manifest.permission.POST_NOTIFICATIONS);
        monitor=new Instrumentation.ActivityMonitor(){@Override public Instrumentation.ActivityResult onStartActivity(Intent intent){
            if(intent.getComponent()!=null&&intent.getComponent().getClassName().equals(CalendarActivity.class.getName())) {opened.incrementAndGet();return new Instrumentation.ActivityResult(Activity.RESULT_CANCELED,null);}return null;
        }};
        InstrumentationRegistry.getInstrumentation().addMonitor(monitor);
    }
    @After public void after() throws Exception {
        InstrumentationRegistry.getInstrumentation().removeMonitor(monitor);
        try(EventStore store=new EventStore(context)){for(long id:created){store.delete(id);ReminderScheduler.cancelNotification(context,id);}}
        ReminderScheduler.refresh(context).get(20,TimeUnit.SECONDS);
    }
    private void speech(EventEditorActivity activity,String command) {
        activity.onActivityResult(100,Activity.RESULT_OK,new Intent().putStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS,new ArrayList<>(Collections.singletonList(command))));
    }
    private SavedEvent saved(String title) {
        try(EventStore store=new EventStore(context)) {
            for(SavedEvent event:store.between(Instant.now().minusSeconds(86400),Instant.now().plusSeconds(370L*86400)))
                if(event.title.equals(title)){created.add(event.id);return store.find(event.id);}
        }
        fail("Expected persisted event: "+title);return null;
    }
    @Test public void naturalCommandSavesTheCorrectTimeAndEnablesTheRequestedReminder() {
        String title="зателефонувати мамі "+UUID.randomUUID();
        try(ActivityScenario<EventEditorActivity> screen=ActivityScenario.launch(EventEditorActivity.class)) {
            screen.onActivity(activity->{speech(activity,"Нагадай завтра о дев’ятій ранку "+title);activity.findViewById(R.id.save_local).performClick();});
            SavedEvent event=saved(title);assertEquals(LocalTime.of(9,0),event.start.atZone(event.zone).toLocalTime());
            assertEquals(Integer.valueOf(0),event.reminderMinutes);assertNull(event.durationMinutes);
        }
    }
    @Test public void ambiguousSpokenHourSurvivesRecreationAndCannotBeSilentlySaved() {
        try(ActivityScenario<EventEditorActivity> screen=ActivityScenario.launch(EventEditorActivity.class)) {
            screen.onActivity(activity->speech(activity,"Завтра о сьомій Зустріч"));screen.recreate();
            screen.onActivity(activity->{
                assertEquals(activity.getString(R.string.choose_time),((TextView)activity.findViewById(R.id.time)).getText().toString());
                activity.findViewById(R.id.save_local).performClick();
            });
            assertEquals(0,opened.get());
        }
    }
    @Test public void vacationRangeSavesWithoutDemandingAClockAndSurvivesRecreation() {
        String title="відпустку "+UUID.randomUUID();
        try(ActivityScenario<EventEditorActivity> screen=ActivityScenario.launch(EventEditorActivity.class)) {
            screen.onActivity(activity->speech(activity,"Додай "+title+" з п’ятого по десяте листопада"));screen.recreate();
            screen.onActivity(activity->activity.findViewById(R.id.save_local).performClick());
            SavedEvent event=saved(title);assertTrue(event.allDay);assertEquals(5,event.start.atZone(event.zone).getDayOfMonth());
            assertEquals(11,event.endDateExclusive.getDayOfMonth());assertNull(event.durationMinutes);assertNull(event.reminderTime);
        }
    }
    @Test public void dailySpokenSeriesSavesOneMasterAndAppearsOnFollowingDays() {
        String title="Нарада "+UUID.randomUUID();
        try(ActivityScenario<EventEditorActivity> screen=ActivityScenario.launch(EventEditorActivity.class)) {
            screen.onActivity(activity->{speech(activity,"Заплануй "+title+" завтра о 15:00 щодня");activity.findViewById(R.id.save_local).performClick();});
            SavedEvent event=saved(title);assertEquals("D:1",event.recurrence);
            LocalDate date=event.start.atZone(event.zone).toLocalDate().plusDays(7);
            try(EventStore store=new EventStore(context)) {
                assertTrue(store.between(date.atStartOfDay(event.zone).toInstant(),date.plusDays(1).atStartOfDay(event.zone).toInstant(),event.zone)
                        .stream().anyMatch(value->value.id==event.id));
            }
        }
    }
    @Test public void seriesKeepsItsOriginalWallTimeAndZoneAfterRecreationWhileTravelling() {
        TimeZone original=TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            try(ActivityScenario<EventEditorActivity> screen=ActivityScenario.launch(EventEditorActivity.class)) {
                screen.onActivity(activity->speech(activity,"Завтра о 09:00 щодня Нарада"));
                TimeZone.setDefault(TimeZone.getTimeZone("GMT+02:00"));
                screen.recreate();
                screen.onActivity(activity->{
                    assertEquals(activity.getString(R.string.time_value,"09:00"),((TextView)activity.findViewById(R.id.time)).getText().toString());
                    assertEquals(activity.getString(R.string.timezone,"UTC"),((TextView)activity.findViewById(R.id.timezone)).getText().toString());
                });
            }
        } finally {TimeZone.setDefault(original);}
    }

}
