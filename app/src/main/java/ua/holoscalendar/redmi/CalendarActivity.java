package ua.holoscalendar.redmi;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.database.sqlite.SQLiteException;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Local month/day calendar. Database reads run away from the UI thread. */
public final class CalendarActivity extends Activity {
    static final String SELECTED_DATE = "selectedDate";
    private static final Locale UK = Locale.forLanguageTag("uk-UA");
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private LocalDate selectedDate = LocalDate.now();
    private YearMonth month = YearMonth.now();
    private List<SavedEvent> entries = Collections.emptyList();
    private int request;
    private ZoneId displayZone;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_calendar);
        ScreenInsets.apply(findViewById(R.id.calendar_scroll));
        if (Build.VERSION.SDK_INT >= 28) {
            findViewById(R.id.month_title).setAccessibilityHeading(true);
            findViewById(R.id.calendar_day_title).setAccessibilityHeading(true);
        }
        if (state != null) {
            selectedDate = LocalDate.ofEpochDay(state.getLong("day", selectedDate.toEpochDay()));
            month = YearMonth.of(state.getInt("year", month.getYear()), state.getInt("month", month.getMonthValue()));
        } else selectIntentDate(getIntent());
        findViewById(R.id.month_previous).setOnClickListener(v -> changeMonth(-1));
        findViewById(R.id.month_next).setOnClickListener(v -> changeMonth(1));
        findViewById(R.id.calendar_reminder_settings).setOnClickListener(v -> ReminderAccess.showSettings(this));
        findViewById(R.id.calendar_today).setOnClickListener(v -> {
            selectedDate = LocalDate.now(); month = YearMonth.from(selectedDate); load();
        });
        findViewById(R.id.calendar_add).setOnClickListener(v -> startActivity(new Intent(this, EventEditorActivity.class)
                .putExtra(SELECTED_DATE, selectedDate.toString())));
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent); setIntent(intent); selectIntentDate(intent); load();
    }

    private void selectIntentDate(Intent intent) {
        String value = intent.getStringExtra(SELECTED_DATE);
        if (value != null) {
            try { selectedDate = LocalDate.parse(value); month = YearMonth.from(selectedDate); }
            catch (DateTimeException ignored) { }
        }
    }

    @Override protected void onResume() {
        super.onResume(); ReminderAccess.ensureChannel(this); ReminderScheduler.refresh(this); load();
    }
    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request == ReminderAccess.NOTIFICATION_PERMISSION) ReminderScheduler.refresh(this);
    }

    private void changeMonth(int direction) {
        month = month.plusMonths(direction);
        selectedDate = month.atDay(Math.min(selectedDate.getDayOfMonth(), month.lengthOfMonth()));
        load();
    }

    private LocalDate gridStart() {
        LocalDate first = month.atDay(1);
        return first.minusDays(first.getDayOfWeek().getValue() - 1);
    }

    private void load() {
        displayZone = ZoneId.systemDefault();
        final int token = ++request;
        LocalDate begin = gridStart();
        Instant first = begin.atStartOfDay(displayZone).toInstant();
        Instant end = begin.plusDays(42).atStartOfDay(displayZone).toInstant();
        entries = Collections.emptyList();
        render();
        message(getString(R.string.calendar_loading), false);
        worker.execute(() -> {
            try (EventStore store = new EventStore(getApplicationContext())) {
                List<SavedEvent> loaded = store.between(first, end,displayZone);
                runOnUiThread(() -> {
                    if (isDestroyed() || token != request) return;
                    entries = loaded; render();
                });
            } catch (SQLiteException | IllegalArgumentException | IllegalStateException | DateTimeException error) {
                runOnUiThread(() -> {
                    if (!isDestroyed() && token == request) message(getString(R.string.local_read_error), true);
                });
            }
        });
    }

    private void render() {
        ((TextView) findViewById(R.id.month_title)).setText(month.format(DateTimeFormatter.ofPattern("LLLL yyyy", UK)));
        ((TextView) findViewById(R.id.calendar_day_title)).setText(selectedDate.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", UK)));
        renderGrid(); renderEvents();
    }

    private void renderGrid() {
        LinearLayout grid = findViewById(R.id.calendar_grid);
        grid.removeAllViews();
        LinearLayout header = row();
        for (DayOfWeek day : DayOfWeek.values()) {
            TextView label = new TextView(this);
            label.setText(day.getDisplayName(TextStyle.SHORT, UK));
            label.setGravity(Gravity.CENTER); label.setTextColor(getColor(R.color.muted));
            header.addView(label, new LinearLayout.LayoutParams(0, dp(32), 1));
        }
        grid.addView(header);
        LocalDate first = gridStart();
        LocalDate today = LocalDate.now();
        for (int week = 0; week < 6; week++) {
            LinearLayout line = row();
            for (int weekday = 0; weekday < 7; weekday++) {
                LocalDate date = first.plusDays(week * 7L + weekday);
                long count = entries.stream().filter(event -> event.occursOn(date, displayZone)).count();
                Button day = new Button(this);
                day.setAllCaps(false); day.setTextSize(15); day.setPadding(0, dp(4), 0, dp(4));
                day.setMinWidth(0); day.setMinimumWidth(0); day.setMinHeight(dp(48));
                day.setText(getString(R.string.calendar_day_cell, date.getDayOfMonth(), count > 0 ? "\n•" : ""));
                day.setContentDescription(getString(R.string.calendar_day_description,
                        date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", UK)), count));
                boolean selected = date.equals(selectedDate);
                day.setTextColor(selected ? android.graphics.Color.WHITE : getColor(
                        YearMonth.from(date).equals(month) ? R.color.ink : R.color.muted));
                if (date.equals(today)) day.setTypeface(null, Typeface.BOLD);
                GradientDrawable background = new GradientDrawable();
                background.setColor(getColor(selected ? R.color.accent : R.color.surface));
                background.setCornerRadius(dp(12));
                if (date.equals(today)) background.setStroke(dp(2), getColor(R.color.muted));
                day.setBackground(background);
                day.setOnClickListener(v -> {
                    selectedDate = date;
                    if (!YearMonth.from(date).equals(month)) { month = YearMonth.from(date); load(); }
                    else render();
                });
                LinearLayout.LayoutParams cell = new LinearLayout.LayoutParams(0, -2, 1);
                cell.setMargins(dp(1), dp(2), dp(1), dp(2));
                line.addView(day, cell);
            }
            grid.addView(line);
        }
    }

    private void renderEvents() {
        LinearLayout list = findViewById(R.id.calendar_events);
        list.removeAllViews();
        int count = 0;
        boolean hasReminder = false;
        for (SavedEvent event : entries) {
            if (!event.occursOn(selectedDate, displayZone)) continue;
            count++;
            hasReminder |= event.reminderMinutes != null;
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL); card.setBackgroundResource(R.drawable.card);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.topMargin = dp(12); list.addView(card, params);
            ZonedDateTime local = event.start.atZone(displayZone);
            String start = local.format(DateTimeFormatter.ofPattern(
                    local.toLocalDate().equals(selectedDate) ? "HH:mm" : "d MMM HH:mm", UK));
            String timing = event.durationMinutes == null ? start
                    : getString(R.string.calendar_time_range, start, event.start.plusSeconds(event.durationMinutes * 60L).atZone(displayZone)
                    .format(DateTimeFormatter.ofPattern("d MMM HH:mm", UK)));
            text(card,event.allDay?getString(R.string.all_day_range,event.start.atZone(event.zone).toLocalDate().toString(),event.endDateExclusive.minusDays(1).toString()):timing,16,R.color.muted);
            text(card, event.title, 20, R.color.ink);
            if(!event.allDay) text(card, event.durationMinutes == null ? getString(R.string.duration_unspecified)
                    : getString(R.string.duration_minutes, event.durationMinutes), 14, R.color.muted);
            if(!event.recurrence.isEmpty()) text(card,RecurrenceRule.label(event.recurrence),14,R.color.muted);
            if (event.reminderMinutes != null)
                text(card, getString(R.string.reminder_label, ReminderAccess.label(this, event.reminderMinutes)), 14, R.color.muted);
            LinearLayout actions = row(); card.addView(actions);
            Button edit = action(getString(event.recurrence.isEmpty()?R.string.event_edit:R.string.series_edit));
            edit.setContentDescription(getString(R.string.event_edit_description, event.title));
            edit.setOnClickListener(v -> startActivity(new Intent(this, EventEditorActivity.class).putExtra(EventEditorActivity.EVENT_ID, event.id)));
            actions.addView(edit, new LinearLayout.LayoutParams(0, -2, 1));
            Button delete = action(getString(R.string.event_delete));
            delete.setTextColor(getColor(R.color.error));
            delete.setContentDescription(getString(R.string.event_delete_description, event.title));
            delete.setOnClickListener(v -> confirmDelete(event));
            actions.addView(delete, new LinearLayout.LayoutParams(0, -2, 1));
        }
        if (hasReminder && !ReminderAccess.canNotify(this)) message(getString(R.string.reminder_blocked), true);
        else if (hasReminder && !ReminderAccess.exactAllowed(this)) message(getString(R.string.reminder_inexact), false);
        else message(getString(count == 0 ? R.string.calendar_empty : R.string.calendar_day_ready), false);
    }

    private void confirmDelete(SavedEvent event) {
        new AlertDialog.Builder(this).setTitle(R.string.event_delete_title)
                .setMessage(getString(event.recurrence.isEmpty()?R.string.event_delete_message:R.string.series_delete_message, event.title))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.event_delete, (dialog, which) -> worker.execute(() -> {
                    try (EventStore store = new EventStore(getApplicationContext())) {
                        store.delete(event.id);
                        ReminderScheduler.cancelNotification(this, event.id);
                        ReminderScheduler.refresh(this);
                        runOnUiThread(() -> { if (!isDestroyed()) load(); });
                    } catch (SQLiteException error) {
                        runOnUiThread(() -> { if (!isDestroyed()) message(getString(R.string.local_write_error), true); });
                    }
                })).show();
    }

    private void message(String value, boolean error) {
        TextView message = findViewById(R.id.calendar_message);
        message.setText(value); message.setTextColor(getColor(error ? R.color.error : R.color.muted));
    }
    private LinearLayout row() {
        LinearLayout value = new LinearLayout(this); value.setOrientation(LinearLayout.HORIZONTAL); return value;
    }
    private Button action(String title) {
        Button button = new Button(this); button.setText(title); button.setAllCaps(false);
        button.setTextColor(getColor(R.color.ink)); button.setMinHeight(dp(48)); return button;
    }
    private void text(LinearLayout parent, String value, int size, int color) {
        TextView label = new TextView(this); label.setText(value); label.setTextSize(size);
        label.setTextColor(getColor(color)); label.setPadding(0, dp(4), 0, dp(4)); parent.addView(label);
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putLong("day", selectedDate.toEpochDay()); state.putInt("year", month.getYear());
        state.putInt("month", month.getMonthValue()); super.onSaveInstanceState(state);
    }
    @Override protected void onDestroy() { ++request; worker.shutdown(); super.onDestroy(); }
}
