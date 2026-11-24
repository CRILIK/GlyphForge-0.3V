package com.example.glyphforge;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebSettings;
import android.util.Base64;
import android.content.ContentValues;
import android.provider.MediaStore;
import android.os.Environment;
import android.widget.Toast;
import java.nio.charset.StandardCharsets;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;

/** Minimal trusted WebView shell. No remote pages are loaded into this JS bridge. */
public class MainActivity extends Activity {
    private static final int FILE_REQUEST = 2001;
    private static final int PERMISSION_PHONE = 2002;
    private static final int PERMISSION_AUDIO = 2003;
    private static final int PERMISSION_PROJECTION = 2004;
    private static final int DIAG_AUDIO = 2005, DIAG_POST = 2006;
    private String pendingMusicProfile;
    private String pendingMusicMode="mix";
    private WebView web;
    private ValueCallback<Uri[]> fileCallback;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        web = new WebView(this);
        // Keep all controls outside status and gesture areas on Android 15+.
        android.widget.FrameLayout safeArea = new android.widget.FrameLayout(this);
        safeArea.setBackgroundColor(android.graphics.Color.BLACK);
        safeArea.addView(web, new android.widget.FrameLayout.LayoutParams(-1,-1));
        safeArea.setOnApplyWindowInsetsListener((view,insets)->{
            android.graphics.Insets bars=insets.getInsets(android.view.WindowInsets.Type.systemBars()|android.view.WindowInsets.Type.displayCutout());
            view.setPadding(bars.left,bars.top,bars.right,bars.bottom);
            return insets;
        });
        setContentView(safeArea);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        web.addJavascriptInterface(new NativeApi(), "GlyphNative");
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                Uri u=r.getUrl();
                if ("file".equals(u.getScheme()) && "android_asset".equals(u.getHost())) return false;
                if ("https".equals(u.getScheme())) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch(Exception ignored) {}
                }
                return true;
            }
        });
        web.setWebChromeClient(new WebChromeClient(){
            @Override public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if(fileCallback!=null) fileCallback.onReceiveValue(null);
                fileCallback=callback;
                try { startActivityForResult(params.createIntent(), FILE_REQUEST); return true; }
                catch(Exception ex) {fileCallback=null; return false;}
            }
        });
        web.loadUrl("file:///android_asset/index.html");
    }
    @Override protected void onActivityResult(int req,int result,Intent data){
        super.onActivityResult(req,result,data);
        if(req==PERMISSION_PROJECTION){
            if(result==RESULT_OK&&data!=null)launchMusicService(pendingMusicProfile,"capture",result,data);
            else status("Захват аудио отменён пользователем");
            return;
        }
        if(req==FILE_REQUEST&&fileCallback!=null){fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result,data));fileCallback=null;}
    }
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grants){
        super.onRequestPermissionsResult(requestCode,permissions,grants);
        if(requestCode==PERMISSION_AUDIO){
            if(grants.length>0&&grants[0]==PackageManager.PERMISSION_GRANTED)beginMusic(pendingMusicProfile,pendingMusicMode);
            else status("Для анализа музыки необходимо разрешение на аудио");
        }
    }
    private void beginMusic(String profile,String source){
        if(profile==null)return;
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
            pendingMusicProfile=profile;pendingMusicMode=source;
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},PERMISSION_AUDIO);return;
        }
        if("capture".equals(source)){
            android.media.projection.MediaProjectionManager manager=(android.media.projection.MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
            if(manager==null){status("MediaProjection недоступен");return;}
            pendingMusicProfile=profile;
            startActivityForResult(manager.createScreenCaptureIntent(),PERMISSION_PROJECTION);
        }else launchMusicService(profile,"mix",RESULT_CANCELED,null);
    }
    private void launchMusicService(String profile,String source,int result,Intent data){
        try{
            Intent i=new Intent(this,MusicVisualizerService.class);
            i.setAction(MusicVisualizerService.ACTION_START);
            i.putExtra(MusicVisualizerService.EXTRA_PROFILE,profile);
            i.putExtra(MusicVisualizerService.EXTRA_SOURCE,source);
            if(data!=null){i.putExtra(MusicVisualizerService.EXTRA_PROJECTION_RESULT,result);i.putExtra(MusicVisualizerService.EXTRA_PROJECTION_DATA,data);}
            startForegroundService(i);
            status("Запуск музыкального режима…");
        }catch(Exception e){status("Невозможно запустить Music: "+e.getMessage());}
    }
    @Override public void onBackPressed(){
        if(web==null){super.onBackPressed();return;}
        web.evaluateJavascript("window.GlyphForgeNavigateBack?.()",value->{
            if(!"true".equals(value))MainActivity.super.onBackPressed();
        });
    }
    @Override protected void onDestroy(){
        if(!MusicVisualizerService.isRunning())GlyphPlayer.get(this).stop();
        if(web!=null){web.removeJavascriptInterface("GlyphNative");web.destroy();}
        super.onDestroy();
    }
    private void status(String msg){
        final String json=org.json.JSONObject.quote(msg);
        runOnUiThread(()->{if(web!=null)web.evaluateJavascript("window.onNativeStatus("+json+")",null);});
    }
    private void diagnosticResult(String token,String group,org.json.JSONArray result){
        runOnUiThread(()->{if(web!=null&&!isDestroyed())web.evaluateJavascript("window.GlyphDiagnosticResult("+org.json.JSONObject.quote(token)+","+org.json.JSONObject.quote(group)+","+org.json.JSONObject.quote(result.toString())+")",null);});
    }
    private void testNotification(String token){
        String name="Тестовое уведомление";
        android.app.NotificationManager nm=getSystemService(android.app.NotificationManager.class);
        if(!DeviceDiagnostics.notificationAllowed(this)||!DeviceDiagnostics.allowed(this,Manifest.permission.POST_NOTIFICATIONS)||nm==null||!nm.areNotificationsEnabled()){
            diagnosticResult(token,name,DeviceDiagnostics.one(name,"blocked","Нужны доступ к уведомлениям и разрешение их публикации"));return;}
        if(!GlyphNotificationListener.diagnosticConnected()){diagnosticResult(token,name,DeviceDiagnostics.one(name,"untested","Listener ещё не подключён"));return;}
        try{
            long before=GlyphNotificationListener.diagnosticSeen();
            String channel="gf_diag_silent";
            nm.createNotificationChannel(new android.app.NotificationChannel(channel,"Самодиагностика GlyphForge",android.app.NotificationManager.IMPORTANCE_LOW));
            nm.notify("gf_diagnostic",3092,new android.app.Notification.Builder(this,channel).setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("GlyphForge: самопроверка").setContentText("Тестовое уведомление").setOnlyAlertOnce(true).build());
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(()->{
                boolean delivered=GlyphNotificationListener.diagnosticSeen()>before;nm.cancel("gf_diagnostic",3092);
                diagnosticResult(token,name,DeviceDiagnostics.one(name,delivered?"pass":"fail",delivered?"Собственное тестовое уведомление получено Listener; правила пользователя не запускались":"Callback Listener не получен за 3 секунды"));
            },3000);
        }catch(Exception e){diagnosticResult(token,name,DeviceDiagnostics.one(name,"fail",e.getClass().getSimpleName()));}
    }
    public final class NativeApi {
        @JavascriptInterface public void requestDiagnosticAudioPermission(){runOnUiThread(()->requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},DIAG_AUDIO));}
        @JavascriptInterface public void requestDiagnosticNotificationPermission(){runOnUiThread(()->requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},DIAG_POST));}
        @JavascriptInterface public void runFullDiagnostics(String token){
            if(token==null||!token.matches("[0-9-]{1,40}"))return;
            runOnUiThread(()->{
                GlyphPlayer.get(MainActivity.this).diagnosticProbe(result->diagnosticResult(token,"Подключение и передача Glyph SDK",result));
                testNotification(token);
            });
            new Thread(()->diagnosticResult(token,"Сеть HTTPS и обновления",DeviceDiagnostics.network()),"gf-diagnostics-network").start();
            new Thread(()->diagnosticResult(token,"Аудиовыход Visualizer",DeviceDiagnostics.audioProbe(MainActivity.this)),"gf-diagnostics-audio").start();
        }
        @JavascriptInterface public String getDiagnostics(){return DeviceDiagnostics.collect(MainActivity.this).toString();}
        @JavascriptInterface public void shareReport(String json){
            if(json==null||json.length()>200_000)return;
            runOnUiThread(()->{
                Intent send=new Intent(Intent.ACTION_SEND);
                send.setType("text/plain");send.putExtra(Intent.EXTRA_TEXT,json);
                try{startActivity(Intent.createChooser(send,"Отправить отчёт GlyphForge"));}
                catch(Exception e){status("Не удалось открыть меню отправки");}
            });
        }
        @JavascriptInterface public void installUpdate(String url){
            runOnUiThread(()->UpdateInstaller.start(MainActivity.this,url,message->runOnUiThread(()->{
                if(web!=null&&!isDestroyed())web.evaluateJavascript("window.GlyphInstallStatus("+org.json.JSONObject.quote(message)+")",null);
            })));
        }
        @JavascriptInterface public void checkForUpdates(){
            UpdateChecker.check(MainActivity.this, result -> runOnUiThread(()->{
                if(web!=null)web.evaluateJavascript("window.GlyphUpdateResult("+org.json.JSONObject.quote(result)+")",null);
            }));
        }
        @JavascriptInterface public boolean sdkAvailable(){
            try{Class.forName("com.nothing.ketchum.GlyphMatrixManager");return true;}
            catch(Throwable ignored){return false;}
        }
        @JavascriptInterface public String getState(){return Store.read(MainActivity.this);}
        @JavascriptInterface public void saveState(String json){
            if(json!=null&&json.length()<=64_000_000) {Store.write(MainActivity.this,json);RuleScheduler.refresh(MainActivity.this);GlyphNotificationListener.refreshRegistrations();}
        }
        @JavascriptInterface public void play(String json){
            runOnUiThread(()->{
                try { GlyphPlayer.get(MainActivity.this).playJson(json,false, msg->status(msg)); }
                catch(Exception ex){status("Ошибка Glyph: "+ex.getMessage());}
            });
        }
        @JavascriptInterface public void stop(){runOnUiThread(()->GlyphPlayer.get(MainActivity.this).stop());}
        @JavascriptInterface public void startMusic(String profile,String mode){
            if(profile==null||profile.length()>12000)return;
            runOnUiThread(()->{pendingMusicProfile=profile;pendingMusicMode="capture".equals(mode)?"capture":"mix";beginMusic(profile,pendingMusicMode);});
        }
        @JavascriptInterface public void stopMusic(){runOnUiThread(()->{
            Intent i=new Intent(MainActivity.this,MusicVisualizerService.class);
            i.setAction(MusicVisualizerService.ACTION_STOP);startService(i);
        });}
        @JavascriptInterface public String musicStatus(){
            android.content.SharedPreferences prefs=getSharedPreferences(MusicVisualizerService.STATUS,MODE_PRIVATE);
            return (MusicVisualizerService.isRunning()?"RUNNING: ":"STOPPED: ")+prefs.getString("message","");
        }
        @JavascriptInterface public void openAppSettings(){runOnUiThread(()->{
            try{startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())));}
            catch(Exception e){status("Открой системные настройки приложения GlyphForge");}
        });}
        @JavascriptInterface public void openNotificationSettings(){runOnUiThread(()->{
            startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
        });}
        @JavascriptInterface public String getAutomationKey(){return AutomationReceiver.key(MainActivity.this);}
        @JavascriptInterface public void requestCallPermission(){runOnUiThread(()->{
            if(checkSelfPermission(Manifest.permission.READ_PHONE_STATE)!=PackageManager.PERMISSION_GRANTED){
                requestPermissions(new String[]{Manifest.permission.READ_PHONE_STATE},PERMISSION_PHONE);
            }else status("Доступ к состоянию звонков уже разрешён");
        });}
        @JavascriptInterface public void openRingtoneSettings(){runOnUiThread(()->{
            try{startActivity(new Intent(Settings.ACTION_SOUND_SETTINGS));}
            catch(Exception e){status("Настройки звука недоступны");}
        });}
        @JavascriptInterface public void openToyManager(){runOnUiThread(()->{
            try { Intent i=new Intent();i.setComponent(new ComponentName("com.nothing.thirdparty","com.nothing.thirdparty.matrix.toys.manager.ToysManagerActivity"));startActivity(i);}
            catch(Exception e){status("Открой Настройки → Glyph Interface → Flip to Glyph → Always-on Glyph Toy");}
        });}
        @JavascriptInterface public void openCommunity(String url){
            if(url==null||(!url.startsWith("https://app.glyphmuseum.com/")&&!url.startsWith("https://playground.nothing.tech/")))return;
            runOnUiThread(()->{Intent i=new Intent(MainActivity.this,CommunityActivity.class);i.putExtra("url",url);startActivity(i);});
        }
        @JavascriptInterface public void openExternal(String url){
            try{Uri uri=Uri.parse(url);if(!"https".equals(uri.getScheme()))return;runOnUiThread(()->startActivity(new Intent(Intent.ACTION_VIEW,uri)));}
            catch(Exception e){status("Не удалось открыть ссылку");}
        }
        @JavascriptInterface public void saveBase64(String base64,String filename,String mime){
            if(base64==null||base64.length()>21_000_000)return;
            try {
                byte[] bytes=Base64.decode(base64,Base64.DEFAULT);
                String safe=(filename==null?"glyphforge.gforge":filename).replaceAll("[^a-zA-Zа-яА-ЯёЁ0-9_ .()-]","_");
                ContentValues v=new ContentValues();v.put(MediaStore.Downloads.DISPLAY_NAME,safe);v.put(MediaStore.Downloads.MIME_TYPE,mime==null?"application/json":mime);
                v.put(MediaStore.Downloads.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/GlyphForge");
                Uri dst=getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,v);
                if(dst==null)throw new Exception("Нет доступа к Downloads");
                try(OutputStream out=getContentResolver().openOutputStream(dst)){if(out==null)throw new Exception("Не удалось записать файл");out.write(bytes);}
                DiagnosticJournal.mark(MainActivity.this,"local_export_ok");status("Файл сохранён в Загрузки/GlyphForge");
            }catch(Exception e){DiagnosticJournal.mark(MainActivity.this,"local_export_failed");status("Ошибка экспорта: "+e.getMessage());}
        }
    }
}
