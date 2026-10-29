package ua.holoscalendar.redmi;

import android.app.Application;

/** Removes obsolete private speech data after updating, away from the UI thread. */
public final class HolosApplication extends Application {
    @Override public void onCreate() {
        super.onCreate();
        Thread cleanup = new Thread(() -> LegacySpeechCleanup.clean(this), "LegacySpeechCleanup");
        cleanup.setDaemon(true);
        cleanup.start();
    }
}
