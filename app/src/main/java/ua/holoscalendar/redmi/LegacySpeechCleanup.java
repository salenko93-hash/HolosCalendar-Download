package ua.holoscalendar.redmi;

import android.content.Context;
import android.util.Log;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

/** Migration only: removes the old app-private directory without following symbolic links. */
final class LegacySpeechCleanup {
    private LegacySpeechCleanup() { }

    static synchronized boolean clean(Context context) {
        Path directory = context.getFilesDir().toPath().resolve("speech");
        try {
            if (Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
                Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
                    @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        Files.delete(file);
                        return FileVisitResult.CONTINUE;
                    }
                    @Override public FileVisitResult postVisitDirectory(Path dir, IOException error) throws IOException {
                        if (error != null) throw error;
                        Files.delete(dir);
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
            context.deleteSharedPreferences("speech");
            return true;
        } catch (IOException | SecurityException error) {
            Log.w("HolosCleanup", "Legacy private speech data could not be removed; will retry on next launch", error);
            return false;
        }
    }
}
