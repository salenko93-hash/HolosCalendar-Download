package ua.holoscalendar.redmi;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Restores the queue after boot/update/clock changes and delivers scheduled reminders. */
public final class ReminderReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        String action = intent == null ? null : intent.getAction();
        if (!ReminderScheduler.ACTION.equals(action) && !Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action) && !Intent.ACTION_TIME_CHANGED.equals(action)
                && !Intent.ACTION_TIMEZONE_CHANGED.equals(action)
                && !android.app.AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED.equals(action)) return;
        PendingResult pending = goAsync();
        ReminderScheduler.receive(context, intent, pending::finish);
    }
}
