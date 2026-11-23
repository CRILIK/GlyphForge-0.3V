package com.example.glyphforge;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.media.*;
import android.media.audiofx.Visualizer;
import android.media.projection.*;
import android.os.*;
import android.util.Log;
import org.json.JSONObject;

/** Opt-in music reactive mode. Foreground only; no audio is stored or uploaded. */
public final class MusicVisualizerService extends Service {
    public static final String ACTION_START="com.example.glyphforge.MUSIC_START";
    public static final String ACTION_STOP="com.example.glyphforge.MUSIC_STOP";
    public static final String EXTRA_PROFILE="profile";
    public static final String EXTRA_SOURCE="source";
    public static final String EXTRA_PROJECTION_DATA="projectionData";
    public static final String EXTRA_PROJECTION_RESULT="projectionResult";
    public static final String STATUS="music_status";
    private static final String TAG="GlyphForge.Music";
    private static final String CHANNEL="glyphforge_music";
    private static final int NOTIFICATION_ID=71;
    private static volatile boolean running;
    private Handler handler;
    private Visualizer visualizer;
    private MediaProjection projection;
    private AudioRecord recorder;
    private Thread recordingThread;
    private JSONObject profile;
    private String source="mix";
    private float[] smoothed=new float[8];
    private float averageBass=.06f,lastBeat,smoothedRms;
    private long started, lastSent, lastAudible;
    private boolean blankShown;
    private MusicFrameRenderer.Levels levels=new MusicFrameRenderer.Levels();
    public static boolean isRunning(){return running;}
    private final Runnable sample=new Runnable(){@Override public void run(){
        if(!running)return;
        try{
            byte[] fft=new byte[visualizer.getCaptureSize()];
            int status=visualizer.getFft(fft);
            if(status==Visualizer.SUCCESS)dispatch(MusicFrameRenderer.analyzeFft(fft));
        }catch(Exception e){updateStatus("Ошибка Visualizer: "+e.getMessage());stopSelf();return;}
        handler.postDelayed(this,70);
    }};
    @Override public void onCreate(){super.onCreate();handler=new Handler(Looper.getMainLooper());}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent==null)return START_NOT_STICKY;
        if(ACTION_STOP.equals(intent.getAction())){stopSelf();return START_NOT_STICKY;}
        if(!ACTION_START.equals(intent.getAction()))return START_NOT_STICKY;
        stopCapture();
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){updateStatus("Разреши доступ к аудио");stopSelf();return START_NOT_STICKY;}
        try{profile=new JSONObject(intent.getStringExtra(EXTRA_PROFILE));source=intent.getStringExtra(EXTRA_SOURCE);
            if(!"capture".equals(source))source="mix";
            int type=android.os.Build.VERSION.SDK_INT>=34?("capture".equals(source)?android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION:android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE):0;
            createChannel();
            Intent stopIntent=new Intent(this,MusicVisualizerService.class);stopIntent.setAction(ACTION_STOP);
            PendingIntent stopAction=PendingIntent.getService(this,1,stopIntent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            Notification n=new Notification.Builder(this,CHANNEL)
                .addAction(android.R.drawable.ic_media_pause,"Стоп",stopAction)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("GlyphForge · Музыкальные глифы")
                .setContentText("Реакция на музыку активна · нажми, чтобы настроить")
                .setOngoing(true)
                .setContentIntent(PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE))
                .build();
            if(android.os.Build.VERSION.SDK_INT>=29)startForeground(NOTIFICATION_ID,n,type);else startForeground(NOTIFICATION_ID,n);
            running=true;started=SystemClock.elapsedRealtime();lastSent=0;lastAudible=started;blankShown=false;
            if("capture".equals(source)){
                Intent data;
                if(android.os.Build.VERSION.SDK_INT>=33)data=intent.getParcelableExtra(EXTRA_PROJECTION_DATA,Intent.class);
                else data=intent.getParcelableExtra(EXTRA_PROJECTION_DATA);
                int result=intent.getIntExtra(EXTRA_PROJECTION_RESULT,Activity.RESULT_CANCELED);
                if(data==null||result!=Activity.RESULT_OK)throw new SecurityException("Нет разрешения на захват воспроизведения");
                startCapture(data,result);
            }else{
                visualizer=new Visualizer(0);
                int[] range=Visualizer.getCaptureSizeRange();
                visualizer.setCaptureSize(Math.min(1024,range[1]));
                visualizer.setScalingMode(Visualizer.SCALING_MODE_NORMALIZED);
                visualizer.setEnabled(true);
                handler.post(sample);
            }
            updateStatus("Музыкальная визуализация запущена · "+("mix".equals(source)?"выходной микс":"захват воспроизведения"));
        }catch(Exception e){Log.e(TAG,"Music capture failed",e);updateStatus("Не удалось получить звук: "+e.getMessage());stopSelf();}
        return START_NOT_STICKY;
    }
    private void createChannel(){NotificationManager nm=getSystemService(NotificationManager.class);nm.createNotificationChannel(new NotificationChannel(CHANNEL,"Музыкальные глифы",NotificationManager.IMPORTANCE_LOW));}
    private void startCapture(Intent data,int code){
        MediaProjectionManager mgr=(MediaProjectionManager)getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        projection=mgr.getMediaProjection(code,data);
        projection.registerCallback(new MediaProjection.Callback(){@Override public void onStop(){updateStatus("Разрешение на захват звука отозвано");stopSelf();}},handler);
        AudioPlaybackCaptureConfiguration config=new AudioPlaybackCaptureConfiguration.Builder(projection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA).addMatchingUsage(AudioAttributes.USAGE_GAME).build();
        int rate=44100,buffer=Math.max(4096,AudioRecord.getMinBufferSize(rate,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT));
        AudioFormat fmt=new AudioFormat.Builder().setSampleRate(rate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_IN_MONO).build();
        recorder=new AudioRecord.Builder().setAudioPlaybackCaptureConfig(config).setAudioFormat(fmt).setBufferSizeInBytes(buffer*2).build();
        if(recorder.getState()!=AudioRecord.STATE_INITIALIZED)throw new IllegalStateException("AudioRecord недоступен");
        recorder.startRecording();
        recordingThread=new Thread(()->{
            short[] block=new short[2048];
            while(running&&recorder!=null){
                int count;try{count=recorder.read(block,0,block.length,AudioRecord.READ_BLOCKING);}catch(Exception e){break;}
                if(count>=512){MusicFrameRenderer.Levels l=MusicFrameRenderer.analyzePcm(block,count,rate);handler.post(()->dispatch(l));}
            }
        },"GlyphForge-AudioCapture");recordingThread.setDaemon(true);recordingThread.start();
    }
    private void dispatch(MusicFrameRenderer.Levels next){
        if(!running)return;
        long now=SystemClock.elapsedRealtime();if(now-lastSent<65)return;
        lastSent=now;
        float sensitivity=(float)profile.optDouble("sensitivity",1.6);
        float smoothing=MusicFrameRenderer.clamp((float)profile.optDouble("smoothing",.55),0,.94f);
        for(int i=0;i<8;i++){smoothed[i]=smoothed[i]*smoothing+next.bands[i]*(1-smoothing);levels.bands[i]=smoothed[i];}
        smoothedRms=smoothedRms*smoothing+next.rms*(1-smoothing);levels.rms=smoothedRms;
        float sum=0;for(float band:levels.bands)sum+=band;
        if(levels.rms>.018f||sum>.09f){lastAudible=now;blankShown=false;}
        else if(now-lastAudible>1600){
            if(!blankShown){GlyphPlayer.get(this).playLiveFrame(new int[169]);blankShown=true;}
            return;
        }
        float bass=(smoothed[0]+smoothed[1])*.5f, delta=bass-averageBass;
        averageBass=averageBass*.94f+bass*.06f;
        lastBeat=lastBeat*.72f;
        if(delta>.065f && bass>.11f)lastBeat=.95f;
        String mode=profile.optString("mode","bars");
        int[] art="art".equals(mode)?MusicFrameRenderer.artworkFromState(Store.read(this),profile.optString("designId",""),
                (int)((now-started)/780+lastBeat*3)):null;
        int[] frame=MusicFrameRenderer.render(mode,levels,art,(now-started)/1000f,(float)profile.optDouble("sensitivity",1.6),
                (float)profile.optDouble("brightness",.95),lastBeat);
        GlyphPlayer.get(this).playLiveFrame(frame);
    }
    private void updateStatus(String msg){getSharedPreferences(STATUS,MODE_PRIVATE).edit().putString("message",msg).putBoolean("running",running).apply();
        Intent status=new Intent("com.example.glyphforge.MUSIC_STATUS");status.setPackage(getPackageName());status.putExtra("message",msg);sendBroadcast(status);
        Log.i(TAG,msg);
    }
    private void stopCapture(){
        running=false;
        if(handler!=null)handler.removeCallbacks(sample);
        if(visualizer!=null){try{visualizer.setEnabled(false);}catch(Exception ignored){}try{visualizer.release();}catch(Exception ignored){}visualizer=null;}
        if(recorder!=null){try{recorder.stop();}catch(Exception ignored){}try{recorder.release();}catch(Exception ignored){}recorder=null;}
        if(projection!=null){MediaProjection p=projection;projection=null;try{p.stop();}catch(Exception ignored){}}
        GlyphPlayer.get(this).stopLive();
    }
    @Override public void onDestroy(){stopCapture();updateStatus("Музыкальный режим остановлен");super.onDestroy();}
    @Override public android.os.IBinder onBind(Intent intent){return null;}
}
