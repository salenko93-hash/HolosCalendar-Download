package ua.holoscalendar.redmi;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.TaskStackBuilder;
import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** One system alarm points to the next pending reminder; all queue state lives in SQLite. */
final class ReminderScheduler {
    static final String ACTION = "ua.holoscalendar.redmi.REMIND";
    static final String EXPECTED_TIME = "expectedTime";
    static final int NOTIFICATION_ID = 1;
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
    private ReminderScheduler() { }

    static Future<?> refresh(Context context) {
        Context app = context.getApplicationContext();
        return WORKER.submit(() -> safelySchedule(app));
    }

    static void receive(Context context, Intent intent, Runnable finished) {
        Context app = context.getApplicationContext();
        WORKER.execute(() -> {
            try {
                if (ACTION.equals(intent.getAction())) deliver(app, intent.getLongExtra(EventEditorActivity.EVENT_ID, 0),
                        intent.getLongExtra(EXPECTED_TIME, Long.MIN_VALUE), Instant.now());
            } catch (SQLiteException | IllegalArgumentException | IllegalStateException | DateTimeException error) {
                Log.e("HolosReminders", "Could not deliver a reminder", error);
            } finally {
                try { safelySchedule(app); } finally { finished.run(); }
            }
        });
    }

    private static void safelySchedule(Context context) {
        try { scheduleNext(context, Instant.now()); }
        catch (SQLiteException | IllegalArgumentException | IllegalStateException | DateTimeException error) {
            Log.e("HolosReminders", "Could not schedule the next reminder", error);
        }
    }

    static void scheduleNext(Context context, Instant now) {
        ReminderAccess.ensureChannel(context);
        AlarmManager manager = context.getSystemService(AlarmManager.class);
        PendingIntent old = alarmIntent(context, 0, 0);
        manager.cancel(old);
        if (!ReminderAccess.canNotify(context)) { old.cancel(); return; }
        SavedEvent next;
        try (EventStore store = new EventStore(context)) { next = store.nextReminder(now); }
        if (next == null) { old.cancel(); return; }
        PendingIntent pending = alarmIntent(context, next.id, ReminderPolicy.plannedAt(next));
        long at = ReminderPolicy.nextAlarmAt(next, now);
        if (Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms()) {
            try { manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending); return; }
            catch (SecurityException revoked) { /* Permission may change between checking and scheduling. */ }
        }
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending);
    }

    private static PendingIntent alarmIntent(Context context, long id, long expected) {
        Intent intent = new Intent(context, ReminderReceiver.class).setAction(ACTION)
                .putExtra(EventEditorActivity.EVENT_ID, id).putExtra(EXPECTED_TIME, expected);
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    @SuppressLint("MissingPermission") // canNotify checks POST_NOTIFICATIONS and channel/app settings at runtime.
    static synchronized boolean deliver(Context context, long id, long expected, Instant now) {
        ReminderAccess.ensureChannel(context);
        if (!ReminderAccess.canNotify(context)) return false;
        try (EventStore store = new EventStore(context)) {
            SavedEvent event = store.claimReminder(id, expected, now);
            if (event == null) return false;
            try {
                context.getSystemService(NotificationManager.class).notify(tag(id), NOTIFICATION_ID, notification(context, event));
                return true;
            } catch (SecurityException revoked) {
                store.releaseReminder(id, expected);
                return false;
            }
        }
    }

    static Notification notification(Context context, SavedEvent event) {
        Intent editor = new Intent(context, EventEditorActivity.class)
                .setData(Uri.parse("holoscalendar://event/" + event.id)).putExtra(EventEditorActivity.EVENT_ID, event.id);
        PendingIntent open = TaskStackBuilder.create(context)
                .addNextIntent(new Intent(context, CalendarActivity.class)
                        .putExtra(CalendarActivity.SELECTED_DATE, event.start.atZone(event.allDay?event.zone:ZoneId.systemDefault()).toLocalDate().toString()))
                .addNextIntent(editor).getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String time = event.start.atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("EEEE, d MMMM · HH:mm", Locale.forLanguageTag("uk-UA")));
        String text = event.allDay?context.getString(R.string.all_day_range,event.start.atZone(event.zone).toLocalDate().toString(),event.endDateExclusive.minusDays(1).toString()):context.getString(R.string.reminder_notification_time, time);
        if (event.durationMinutes != null) text += "\n" + context.getString(R.string.duration_minutes, event.durationMinutes);
        Notification publicVersion = new Notification.Builder(context, ReminderAccess.CHANNEL)
                .setSmallIcon(R.drawable.ic_notification).setContentTitle(context.getString(R.string.app_name))
                .setContentText(context.getString(R.string.reminder_public)).build();
        return new Notification.Builder(context, ReminderAccess.CHANNEL)
                .setSmallIcon(R.drawable.ic_notification).setContentTitle(event.title).setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text)).setCategory(Notification.CATEGORY_EVENT)
                .setVisibility(Notification.VISIBILITY_PRIVATE).setPublicVersion(publicVersion)
                .setAutoCancel(true).setOnlyAlertOnce(false).setContentIntent(open).build();
    }

    static String tag(long id) { return "event:" + id; }
    // Share the delivery lock so a simultaneous deletion cannot leave an old notification behind.
    static synchronized void cancelNotification(Context context, long id) {
        context.getSystemService(NotificationManager.class).cancel(tag(id), NOTIFICATION_ID);
    }
}
