package ua.holoscalendar.redmi;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.database.sqlite.SQLiteException;
import android.widget.Toast;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.speech.RecognizerIntent;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.text.InputType;
import android.text.InputFilter;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Locale;

/** Edits one local event; optional system-calendar handoff remains a separate action. */
public final class EventEditorActivity extends Activity {
    static final String EVENT_ID = "eventId";
    private static final int VOICE = 100;
    static final int LOCAL_VOICE = 101;
    private static final int MAX_SPEECH_LENGTH = 1000;
    private static final int[] DURATIONS = {15, 30, 60, 90, 120};
    private static final Locale UK = Locale.forLanguageTag("uk-UA");
    private EditText raw, name;
    private TextView status;
    private Button dateButton, timeButton, durationButton;
    private LocalDate selectedDate = LocalDate.now();
    private LocalTime selectedTime = LocalTime.NOON;
    private boolean datePresent, timePresent, sourceDirty;
    private Integer durationMinutes;
    private Integer reminderMinutes;
    private boolean statusError;
    private String lastParsedText = "";
    private Instant relativeInstant;
    private long editingId;
    private boolean allDay,confirmParsed,durationNeedsChoice,recurrenceNeedsChoice;
    private LocalDate endDateExclusive;
    private String recurrence="";
    private ZoneId eventZone=ZoneId.systemDefault();
    private final ArrayList<LocalTime> timeChoices=new ArrayList<>();

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        setContentView(R.layout.activity_main);
        raw = findViewById(R.id.raw);
        name = findViewById(R.id.name);
        status = findViewById(R.id.status);
        dateButton = findViewById(R.id.date);
        timeButton = findViewById(R.id.time);
        durationButton = findViewById(R.id.duration);
        ScreenInsets.apply(findViewById(R.id.scroll));
        findViewById(R.id.voice).setOnClickListener(v -> recognize());
        findViewById(R.id.voice_mode).setOnClickListener(v -> new AlertDialog.Builder(this).setTitle(R.string.voice_mode_title)
            .setSingleChoiceItems(new String[]{getString(R.string.voice_mode_local_choice),getString(R.string.voice_mode_system_choice)},SpeechSettings.offline(this)?0:1,(d,index)->{
                SpeechSettings.offline(this,index==0);d.dismiss();refresh();
            }).setNegativeButton(R.string.cancel,null).show());
        findViewById(R.id.parse).setOnClickListener(v -> parse());
        findViewById(R.id.example).setOnClickListener(v -> { raw.setText(R.string.raw_hint); parse(); });
        findViewById(R.id.event_type).setOnClickListener(v -> pickType());
        findViewById(R.id.end_date).setOnClickListener(v -> pickEndDate());
        findViewById(R.id.recurrence).setOnClickListener(v -> pickRecurrence());
        dateButton.setOnClickListener(v -> pickDate());
        timeButton.setOnClickListener(v -> pickTime());
        durationButton.setOnClickListener(v -> pickDuration());
        findViewById(R.id.reminder).setOnClickListener(v -> pickReminder());
        findViewById(R.id.reminder_settings).setOnClickListener(v -> ReminderAccess.showSettings(this));
        findViewById(R.id.save).setOnClickListener(v -> openCalendar());
        findViewById(R.id.save_local).setOnClickListener(v -> prepareEvent(true));
        findViewById(R.id.my_calendar).setOnClickListener(v -> showOwnCalendar(datePresent ? selectedDate : LocalDate.now()));
        findViewById(R.id.help).setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle(getString(R.string.help_version, getString(R.string.app_version))).setMessage(R.string.help_body).setPositiveButton(android.R.string.ok, null).show());
        findViewById(R.id.clear).setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle(R.string.clear_title).setMessage(R.string.clear_message)
                .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.clear, (d, w) -> clear()).show());
        if (saved != null) restore(saved);
        else initializeEvent();
        raw.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                String current = s.toString().trim();
                sourceDirty = !current.isEmpty() && !current.equals(lastParsedText);
                setStatus(getString(sourceDirty ? R.string.dirty_source : current.isEmpty()
                        ? R.string.initial_status : R.string.review_ready), sourceDirty);
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        refresh();
    }

    private void initializeEvent() {
        long id = getIntent().getLongExtra(EVENT_ID, 0);
        if (id > 0) {
            try (EventStore store = new EventStore(this)) {
                SavedEvent event = store.find(id);
                if (event == null) {
                    setStatus(getString(R.string.event_missing), true);
                    findViewById(R.id.save_local).setEnabled(false); return;
                }
                editingId = id; name.setText(event.title);
                allDay=event.allDay;endDateExclusive=event.endDateExclusive;recurrence=event.recurrence;
                eventZone=allDay||!recurrence.isEmpty()?event.zone:ZoneId.systemDefault();
                relativeInstant=allDay?null:event.start;
                ZonedDateTime start = event.start.atZone(eventZone);
                selectedDate = start.toLocalDate(); selectedTime = start.toLocalTime();
                datePresent = true; timePresent = true; durationMinutes = event.durationMinutes;
                reminderMinutes = event.reminderMinutes;
                if(allDay) { selectedTime=event.reminderTime==null?LocalTime.NOON:event.reminderTime;timePresent=event.reminderTime!=null; }
                setStatus(getString(R.string.editing_event), false);
            } catch (SQLiteException | IllegalArgumentException | IllegalStateException | DateTimeException error) {
                setStatus(getString(R.string.local_read_error), true);
                findViewById(R.id.save_local).setEnabled(false);
            }
        } else {
            String initial = getIntent().getStringExtra(CalendarActivity.SELECTED_DATE);
            if (initial != null) {
                try { selectedDate = LocalDate.parse(initial); datePresent = true; }
                catch (DateTimeException ignored) { }
            }
        }
    }

    private void refresh() {
        ((Button)findViewById(R.id.voice_mode)).setText(SpeechSettings.offline(this)?R.string.voice_mode_local:R.string.voice_mode_system);
        // A one-off keeps its instant and follows the phone display zone; a series keeps its wall-time zone.
        if(!allDay&&recurrence.isEmpty()) eventZone=ZoneId.systemDefault();
        if (relativeInstant != null) {
            ZonedDateTime local = relativeInstant.atZone(eventZone);
            selectedDate = local.toLocalDate(); selectedTime = local.toLocalTime();
        }
        dateButton.setText(datePresent ? getString(R.string.date_value,
                selectedDate.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", UK))) : getString(R.string.choose_date));
        ((Button)findViewById(R.id.event_type)).setText(allDay?R.string.type_all_day:R.string.type_timed);
        findViewById(R.id.end_date).setVisibility(allDay?View.VISIBLE:View.GONE);
        ((Button)findViewById(R.id.end_date)).setText(getString(R.string.end_date_value,endDateExclusive==null?"—":endDateExclusive.minusDays(1).toString()));
        findViewById(R.id.duration).setVisibility(allDay?View.GONE:View.VISIBLE);
        timeButton.setVisibility(allDay&&reminderMinutes==null?View.GONE:View.VISIBLE);
        ((Button)findViewById(R.id.recurrence)).setText(getString(R.string.recurrence_value,RecurrenceRule.label(recurrence)));
        findViewById(R.id.series_hint).setVisibility(recurrence.isEmpty()?View.GONE:View.VISIBLE);
        timeButton.setText(timePresent ? getString(allDay?R.string.reminder_time_value:R.string.time_value,
                selectedTime.format(DateTimeFormatter.ofPattern("HH:mm", UK))) : getString(allDay?R.string.choose_reminder_time:R.string.choose_time));
        durationButton.setText(durationMinutes == null ? getString(R.string.duration_unspecified)
                : getString(R.string.duration_minutes, durationMinutes));
        ((Button) findViewById(R.id.reminder)).setText(getString(R.string.reminder_label, ReminderAccess.label(this, reminderMinutes)));
        TextView reminderHint = findViewById(R.id.reminder_hint);
        reminderHint.setText(reminderMinutes == null ? R.string.reminder_local_only
                : !ReminderAccess.canNotify(this) ? R.string.reminder_blocked
                : !ReminderAccess.exactAllowed(this) ? R.string.reminder_inexact : R.string.reminder_local_only);
        findViewById(R.id.reminder_settings).setVisibility(reminderMinutes != null ? View.VISIBLE : View.GONE);
        updateReminderPreview();
        ((Button) findViewById(R.id.save_local)).setText(editingId == 0 ? R.string.save_local : recurrence.isEmpty()?R.string.update_local:R.string.update_series);
        ((TextView) findViewById(R.id.timezone)).setText(getString(R.string.timezone, eventZone.getId()));
    }

    private void updateReminderPreview() {
        TextView preview = findViewById(R.id.reminder_preview);
        preview.setVisibility(reminderMinutes == null ? View.GONE : View.VISIBLE);
        if (reminderMinutes == null) return;
        if (!datePresent || !timePresent) {
            preview.setText(R.string.reminder_preview_missing); return;
        }
        try {
            CalendarEvent draft = new CalendarEvent("Preview", selectedDate, selectedTime, durationMinutes,
                    eventZone, relativeInstant, allDay, endDateExclusive, recurrence);
            if (eventZone.getRules().getValidOffsets(selectedDate.atTime(selectedTime)).isEmpty()) {
                preview.setText(R.string.dst_error); return;
            }
            SavedEvent master = new SavedEvent(0, draft.title, Instant.ofEpochMilli(draft.startMillis()), eventZone,
                    durationMinutes, reminderMinutes, null, allDay, draft.endDateExclusive, recurrence,
                    allDay ? selectedTime : null);
            Instant now = Instant.now();
            SavedEvent next = OccurrenceEngine.nextReminder(master, now);
            if (next == null) { preview.setText(R.string.reminder_preview_expired); return; }
            Instant at = Instant.ofEpochMilli(ReminderPolicy.plannedAt(next));
            if (at.isBefore(now)) { preview.setText(R.string.reminder_preview_late); return; }
            preview.setText(getString(R.string.reminder_preview,
                    at.atZone(eventZone).format(DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm", UK)), eventZone.getId()));
        } catch (IllegalArgumentException | IllegalStateException | DateTimeException invalid) {
            preview.setText(R.string.reminder_preview_missing);
        }
    }

    private void setStatus(String message, boolean error) {
        statusError = error;
        status.setText(message);
        status.setTextColor(getColor(error ? R.color.error : R.color.ink));
    }

    private void parse() {
        String text = raw.getText().toString().trim();
        if (text.isEmpty()) { raw.setError(getString(R.string.empty_input)); raw.requestFocus(); return; }
        VoiceParser.Result result = VoiceParser.parse(text, ZonedDateTime.now(eventZone));
        relativeInstant = result.instant;
        allDay=result.allDay;endDateExclusive=result.endDateExclusive;recurrence=result.recurrence;
        confirmParsed=result.confirmApproximation;durationNeedsChoice=result.durationNeedsChoice;recurrenceNeedsChoice=result.recurrenceNeedsChoice;
        timeChoices.clear();timeChoices.addAll(result.timeChoices);
        if(result.reminderMinutes!=null) { reminderMinutes=result.reminderMinutes;ReminderAccess.requestNotifications(this); }
        durationMinutes = result.durationMinutes;
        name.setText(result.title);
        name.setError(result.title.isEmpty() ? getString(R.string.missing_name) : null);
        datePresent = result.date != null;
        timePresent = result.time != null;
        if (datePresent) selectedDate = result.date;
        if (timePresent) selectedTime = result.time;
        lastParsedText = text;
        sourceDirty = false;
        raw.setError(null);
        refresh();
        setStatus(result.warning, !datePresent || (!allDay||reminderMinutes!=null)&&!timePresent || result.title.isEmpty());
        InputMethodManager keyboard = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        keyboard.hideSoftInputFromWindow(raw.getWindowToken(), 0);
        findViewById(R.id.status).requestRectangleOnScreen(new android.graphics.Rect(0, 0, status.getWidth(), status.getHeight()));
    }

    private void updateReadiness() {
        if (sourceDirty) return;
        setStatus(getString(!datePresent ? R.string.choose_date : (!allDay||reminderMinutes!=null)&&!timePresent ? (allDay?R.string.choose_reminder_time:R.string.choose_time)
                : name.getText().toString().trim().isEmpty() ? R.string.missing_name : R.string.review_ready),
                !datePresent || (!allDay||reminderMinutes!=null)&&!timePresent || name.getText().toString().trim().isEmpty());
    }

    private void pickDate() {
        LocalDate initial = datePresent ? selectedDate : LocalDate.now();
        new DatePickerDialog(this, (v, year, month, day) -> {
            relativeInstant = null;
            LocalDate chosen=LocalDate.of(year,month+1,day);
            if(allDay) endDateExclusive=chosen.plusDays(endDateExclusive==null?1:Math.max(1,java.time.temporal.ChronoUnit.DAYS.between(selectedDate,endDateExclusive)));
            selectedDate = chosen; datePresent = true; refresh(); updateReadiness();
        }, initial.getYear(), initial.getMonthValue() - 1, initial.getDayOfMonth()).show();
    }

    private void pickTime() {
        if(!timeChoices.isEmpty()) {
            String[] labels=new String[timeChoices.size()];for(int i=0;i<labels.length;i++) labels[i]=timeChoices.get(i).toString();
            new AlertDialog.Builder(this).setTitle(R.string.choose_time_of_day).setItems(labels,(d,index)->{
                selectedTime=timeChoices.get(index);timeChoices.clear();relativeInstant=null;timePresent=true;refresh();updateReadiness();
            }).setNeutralButton(R.string.other_time,(d,w)->{timeChoices.clear();pickTime();}).setNegativeButton(R.string.cancel,null).show();return;
        }
        new TimePickerDialog(this, (v, hour, minute) -> {
            relativeInstant = null;
            selectedTime = LocalTime.of(hour, minute); timePresent = true; refresh(); updateReadiness();
        }, selectedTime.getHour(), selectedTime.getMinute(), true).show();
    }

    private void pickDuration() {
        ArrayList<Integer> values = new ArrayList<>();
        ArrayList<String> labels = new ArrayList<>();
        values.add(null); labels.add(getString(R.string.duration_none));
        for (int value : DURATIONS) {
            values.add(value); labels.add(getString(R.string.duration_minutes, value));
        }
        if (durationMinutes != null && !values.contains(durationMinutes)) {
            values.add(durationMinutes); labels.add(getString(R.string.duration_minutes, durationMinutes));
        }
        int checked = values.indexOf(durationMinutes);
        new AlertDialog.Builder(this).setTitle(R.string.duration_title)
                .setSingleChoiceItems(labels.toArray(new String[0]), checked, (dialog, index) -> {
                    durationMinutes = values.get(index); durationNeedsChoice=false; refresh(); dialog.dismiss();
                }).setNegativeButton(R.string.cancel, null).show();
    }

    AlertDialog pickReminder() {
        ArrayList<Integer> values = new ArrayList<>(); values.add(null);
        ArrayList<String> labels = new ArrayList<>(); labels.add(ReminderAccess.label(this, null));
        for (int value : new int[]{0, 5, 10, 15, 30, 60, 1440}) {
            values.add(value); labels.add(ReminderAccess.label(this, value));
        }
        if (reminderMinutes != null && !values.contains(reminderMinutes)) {
            values.add(reminderMinutes); labels.add(ReminderAccess.label(this, reminderMinutes));
        }
        labels.add(getString(R.string.reminder_custom));
        return new AlertDialog.Builder(this).setTitle(R.string.reminder_title)
                .setSingleChoiceItems(labels.toArray(new String[0]), values.indexOf(reminderMinutes), (dialog, index) -> {
                    dialog.dismiss();
                    if (index == values.size()) pickCustomReminder();
                    else selectReminder(values.get(index));
                }).setNegativeButton(R.string.cancel, null).show();
    }

    private void selectReminder(Integer minutes) {
        reminderMinutes = minutes;
        if (minutes != null) ReminderAccess.requestNotifications(this);
        refresh(); updateReadiness();
    }

    AlertDialog pickCustomReminder() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (24 * getResources().getDisplayMetrics().density);
        form.setPadding(padding, 0, padding, 0);
        TextView label = new TextView(this);
        label.setText(R.string.reminder_custom_hint);
        label.setLabelFor(R.id.reminder_custom_minutes);
        EditText input = new EditText(this);
        input.setId(R.id.reminder_custom_minutes);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setSingleLine(true);
        input.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(5)});
        input.setText(String.format(Locale.ROOT, "%d", reminderMinutes == null ? 15 : reminderMinutes));
        input.selectAll();
        form.addView(label); form.addView(input);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(R.string.reminder_custom_label).setView(form)
                .setNegativeButton(R.string.cancel, null).setPositiveButton(android.R.string.ok, null).create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                int minutes = Integer.parseInt(input.getText().toString().trim());
                if (minutes < 0 || minutes > 1440) throw new NumberFormatException();
                selectReminder(minutes); dialog.dismiss();
            } catch (NumberFormatException invalid) { input.setError(getString(R.string.reminder_custom_error)); }
        });
        return dialog;
    }

    private void pickType() {
        new AlertDialog.Builder(this).setTitle(R.string.event_type).setSingleChoiceItems(new String[]{getString(R.string.type_timed),getString(R.string.type_all_day)},allDay?1:0,(dialog,index)->{
            boolean next=index==1;
            if(next!=allDay) { allDay=next;relativeInstant=null;timePresent=false;timeChoices.clear();durationMinutes=null;durationNeedsChoice=false; }
            if(allDay&&(endDateExclusive==null||!endDateExclusive.isAfter(selectedDate))) endDateExclusive=selectedDate.plusDays(1);
            dialog.dismiss();refresh();updateReadiness();
        }).setNegativeButton(R.string.cancel,null).show();
    }
    private void pickEndDate() {
        LocalDate initial=endDateExclusive==null?selectedDate:endDateExclusive.minusDays(1);
        new DatePickerDialog(this,(picker,y,m,d)->{
            LocalDate end=LocalDate.of(y,m+1,d).plusDays(1);
            if(!end.isAfter(selectedDate)||java.time.temporal.ChronoUnit.DAYS.between(selectedDate,end)>3660) {setStatus(getString(R.string.bad_end_date),true);return;}
            endDateExclusive=end;refresh();updateReadiness();
        },initial.getYear(),initial.getMonthValue()-1,initial.getDayOfMonth()).show();
    }
    private void setRecurrence(String rule) {recurrence=rule;recurrenceNeedsChoice=false;refresh();updateReadiness();}
    private void pickRecurrence() {
        LocalDate start=datePresent?selectedDate:LocalDate.now();
        String[] rules={"","D:1","D:2","W:"+start.getDayOfWeek().getValue(),"W:1,2,3,4,5","W:6,7","M:"+start.getDayOfMonth(),"Y:"+start.getMonthValue()+":"+start.getDayOfMonth()};
        String[] labels=new String[rules.length+3];for(int i=0;i<rules.length;i++) labels[i]=RecurrenceRule.label(rules[i]);
        labels[rules.length]=getString(R.string.choose_weekdays);labels[rules.length+1]=getString(R.string.choose_fortnight);labels[rules.length+2]=getString(R.string.choose_month_weekday);
        new AlertDialog.Builder(this).setTitle(R.string.repeat_title).setItems(labels,(dialog,index)->{
            if(index<rules.length) setRecurrence(rules[index]);
            else if(index==rules.length+2) pickMonthWeekday();else pickWeekdays(index==rules.length+1);
        }).setNegativeButton(R.string.cancel,null).show();
    }
    private void pickWeekdays(boolean fortnight) {
        String[] labels=getResources().getStringArray(R.array.weekday_names);boolean[] chosen=new boolean[7];
        new AlertDialog.Builder(this).setTitle(R.string.choose_weekdays).setMultiChoiceItems(labels,chosen,(d,index,value)->chosen[index]=value)
                .setPositiveButton(android.R.string.ok,(d,w)->{
                    ArrayList<String> days=new ArrayList<>();for(int i=0;i<7;i++) if(chosen[i]) days.add(Integer.toString(i+1));
                    if(days.isEmpty()) {setStatus(getString(R.string.pick_at_least_one_day),true);return;}
                    setRecurrence((fortnight?"W2:":"W:")+String.join(",",days));
                }).setNegativeButton(R.string.cancel,null).show();
    }
    private void pickMonthWeekday() {
        new AlertDialog.Builder(this).setTitle(R.string.choose_month_weekday).setItems(R.array.month_week_positions,(dialog,index)->
            new AlertDialog.Builder(this).setTitle(R.string.choose_weekdays).setItems(R.array.weekday_names,(d,day)->setRecurrence("MN:"+(index==4?-1:index+1)+":"+(day+1)))
                .setNegativeButton(R.string.cancel,null).show()).setNegativeButton(R.string.cancel,null).show();
    }

    private void clear() {
        raw.setText(""); name.setText(""); raw.setError(null); name.setError(null);
        selectedDate = LocalDate.now(); selectedTime = LocalTime.NOON;
        datePresent = false; timePresent = false; sourceDirty = false; durationMinutes = null; reminderMinutes = null;
        lastParsedText = ""; relativeInstant = null; editingId = 0;
        allDay=false;endDateExclusive=null;recurrence="";confirmParsed=false;durationNeedsChoice=false;recurrenceNeedsChoice=false;timeChoices.clear();eventZone=ZoneId.systemDefault();
        findViewById(R.id.save_local).setEnabled(true);
        setStatus(getString(R.string.initial_status), false); refresh();
    }

    private void recognize() {
        if(SpeechSettings.offline(this)) {
            startActivityForResult(new Intent(this,OfflineVoiceActivity.class),LOCAL_VOICE);
            return;
        }
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "uk-UA");
        // Let the provider use network recognition when no Ukrainian offline model is available.
        intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.voice_prompt));
        try { startActivityForResult(intent, VOICE); }
        catch (ActivityNotFoundException | SecurityException e) { setStatus(getString(R.string.voice_missing), true); }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if(request==LOCAL_VOICE) {
            String text=result==RESULT_OK&&data!=null?data.getStringExtra(OfflineVoiceActivity.TEXT):null;
            if(text!=null&&!text.trim().isEmpty()) {
                if(text.length()>MAX_SPEECH_LENGTH){setStatus(getString(R.string.voice_too_long),true);return;}
                raw.setText(text);parse();
            } else setStatus(getString(R.string.voice_cancelled),false);
            return;
        }
        if (request != VOICE) return;
        if (result == RESULT_OK && data != null) {
            ArrayList<String> values = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (values != null && !values.isEmpty() && values.get(0) != null && !values.get(0).trim().isEmpty()) {
                if (values.get(0).length() > MAX_SPEECH_LENGTH) {
                    setStatus(getString(R.string.voice_too_long), true); return;
                }
                raw.setText(values.get(0)); parse(); return;
            }
        }
        setStatus(getString(R.string.voice_cancelled), false);
    }

    private void openCalendar() { prepareEvent(false); }

    private void prepareEvent(boolean local) {
        refresh();
        if (sourceDirty) { raw.setError(getString(R.string.dirty_source)); raw.requestFocus(); return; }
        String title = name.getText().toString().trim();
        if (title.isEmpty()) { name.setError(getString(R.string.missing_name)); name.requestFocus(); return; }
        if(recurrenceNeedsChoice) {setStatus(getString(R.string.clarify_recurrence),true);findViewById(R.id.recurrence).requestFocus();return;}
        if(durationNeedsChoice&&!allDay) {setStatus(getString(R.string.clarify_duration),true);durationButton.requestFocus();return;}
        if (!datePresent || (!allDay||reminderMinutes!=null)&&!timePresent) {
            setStatus(getString(!datePresent ? R.string.choose_date : allDay?R.string.choose_reminder_time:R.string.choose_time), true);
            (!datePresent ? dateButton : timeButton).requestFocus(); return;
        }
        CalendarEvent event = new CalendarEvent(title, selectedDate, selectedTime, durationMinutes,eventZone,relativeInstant,allDay,endDateExclusive,recurrence);
        if(allDay&&reminderMinutes!=null&&eventZone.getRules().getValidOffsets(selectedDate.atTime(selectedTime)).isEmpty()) {setStatus(getString(R.string.dst_error),true);return;}
        if(confirmParsed) {
            new AlertDialog.Builder(this).setTitle(R.string.confirm_parsed_title).setMessage(R.string.confirm_parsed_message)
                .setNegativeButton(R.string.change,null).setPositiveButton(R.string.continue_action,(d,w)->{confirmParsed=false;prepareEvent(local);}).show();return;
        }
        if (event.isMissingLocalTime()) { setStatus(getString(R.string.dst_error), true); return; }
        if (allDay ? !event.endDateExclusive.isAfter(LocalDate.now(eventZone)) : event.startMillis() < System.currentTimeMillis()) {
            new AlertDialog.Builder(this).setTitle(R.string.past_title).setMessage(R.string.past_message)
                    .setNegativeButton(R.string.change, null)
                    .setPositiveButton(R.string.continue_action, (d, w) -> confirmOverlap(event, local)).show();
        } else confirmOverlap(event, local);
    }

    private void confirmOverlap(CalendarEvent event, boolean local) {
        if (event.requiresOffsetConfirmation() || allDay&&reminderMinutes!=null&&eventZone.getRules().getValidOffsets(selectedDate.atTime(selectedTime)).size()>1) {
            new AlertDialog.Builder(this).setTitle(R.string.dst_overlap_title).setMessage(R.string.dst_overlap_message)
                    .setNegativeButton(R.string.change, null)
                    .setPositiveButton(R.string.continue_action, (d, w) -> complete(event, local)).show();
        } else complete(event, local);
    }

    private void complete(CalendarEvent event, boolean local) {
        if (!local) { insert(event); return; }
        try (EventStore store = new EventStore(this)) {
            editingId = store.save(editingId,event,reminderMinutes,allDay&&reminderMinutes!=null?selectedTime:null);
            ReminderScheduler.cancelNotification(this, editingId);
            ReminderScheduler.refresh(this);
            refresh();
            setStatus(getString(R.string.local_saved), false);
            Toast.makeText(this, R.string.local_saved, Toast.LENGTH_SHORT).show();
            LocalDate visibleDate=recurrence.isEmpty()?selectedDate:RecurrenceRule.next(recurrence,selectedDate,selectedDate);
            showOwnCalendar(visibleDate==null?selectedDate:visibleDate);
        } catch (SQLiteException | IllegalStateException error) {
            setStatus(getString(R.string.local_write_error), true);
        }
    }

    private void showOwnCalendar(LocalDate date) {
        startActivity(new Intent(this, CalendarActivity.class)
                .putExtra(CalendarActivity.SELECTED_DATE, date.toString())
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
    }

    private void insert(CalendarEvent event) {
        Intent intent = new Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI);
        intent.putExtra(CalendarContract.Events.TITLE, event.title);
        intent.putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY,event.allDay);
        intent.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME,event.allDay?event.localStart.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli():event.startMillis());
        if(event.allDay) intent.putExtra(CalendarContract.EXTRA_EVENT_END_TIME,event.endDateExclusive.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli());
        else if (event.durationMinutes != null) intent.putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.endMillis());
        if(!recurrence.isEmpty()) intent.putExtra(CalendarContract.Events.RRULE,RecurrenceRule.rrule(recurrence));
        intent.putExtra(CalendarContract.Events.EVENT_TIMEZONE, (event.allDay?"UTC":event.zone.getId()));
        try { startActivity(intent); setStatus(getString(R.string.calendar_opened), false); }
        catch (ActivityNotFoundException | SecurityException e) { setStatus(getString(R.string.calendar_missing), true); }
    }

    private void restore(Bundle state) {
        editingId = state.getLong(EVENT_ID);
        allDay=state.getBoolean("allDay");recurrence=state.getString("recurrence","");eventZone=ZoneId.of(state.getString("eventZone",ZoneId.systemDefault().getId()));
        endDateExclusive=state.containsKey("endDate")?LocalDate.ofEpochDay(state.getLong("endDate")):null;
        confirmParsed=state.getBoolean("confirmParsed");durationNeedsChoice=state.getBoolean("durationChoice");recurrenceNeedsChoice=state.getBoolean("recurrenceChoice");
        timeChoices.clear();ArrayList<String> savedChoices=state.getStringArrayList("timeChoices");if(savedChoices!=null) for(String value:savedChoices) timeChoices.add(LocalTime.parse(value));
        reminderMinutes = state.containsKey("reminder") ? state.getInt("reminder") : null;
        findViewById(R.id.save_local).setEnabled(state.getBoolean("localSaveEnabled", true));
        lastParsedText = state.getString("lastParsedText", state.getBoolean("sourceDirty") ? "" : state.getString("raw", ""));
        String savedInstant = state.getString("relativeInstant");
        relativeInstant = savedInstant == null ? null : Instant.parse(savedInstant);
        raw.setText(state.getString("raw", "")); name.setText(state.getString("name", ""));
        selectedDate = LocalDate.ofEpochDay(state.getLong("date", selectedDate.toEpochDay()));
        selectedTime = LocalTime.ofSecondOfDay(state.getInt("time", selectedTime.toSecondOfDay()));
        datePresent = state.getBoolean("datePresent"); timePresent = state.getBoolean("timePresent");
        durationMinutes = state.containsKey("duration") ? state.getInt("duration") : null; sourceDirty = state.getBoolean("sourceDirty");
        setStatus(state.getString("status", getString(R.string.initial_status)), state.getBoolean("statusError"));
    }

    @Override protected void onRestoreInstanceState(Bundle state) {
        super.onRestoreInstanceState(state);
        // EditText restores its text after onCreate; restore our dirty flag afterwards.
        restore(state); refresh();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putLong(EVENT_ID, editingId);
        state.putBoolean("allDay",allDay);state.putString("recurrence",recurrence);state.putString("eventZone",eventZone.getId());
        if(endDateExclusive!=null) state.putLong("endDate",endDateExclusive.toEpochDay());
        state.putBoolean("confirmParsed",confirmParsed);state.putBoolean("durationChoice",durationNeedsChoice);state.putBoolean("recurrenceChoice",recurrenceNeedsChoice);
        ArrayList<String> savedChoices=new ArrayList<>();for(LocalTime choice:timeChoices) savedChoices.add(choice.toString());state.putStringArrayList("timeChoices",savedChoices);
        if (reminderMinutes != null) state.putInt("reminder", reminderMinutes);
        state.putBoolean("localSaveEnabled", findViewById(R.id.save_local).isEnabled());
        state.putString("raw", raw.getText().toString()); state.putString("name", name.getText().toString());
        state.putLong("date", selectedDate.toEpochDay()); state.putInt("time", selectedTime.toSecondOfDay());
        state.putBoolean("datePresent", datePresent); state.putBoolean("timePresent", timePresent);
        if (durationMinutes != null) state.putInt("duration", durationMinutes);
        state.putBoolean("sourceDirty", sourceDirty);
        state.putString("lastParsedText", lastParsedText);
        if (relativeInstant != null) state.putString("relativeInstant", relativeInstant.toString());
        state.putString("status", status.getText().toString()); state.putBoolean("statusError", statusError);
        super.onSaveInstanceState(state);
    }

    @Override protected void onResume() {
        super.onResume(); ReminderAccess.ensureChannel(this); ReminderScheduler.refresh(this);
        if (dateButton != null) refresh();
    }
    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request == ReminderAccess.NOTIFICATION_PERMISSION) { refresh(); ReminderScheduler.refresh(this); }
    }
}
