package ua.holoscalendar.redmi;

import android.content.Context;
import java.io.File;

final class SpeechSettings {
    private SpeechSettings() { }
    static boolean offline(Context context) { return context.getSharedPreferences("speech", Context.MODE_PRIVATE).getBoolean("offline", true); }
    static void offline(Context context, boolean enabled) { context.getSharedPreferences("speech", Context.MODE_PRIVATE).edit().putBoolean("offline", enabled).apply(); }
    static File model(Context context) { return new File(context.getFilesDir(), "speech/whisper.bin"); }
    static boolean installed(Context context) { File model=model(context); return model.isFile() && model.length()>0 && model.length()<=SpeechModel.MAX_BYTES; }
}
