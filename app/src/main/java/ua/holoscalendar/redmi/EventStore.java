package ua.holoscalendar.redmi;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Private local storage, with no dependency on calendar-provider permissions or accounts. */
final class EventStore extends SQLiteOpenHelper {
    EventStore(Context context) { this(context, "holoscalendar.db"); }
    EventStore(Context context, String databaseName) { super(context, databaseName, null, 3); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE events (_id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "title TEXT NOT NULL CHECK(length(trim(title)) > 0), start_millis INTEGER NOT NULL, "
                + "zone_id TEXT NOT NULL, duration_minutes INTEGER "
                + "CHECK(duration_minutes IS NULL OR duration_minutes BETWEEN 1 AND 1440), "
                + "reminder_minutes INTEGER CHECK(reminder_minutes IS NULL OR reminder_minutes BETWEEN 0 AND 1440), "
                + "reminder_delivered_for INTEGER, all_day INTEGER NOT NULL DEFAULT 0, end_day INTEGER, "
                + "recurrence TEXT NOT NULL DEFAULT '', reminder_time TEXT)");
        db.execSQL("CREATE INDEX events_start ON events(start_millis)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if(oldVersion<2) {
            db.execSQL("ALTER TABLE events ADD COLUMN reminder_minutes INTEGER CHECK(reminder_minutes IS NULL OR reminder_minutes BETWEEN 0 AND 1440)");
            db.execSQL("ALTER TABLE events ADD COLUMN reminder_delivered_for INTEGER");
        }
        if(oldVersion<3) {
            db.execSQL("ALTER TABLE events ADD COLUMN all_day INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE events ADD COLUMN end_day INTEGER");
            db.execSQL("ALTER TABLE events ADD COLUMN recurrence TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE events ADD COLUMN reminder_time TEXT");
        }
        if(newVersion>3) throw new IllegalStateException("A database migration is required");
    }

    long save(long id, CalendarEvent event) {
        return save(id, event, null);
    }

    long save(long id, CalendarEvent event, Integer reminderMinutes) {
        return save(id,event,reminderMinutes,null);
    }

    long save(long id,CalendarEvent event,Integer reminderMinutes,LocalTime reminderTime) {
        if(event.allDay&&reminderMinutes!=null&&reminderTime==null) throw new IllegalArgumentException("Choose an all-day reminder time");
        if (reminderMinutes != null && (reminderMinutes < 0 || reminderMinutes > 1440))
            throw new IllegalArgumentException("Invalid reminder");
        ContentValues values = new ContentValues();
        values.put("title", event.title);
        values.put("start_millis", event.startMillis());
        values.put("zone_id", event.zone.getId());
        if (event.durationMinutes == null) values.putNull("duration_minutes");
        else values.put("duration_minutes", event.durationMinutes);
        if (reminderMinutes == null) values.putNull("reminder_minutes");
        else values.put("reminder_minutes", reminderMinutes);
        values.put("all_day",event.allDay?1:0);
        if(event.endDateExclusive==null) values.putNull("end_day"); else values.put("end_day",event.endDateExclusive.toEpochDay());
        values.put("recurrence",event.recurrence);
        if(reminderTime==null||!event.allDay) values.putNull("reminder_time"); else values.put("reminder_time",reminderTime.toString());
        SQLiteDatabase db = getWritableDatabase();
        if (id == 0) return db.insertOrThrow("events", null, values);
        SavedEvent previous = find(id);
        if (previous == null) throw new IllegalStateException("The event was already deleted");
        if (previous.start.toEpochMilli() != event.startMillis() || !Objects.equals(previous.reminderMinutes, reminderMinutes)
                ||previous.allDay!=event.allDay||!previous.recurrence.equals(event.recurrence)||!Objects.equals(previous.reminderTime,event.allDay?reminderTime:null))
            values.putNull("reminder_delivered_for");
        if (db.update("events", values, "_id = ?", new String[]{Long.toString(id)}) != 1)
            throw new IllegalStateException("The event was already deleted");
        return id;
    }

    SavedEvent find(long id) {
        try (Cursor cursor = getReadableDatabase().query("events", null, "_id = ?",
                new String[]{Long.toString(id)}, null, null, null)) {
            return cursor.moveToFirst() ? read(cursor) : null;
        }
    }

