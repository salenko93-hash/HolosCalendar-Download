package ua.holoscalendar.redmi;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.View;
import android.widget.*;
import java.io.*;
import java.util.Arrays;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Foreground-only local speech. Backgrounding or rotating cancels audio and leaves the event form intact. */
public final class OfflineVoiceActivity extends Activity {
    static final String TEXT = "offlineSpeechText";
    private static final int IMPORT = 201, MICROPHONE = 202;
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private TextView status, timer, modelState;
    private Button record, stop, importModel, download, remove;
    private ProgressBar progress;
    private Job current;
    private boolean visible, pendingRecord;
    private Uri pendingImport;
    private static final class Job {
        final AtomicBoolean cancelled = new AtomicBoolean(), finish = new AtomicBoolean();
        final CancellationSignal signal = new CancellationSignal();
        volatile WhisperEngine engine;
        volatile InputStream input;
        long startedAt;
        void cancel() {
            cancelled.set(true); finish.set(true); signal.cancel();
            WhisperEngine active = engine;
            if (active != null) active.cancel();
            InputStream stream = input;
            if (stream != null) try { stream.close(); } catch (IOException ignored) { }
        }
    }
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        setContentView(R.layout.activity_offline_voice);
        ScreenInsets.apply(findViewById(R.id.offline_scroll));
        status=findViewById(R.id.offline_status); timer=findViewById(R.id.offline_timer);
        modelState=findViewById(R.id.offline_model_state);
        record=findViewById(R.id.offline_record); stop=findViewById(R.id.offline_stop);
        importModel=findViewById(R.id.offline_import); download=findViewById(R.id.offline_download);
        remove=findViewById(R.id.offline_remove); progress=findViewById(R.id.offline_progress);
        record.setOnClickListener(v->requestRecording());
        stop.setOnClickListener(v->{ if(current!=null) current.finish.set(true); stop.setEnabled(false); });
        findViewById(R.id.offline_cancel).setOnClickListener(v->{cancel();setResult(RESULT_CANCELED);finish();});
        importModel.setOnClickListener(v->chooseFile()); download.setOnClickListener(v->download());
        remove.setOnClickListener(v->new AlertDialog.Builder(this).setTitle(R.string.offline_remove_title)
                .setMessage(R.string.offline_remove_message).setNegativeButton(R.string.cancel,null)
                .setPositiveButton(R.string.offline_remove,(d,w)->removeModel()).show());
        findViewById(R.id.offline_microphone_settings).setOnClickListener(v->{
            try {startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())));}
            catch(ActivityNotFoundException ignored) {status.setText(R.string.offline_permission);}
        });
        idle();
        if(saved!=null) status.setText(R.string.offline_interrupted);
    }
    @Override protected void onStart() {super.onStart();visible=true;}
    @Override protected void onResume() {
        super.onResume();if(current==null) idle();
        if(pendingImport!=null&&current==null){Uri uri=pendingImport;pendingImport=null;importFile(uri);}
        else if(pendingRecord&&hasMicrophone()){pendingRecord=false;startRecording();}
    }
    @Override protected void onStop() {
        visible=false;pendingRecord=false;
        boolean active=current!=null;cancel();
        if(active){idle();status.setText(R.string.offline_interrupted);}
        super.onStop();
    }
    @Override protected void onDestroy() {cancel();main.removeCallbacksAndMessages(null);super.onDestroy();}
    private void cancel() {Job old=current;current=null;if(old!=null) old.cancel();main.removeCallbacksAndMessages(null);}
    private void idle() {
        getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        boolean ready=SpeechSettings.installed(this);
        modelState.setText(ready?R.string.offline_model_ready:R.string.offline_model_missing);
        record.setEnabled(ready);importModel.setEnabled(true);download.setEnabled(true);remove.setEnabled(ready);
        stop.setVisibility(View.GONE);stop.setEnabled(true);timer.setVisibility(View.GONE);progress.setVisibility(View.GONE);
        if(status.getText().length()==0) status.setText(ready?R.string.offline_ready:R.string.offline_install_help);
    }
    private Job begin(int message) {
        if(current!=null) throw new IllegalStateException("Speech operation is already active");
        Job job=new Job();current=job;
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        record.setEnabled(false);importModel.setEnabled(false);download.setEnabled(false);remove.setEnabled(false);
        stop.setVisibility(View.GONE);timer.setVisibility(View.GONE);progress.setVisibility(View.VISIBLE);status.setText(message);
        return job;
    }
    private void deliver(Job job,Runnable action) {
        main.post(()->{if(visible&&current==job&&!job.cancelled.get()&&!isFinishing()&&!isDestroyed()) action.run();});
    }
    private void complete(Job job,int message) {deliver(job,()->{current=null;idle();status.setText(message);});}
    private boolean hasMicrophone() {return checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED;}
    private void requestRecording() {
        if(current!=null) return;
        if(!SpeechSettings.installed(this)){status.setText(R.string.offline_install_help);return;}
        if(!hasMicrophone()) {
            pendingRecord=true;
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},MICROPHONE);
        } else startRecording();
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants) {
        super.onRequestPermissionsResult(request,permissions,grants);
        if(request!=MICROPHONE) return;
        boolean wanted=pendingRecord;pendingRecord=false;
        if(grants.length>0&&grants[0]==PackageManager.PERMISSION_GRANTED){if(visible&&wanted)startRecording();}
        else status.setText(R.string.offline_permission);
    }
    private void startRecording() {
        if(!visible||current!=null) return;
        Job job=begin(R.string.offline_preparing);
        File model=SpeechSettings.model(this);
        WORKER.execute(()->{
            float[] samples=null;
            try {
                SpeechModel.verify(model,job.cancelled);
                if(job.cancelled.get()) return;
                try(WhisperEngine engine=new WhisperEngine(model)) {
                    job.engine=engine;
                    if(job.cancelled.get()){engine.cancel();return;}
                    samples=MicrophoneRecorder.record(job.finish,job.cancelled,()->{
                        job.startedAt=SystemClock.elapsedRealtime();
                        deliver(job,()->{progress.setVisibility(View.GONE);status.setText(R.string.offline_recording);
                            stop.setVisibility(View.VISIBLE);stop.setEnabled(true);timer.setVisibility(View.VISIBLE);tick(job);});
                    });
                    if(job.cancelled.get()) return;
                    deliver(job,()->{stop.setVisibility(View.GONE);timer.setVisibility(View.GONE);progress.setVisibility(View.VISIBLE);status.setText(R.string.offline_transcribing);});
                    String text=engine.transcribe(samples);
                    if(text.isEmpty()){complete(job,R.string.offline_no_speech);return;}
                    if(text.length()>1000){complete(job,R.string.voice_too_long);return;}
                    deliver(job,()->{current=null;setResult(RESULT_OK,new Intent().putExtra(TEXT,text));finish();});
                } finally {job.engine=null;}
            } catch(CancellationException ignored) {
                // onStop already reset the screen; a cancelled result is never delivered to the editor.
            } catch(SecurityException denied) {complete(job,R.string.offline_permission);
            } catch(IllegalStateException empty) {complete(job,R.string.offline_no_speech);
            } catch(InterruptedIOException tooSlow) {complete(job,R.string.offline_timeout);
            } catch(IOException error) {complete(job,R.string.offline_failed);
            } catch(InterruptedException interrupted) {Thread.currentThread().interrupt();complete(job,R.string.offline_failed);
            } catch(LinkageError unavailable) {complete(job,R.string.offline_native_unavailable);
            } catch(OutOfMemoryError unavailable) {complete(job,R.string.offline_memory);
            } finally {if(samples!=null)Arrays.fill(samples,0f);}
        });
    }
    private void tick(Job job) {
        if(current!=job||job.cancelled.get()||stop.getVisibility()!=View.VISIBLE) return;
        long seconds=(SystemClock.elapsedRealtime()-job.startedAt)/1000;
        timer.setText(getString(R.string.offline_time,Math.min(30,seconds)));
        if(seconds>=30) job.finish.set(true);
        else main.postDelayed(()->tick(job),1000);
    }
    private void chooseFile() {
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE);
        try {startActivityForResult(intent,IMPORT);}
        catch(ActivityNotFoundException error){status.setText(R.string.offline_no_file_picker);}
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request!=IMPORT||result!=RESULT_OK||data==null||data.getData()==null)return;
        // onActivityResult may precede onStart; import begins once the screen is visible.
        pendingImport=data.getData();
        if(visible&&!isFinishing()&&!isDestroyed()){Uri uri=pendingImport;pendingImport=null;importFile(uri);}
    }
    private void importFile(Uri uri) {
        if(current!=null) return;
        Job job=begin(R.string.offline_importing);File destination=SpeechSettings.model(this);
        WORKER.execute(()->{
            try(AssetFileDescriptor descriptor=getContentResolver().openAssetFileDescriptor(uri,"r",job.signal)) {
                if(descriptor==null||descriptor.getLength()>SpeechModel.MAX_BYTES)throw new IOException("Model is too large");
                try(InputStream input=descriptor.createInputStream()) {
                    job.input=input;SpeechModel.install(input,destination,job.cancelled);
                } finally {job.input=null;}
                complete(job,R.string.offline_installed);
            } catch(OperationCanceledException|CancellationException ignored) {
            } catch(IOException|SecurityException|IllegalArgumentException error){complete(job,R.string.offline_invalid_model);}
        });
    }
    private void download() {
        new AlertDialog.Builder(this).setTitle(R.string.offline_download_title)
            .setItems(new String[]{getString(R.string.offline_tiny),getString(R.string.offline_base)},(d,index)->{
                try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(index==0?SpeechModel.TINY_URL:SpeechModel.BASE_URL)));}
                catch(ActivityNotFoundException error){status.setText(R.string.offline_no_browser);}
            }).setNegativeButton(R.string.cancel,null).show();
    }
    private void removeModel() {
        if(current!=null)return;Job job=begin(R.string.offline_removing);File model=SpeechSettings.model(this);
        WORKER.execute(()->{boolean removed=!model.exists()||model.delete();complete(job,removed?R.string.offline_removed:R.string.offline_failed);});
    }
}
