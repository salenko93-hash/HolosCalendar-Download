package ua.holoscalendar.redmi;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

final class ReminderAccess {
    static final int NOTIFICATION_PERMISSION = 101;
    static final String CHANNEL = "event_reminders_v1";
    private ReminderAccess() { }

    static void ensureChannel(Context context) {
        NotificationChannel channel = new NotificationChannel(CHANNEL,
                context.getString(R.string.reminder_channel), NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription(context.getString(R.string.reminder_channel_description));
        channel.enableVibration(true);
        context.getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    static boolean canNotify(Context context) {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) return false;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        NotificationChannel channel = manager.getNotificationChannel(CHANNEL);
        return manager.areNotificationsEnabled() && (channel == null || channel.getImportance() != NotificationManager.IMPORTANCE_NONE);
    }

    static boolean exactAllowed(Context context) {
        return Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager.class).canScheduleExactAlarms();
    }

    static void requestNotifications(Activity activity) {
        if (Build.VERSION.SDK_INT >= 33 && activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
                && !activity.getSharedPreferences("reminder_access", Context.MODE_PRIVATE).getBoolean("notificationsAsked", false)) {
            activity.getSharedPreferences("reminder_access", Context.MODE_PRIVATE).edit().putBoolean("notificationsAsked", true).apply();
            activity.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION);
        }
    }

    static void showSettings(Activity activity) {
        ensureChannel(activity);
        boolean notify = canNotify(activity), exact = exactAllowed(activity);
        String message = activity.getString(!notify ? R.string.reminder_blocked
                : exact ? R.string.reminder_permissions_ready : R.string.reminder_inexact);
        AlertDialog.Builder dialog = new AlertDialog.Builder(activity).setTitle(R.string.reminder_settings)
                .setMessage(message).setNegativeButton(R.string.cancel, null);
        dialog.setPositiveButton(notify ? R.string.reminder_android_settings : R.string.reminder_enable, (d, w) -> {
            if (Build.VERSION.SDK_INT >= 33 && activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED
                    && !activity.getSharedPreferences("reminder_access", Context.MODE_PRIVATE).getBoolean("notificationsAsked", false)) {
                requestNotifications(activity);
            } else open(activity, new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, activity.getPackageName()));
        });
        if (!exact && Build.VERSION.SDK_INT >= 31) dialog.setNeutralButton(R.string.reminder_exact_settings,
                (d, w) -> open(activity, new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:" + activity.getPackageName()))));
        dialog.show();
    }

    private static void open(Activity activity, Intent intent) {
        try { activity.startActivity(intent); }
        catch (ActivityNotFoundException error) {
            activity.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + activity.getPackageName())));
        }
    }

    static String label(Context context, Integer minutes) {
        if (minutes == null) return context.getString(R.string.reminder_off);
        if (minutes == 0) return context.getString(R.string.reminder_at_start);
        if (minutes == 1440) return context.getString(R.string.reminder_day_before);
        return context.getString(R.string.reminder_before, minutes);
    }
}