    List<SavedEvent> between(Instant begin,Instant end) { return between(begin,end,ZoneId.systemDefault()); }
    List<SavedEvent> between(Instant begin,Instant end,ZoneId displayZone) {
        long first=begin.toEpochMilli();
        String where="recurrence != '' OR all_day = 1 OR (start_millis >= ? AND start_millis < ? AND "
                +"(start_millis >= ? OR (duration_minutes IS NOT NULL AND start_millis + duration_minutes * 60000 > CAST(? AS INTEGER))))";
        String[] args={Long.toString(first-86_400_000L),Long.toString(end.toEpochMilli()),Long.toString(first),Long.toString(first)};
        List<SavedEvent> events=new ArrayList<>();
        try(Cursor cursor=getReadableDatabase().query("events",null,where,args,null,null,"start_millis ASC, _id ASC")) {
            while(cursor.moveToNext()) events.addAll(OccurrenceEngine.between(read(cursor),begin,end,displayZone));
        }
        events.sort(java.util.Comparator.comparing((SavedEvent event)->event.start).thenComparingLong(event->event.id));
        return events;
    }

    boolean delete(long id) {
        return getWritableDatabase().delete("events", "_id = ?", new String[]{Long.toString(id)}) == 1;
    }

    SavedEvent nextReminder(Instant now) {
        SavedEvent best=null;
        try(Cursor cursor=getReadableDatabase().query("events",null,"reminder_minutes IS NOT NULL",null,null,null,null)) {
            while(cursor.moveToNext()) {
                SavedEvent next=OccurrenceEngine.nextReminder(read(cursor),now);
                if(next!=null&&(best==null||ReminderPolicy.plannedAt(next)<ReminderPolicy.plannedAt(best)
                        ||ReminderPolicy.plannedAt(next)==ReminderPolicy.plannedAt(best)&&next.id<best.id)) best=next;
            }
        }
        return best;
    }

    /** Atomically claim a still-current reminder so reboot or duplicate broadcasts cannot repeat it. */
    SavedEvent claimReminder(long id, long expectedTime, Instant now) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            SavedEvent master = find(id);
            SavedEvent event=master==null?null:OccurrenceEngine.expected(master,expectedTime);
            if (event == null || !ReminderPolicy.ready(event, expectedTime, now)) return null;
            ContentValues values = new ContentValues();
            values.put("reminder_delivered_for", expectedTime);
            db.update("events", values, "_id = ?", new String[]{Long.toString(id)});
            db.setTransactionSuccessful();
            return event;
        } finally { db.endTransaction(); }
    }

    void releaseReminder(long id, long expectedTime) {
        ContentValues values = new ContentValues(); values.putNull("reminder_delivered_for");
        getWritableDatabase().update("events", values, "_id = ? AND reminder_delivered_for = ?",
                new String[]{Long.toString(id), Long.toString(expectedTime)});
    }

    private SavedEvent read(Cursor cursor) {
        int duration = cursor.getColumnIndexOrThrow("duration_minutes");
        int reminder = cursor.getColumnIndexOrThrow("reminder_minutes");
        int delivered = cursor.getColumnIndexOrThrow("reminder_delivered_for");
        int end=cursor.getColumnIndexOrThrow("end_day"),clock=cursor.getColumnIndexOrThrow("reminder_time");
        return new SavedEvent(cursor.getLong(cursor.getColumnIndexOrThrow("_id")),
                cursor.getString(cursor.getColumnIndexOrThrow("title")),
                Instant.ofEpochMilli(cursor.getLong(cursor.getColumnIndexOrThrow("start_millis"))),
                ZoneId.of(cursor.getString(cursor.getColumnIndexOrThrow("zone_id"))),
                cursor.isNull(duration) ? null : cursor.getInt(duration),
                cursor.isNull(reminder) ? null : cursor.getInt(reminder),
                cursor.isNull(delivered) ? null : cursor.getLong(delivered),
                cursor.getInt(cursor.getColumnIndexOrThrow("all_day"))!=0,
                cursor.isNull(end)?null:LocalDate.ofEpochDay(cursor.getLong(end)),
                cursor.getString(cursor.getColumnIndexOrThrow("recurrence")),
                cursor.isNull(clock)?null:LocalTime.parse(cursor.getString(clock)));
    }
}
