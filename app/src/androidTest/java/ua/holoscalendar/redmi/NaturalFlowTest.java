package ua.holoscalendar.redmi;

import android.app.*;
import android.content.*;
import android.widget.TextView;
import android.widget.EditText;
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
    private void text(EventEditorActivity activity,String command) {
        ((EditText)activity.findViewById(R.id.raw)).setText(command);
        activity.findViewById(R.id.parse).performClick();
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
            screen.onActivity(activity->{text(activity,"Нагадай завтра о дев’ятій ранку "+title);activity.findViewById(R.id.save_local).performClick();});
            SavedEvent event=saved(title);assertEquals(LocalTime.of(9,0),event.start.atZone(event.zone).toLocalTime());
            assertEquals(Integer.valueOf(0),event.reminderMinutes);assertNull(event.durationMinutes);
        }
    }
    @Test public void ambiguousWrittenHourSurvivesRecreationAndCannotBeSilentlySaved() {
        try(ActivityScenario<EventEditorActivity> screen=ActivityScenario.launch(EventEditorActivity.class)) {
            screen.onActivity(activity->text(activity,"Завтра о сьомій Зустріч"));screen.recreate();
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
            screen.onActivity(activity->text(activity,"Додай "+title+" з п’ятого по десяте листопада"));screen.recreate();
            screen.onActivity(activity->activity.findViewById(R.id.save_local).performClick());
            SavedEvent event=saved(title);assertTrue(event.allDay);assertEquals(5,event.start.atZone(event.zone).getDayOfMonth());
            assertEquals(11,event.endDateExclusive.getDayOfMonth());assertNull(event.durationMinutes);assertNull(event.reminderTime);
        }
    }
    @Test public void dailyWrittenSeriesSavesOneMasterAndAppearsOnFollowingDays() {
        String title="Нарада "+UUID.randomUUID();
        try(ActivityScenario<EventEditorActivity> screen=ActivityScenario.launch(EventEditorActivity.class)) {
            screen.onActivity(activity->{text(activity,"Заплануй "+title+" завтра о 15:00 щодня");activity.findViewById(R.id.save_local).performClick();});
            SavedEvent event=saved(title);assertEquals("D:1",event.recurrence);
            LocalDate date=event.start.atZone(event.zone).toLocalDate().plusDays(7);
            try(EventStore store=new EventStore(context)) {
                assertTrue(store.between(date.atStartOfDay(event.zone).toInstant(),date.plusDays(1).atStartOfDay(event.zone).toInstant(),event.zone)
                        .stream().anyMatch(value->value.id==event.id));
            }
        }
    }
    @Test public void compoundRelativeStartIsSavedWithoutInventingDuration() {
        String title="Дзвінок "+UUID.randomUUID();
        Instant before=Instant.now();
        try(ActivityScenario<EventEditorActivity> screen=ActivityScenario.launch(EventEditorActivity.class)) {
            screen.onActivity(activity->{text(activity,"Через годину і двадцять хвилин "+title);activity.findViewById(R.id.save_local).performClick();});
            SavedEvent event=saved(title);
            long seconds=Duration.between(before,event.start).getSeconds();
            assertTrue(seconds>=80*60&&seconds<83*60);
            assertNull(event.durationMinutes);assertNull(event.reminderMinutes);
        }
    }
    @Test public void twoWrittenWeeklyDaysPersistAsOneSeries() {
        String title="Зустріч "+UUID.randomUUID();
        try(ActivityScenario<EventEditorActivity> screen=ActivityScenario.launch(EventEditorActivity.class)) {
            screen.onActivity(activity->{text(activity,"Заплануйте завтра щопонеділка та щосереди о 15:00 "+title);activity.findViewById(R.id.save_local).performClick();});
            SavedEvent event=saved(title);assertEquals("W:1,3",event.recurrence);
            LocalDate anchor=event.start.atZone(event.zone).toLocalDate();
            Set<Integer> days=new HashSet<>();
            try(EventStore store=new EventStore(context)) {
                for(SavedEvent occurrence:store.between(anchor.atStartOfDay(event.zone).toInstant(),anchor.plusDays(14).atStartOfDay(event.zone).toInstant(),event.zone))
                    if(occurrence.id==event.id) days.add(occurrence.start.atZone(event.zone).getDayOfWeek().getValue());
            }
            assertEquals(new HashSet<>(Arrays.asList(1,3)),days);assertNull(event.durationMinutes);
        }
    }
    @Test public void seriesKeepsItsOriginalWallTimeAndZoneAfterRecreationWhileTravelling() {
        TimeZone original=TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            try(ActivityScenario<EventEditorActivity> screen=ActivityScenario.launch(EventEditorActivity.class)) {
                screen.onActivity(activity->text(activity,"Завтра о 09:00 щодня Нарада"));
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
