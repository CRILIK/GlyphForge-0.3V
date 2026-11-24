package com.example.glyphforge;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.ContentValues;
import android.provider.MediaStore;
import android.os.Environment;
import android.os.Build;
import android.provider.Settings;
import android.net.Uri;
import android.media.audiofx.Visualizer;
import java.io.OutputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.HttpURLConnection;
import java.util.Arrays;
import org.json.JSONArray;
import org.json.JSONObject;

/** Diagnostics use synthetic data, aggregate counts and allowlisted HTTPS destinations. */
public final class DeviceDiagnostics {
    private DeviceDiagnostics(){}
    public static JSONObject item(String name,String status,String detail){
        JSONObject r=new JSONObject();
        try{r.put("name",name);r.put("status",status);r.put("detail",detail);}catch(Exception ignored){}
        return r;
    }
    public static JSONArray one(String name,String status,String detail){return new JSONArray().put(item(name,status,detail));}
    public static JSONObject collect(Context c){
        JSONObject result=new JSONObject();
        try{
            result.put("mode","android");result.put("androidSdk",Build.VERSION.SDK_INT);
            result.put("deviceModel",Build.MANUFACTURER+" "+Build.MODEL);
            android.content.pm.PackageInfo info=c.getPackageManager().getPackageInfo(c.getPackageName(),0);
            result.put("appVersion",info.versionName);result.put("versionCode",info.getLongVersionCode());
        }catch(Exception ex){try{result.put("error",ex.getClass().getSimpleName());}catch(Exception ignored){}}
        try{
            result.put("sdkInstalled",sdkInstalled());
            result.put("notificationAccess",notificationAllowed(c));
            result.put("notificationListenerConnected",GlyphNotificationListener.diagnosticConnected());
            result.put("audioPermission",allowed(c,Manifest.permission.RECORD_AUDIO));
            result.put("callPermission",allowed(c,Manifest.permission.READ_PHONE_STATE));
            result.put("postNotificationPermission",allowed(c,Manifest.permission.POST_NOTIFICATIONS));
            result.put("musicRunning",MusicVisualizerService.isRunning());
            android.content.pm.PackageInfo w=android.webkit.WebView.getCurrentWebViewPackage();
            if(w!=null)result.put("webViewVersion",w.versionName);
            String raw=Store.read(c);result.put("savedLibraryValid",raw.isEmpty()||new JSONObject(raw).optJSONArray("designs")!=null);
            result.put("downloadsWritable",checkDownloads(c));result.put("hardwareUnverified",true);
            JSONArray checks=new JSONArray();
            checks.put(item("Сервис уведомлений",!notificationAllowed(c)?"blocked":GlyphNotificationListener.diagnosticConnected()?"pass":"untested",GlyphNotificationListener.diagnosticConnected()?"Listener подключён":"Нет активного подключения"));
            try{checks.put(RuleDispatcher.diagnostic(c));}catch(Exception e){checks.put(item("Правила: синтетический тест","fail",e.getClass().getSimpleName()));}
            try{checks.put(audioSynthetic());}catch(Exception e){checks.put(item("Музыка: синтетический PCM/FFT","fail",e.getClass().getSimpleName()));}
            result.put("checks",checks);result.put("journal",DiagnosticJournal.read(c));
        }catch(Exception ex){try{result.put("error",ex.getClass().getSimpleName());}catch(Exception ignored){}}
        return result;
    }
    private static boolean sdkInstalled(){try{Class.forName("com.nothing.ketchum.GlyphMatrixManager");return true;}catch(Throwable ex){return false;}}
    static boolean allowed(Context c,String p){return c.checkSelfPermission(p)==PackageManager.PERMISSION_GRANTED;}
    static boolean notificationAllowed(Context c){String enabled=Settings.Secure.getString(c.getContentResolver(),"enabled_notification_listeners");return enabled!=null&&enabled.contains(c.getPackageName()+"/");}
    private static boolean checkDownloads(Context c){
        Uri file=null;
        try{
            byte[] expected=new byte[]{71,70,2,42};
            ContentValues v=new ContentValues();v.put(MediaStore.Downloads.DISPLAY_NAME,"glyphforge-test-"+System.nanoTime()+".tmp");v.put(MediaStore.Downloads.MIME_TYPE,"application/octet-stream");v.put(MediaStore.Downloads.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/GlyphForge");v.put(MediaStore.Downloads.IS_PENDING,1);
            file=c.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,v);if(file==null)return false;
            try(OutputStream out=c.getContentResolver().openOutputStream(file)){if(out==null)return false;out.write(expected);}
            try(InputStream in=c.getContentResolver().openInputStream(file)){if(in==null||!Arrays.equals(expected,in.readNBytes(5)))return false;}
            return true;
        }catch(Exception ex){return false;}finally{if(file!=null)try{c.getContentResolver().delete(file,null,null);}catch(Exception ignored){}}
    }
    private static JSONObject audioSynthetic(){
        short[] signal=new short[1024];for(int i=0;i<signal.length;i++)signal[i]=(short)(14000*Math.sin(2*Math.PI*440*i/44100));
        MusicFrameRenderer.Levels sound=MusicFrameRenderer.analyzePcm(signal,signal.length,44100),silence=MusicFrameRenderer.analyzePcm(new short[1024],1024,44100);
        byte[] fft=new byte[256];fft[12]=100;fft[13]=90;
        MusicFrameRenderer.Levels spectrum=MusicFrameRenderer.analyzeFft(fft);
        int[] lit=MusicFrameRenderer.render("bars",sound,null,0,1.6f,1,0),dark=MusicFrameRenderer.render("bars",silence,null,0,1.6f,1,0);
        boolean valid=lit.length==169&&sound.rms>silence.rms&&spectrum.rms>0&&!Arrays.equals(lit,dark);
        for(int i=0;i<lit.length;i++)if(lit[i]<0||lit[i]>255||(!MusicFrameRenderer.active(i%13,i/13)&&lit[i]!=0))valid=false;
        return item("Музыка: синтетический PCM/FFT",valid?"pass":"fail","Генерация 440 Гц, спектр, тишина, кадр 137 LED; без записи звука");
    }
    public static JSONArray audioProbe(Context c){
        String name="Аудиовыход Visualizer";
        if(!allowed(c,Manifest.permission.RECORD_AUDIO))return one(name,"blocked","Нужно разрешение аудио");
        if(MusicVisualizerService.isRunning())return one(name,"untested","Музыкальный режим уже активен; не прерываем");
        Visualizer v=null;try{
            v=new Visualizer(0);int[] range=Visualizer.getCaptureSizeRange();v.setCaptureSize(Math.min(1024,range[1]));
            int enable=v.setEnabled(true);byte[] fft=new byte[v.getCaptureSize()];int read=v.getFft(fft);
            return one(name,enable==Visualizer.SUCCESS&&read==Visualizer.SUCCESS?"pass":"fail","Коды enable="+enable+", FFT="+read+"; наличие музыки не оценивается");
        }catch(Throwable e){return one(name,"fail",e.getClass().getSimpleName());}
        finally{if(v!=null)try{v.setEnabled(false);v.release();}catch(Exception ignored){}}
    }
    public static JSONArray network(){
        JSONArray rows=new JSONArray();
        String[][] targets={{"HTTPS: Glyph Museum","https://app.glyphmuseum.com/"},{"HTTPS: Nothing Playground","https://playground.nothing.tech/"},{"HTTPS: GitHub Releases","https://api.github.com/repos/CRILIK/GlyphForge-0.3V/releases/latest"}};
        for(String[] target:targets){HttpURLConnection conn=null;try{
            conn=(HttpURLConnection)new URL(target[1]).openConnection();conn.setConnectTimeout(3500);conn.setReadTimeout(3500);
            conn.setRequestProperty("User-Agent","GlyphForge-Diagnostics");conn.setInstanceFollowRedirects(false);
            int code=conn.getResponseCode();
            boolean valid=code>=200&&code<400||target[0].contains("Releases")&&code==404;
            String detail="TLS/HTTP "+code+(code==404&&target[0].contains("Releases")?"; опубликованный релиз не найден":"; без cookies и авторизации");
            rows.put(item(target[0],valid?"pass":"fail",detail));
        }catch(Exception ex){rows.put(item(target[0],"fail",ex.getClass().getSimpleName()));}finally{if(conn!=null)conn.disconnect();}}
        rows.put(item("Скачивание чужих работ с авторизацией","untested","HTTPS-проверка не подтверждает загрузку конкретного GIF/JSON; см. журнал попыток"));
        return rows;
    }
}
