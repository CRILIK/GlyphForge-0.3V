package com.example.glyphforge;
import android.app.Activity;
import android.content.Intent;
import android.content.ClipData;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.provider.Settings;
import java.io.*;
import java.net.URL;
import javax.net.ssl.HttpsURLConnection;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class UpdateInstaller {
    private static final String PREFIX="https://github.com/CRILIK/GlyphForge-0.3V/releases/download/";
    private static final AtomicBoolean BUSY=new AtomicBoolean(false);
    private static final long MAX_BYTES=100L*1024*1024;
    private UpdateInstaller(){}
    static boolean allowedAsset(String url){
        if(url==null||!url.startsWith(PREFIX))return false;
        String[] parts=url.substring(PREFIX.length()).split("/",-1);
        return parts.length==2&&parts[0].matches("v[0-9]+\\.[0-9]+\\.[0-9]+")&&parts[1].equals("GlyphForge-"+parts[0]+".apk");
    }
    static boolean allowedRedirect(URL url){
        String host=url.getHost();
        return "https".equals(url.getProtocol())&&url.getUserInfo()==null&&(url.getPort()==-1||url.getPort()==443)&&
                (host.equals("github.com")||host.equals("release-assets.githubusercontent.com")||host.equals("objects.githubusercontent.com"));
    }
    public static void start(Activity activity,String url,Consumer<String> status){
        if(!allowedAsset(url)){status.accept("Некорректный адрес APK");return;}
        if(!activity.getPackageManager().canRequestPackageInstalls()){
            status.accept("Разреши установку из этого источника в Android, затем снова нажми «Обновить».");
            try{activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+activity.getPackageName())));}
            catch(Exception e){status.accept("Разреши установку неизвестных приложений в настройках Android.");}
            return;
        }
        if(!BUSY.compareAndSet(false,true)){status.accept("Загрузка уже выполняется");return;}
        new Thread(()->{
            File temp=new File(activity.getCacheDir(),"gf-update.part.apk"),apk=new File(activity.getCacheDir(),"gf-update.apk");
            try{
                download(url,temp);
                PackageManager pm=activity.getPackageManager();
                PackageInfo incoming=pm.getPackageArchiveInfo(temp.getAbsolutePath(),PackageManager.GET_SIGNING_CERTIFICATES);
                PackageInfo installed=pm.getPackageInfo(activity.getPackageName(),PackageManager.GET_SIGNING_CERTIFICATES);
                if(incoming==null||!activity.getPackageName().equals(incoming.packageName))throw new IOException("Неверное имя пакета APK");
                if(incoming.getLongVersionCode()<=installed.getLongVersionCode())throw new IOException("Версия APK не новее установленной");
                if(incoming.signingInfo==null||installed.signingInfo==null||!sameSigners(incoming.signingInfo.getApkContentsSigners(),installed.signingInfo.getApkContentsSigners()))
                    throw new IOException("Подпись несовместима. Сохрани библиотеку; для перехода с debug необходима однократная переустановка.");
                if(apk.exists()&&!apk.delete())throw new IOException("Не удалось заменить файл обновления");
                if(!temp.renameTo(apk))throw new IOException("Не удалось сохранить обновление");
                activity.runOnUiThread(()->{
                    if(activity.isFinishing()||activity.isDestroyed())return;
                    try{
                        Intent install=new Intent(Intent.ACTION_VIEW);
                        install.setDataAndType(UpdateFileProvider.URI,"application/vnd.android.package-archive");
                        install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        install.setClipData(ClipData.newRawUri("GlyphForge APK",UpdateFileProvider.URI));
                        activity.startActivity(install);status.accept("APK проверен. Подтверди обновление в Android.");
                    }catch(Exception e){status.accept("Не удалось открыть установщик: "+e.getClass().getSimpleName());}
                });
            }catch(Exception e){status.accept("Обновление не установлено: "+(e instanceof IOException?e.getMessage():e.getClass().getSimpleName()));}
            finally{temp.delete();BUSY.set(false);}
        },"gf-update-download").start();
    }
    private static boolean sameSigners(Signature[] a,Signature[] b){
        if(a==null||b==null||a.length==0||a.length!=b.length)return false;
        return new java.util.HashSet<Signature>(Arrays.asList(a)).equals(new java.util.HashSet<Signature>(Arrays.asList(b)));
    }
    private static void download(String source,File dst)throws IOException{
        URL url=new URL(source);int redirects=0;
        while(true){
            if(!allowedRedirect(url))throw new IOException("Небезопасный адрес загрузки");
            HttpsURLConnection conn=(HttpsURLConnection)url.openConnection();
            try{
                conn.setInstanceFollowRedirects(false);conn.setConnectTimeout(10000);conn.setReadTimeout(20000);
                conn.setRequestProperty("User-Agent","GlyphForge-Android");
                int code=conn.getResponseCode();
                if(code==301||code==302||code==303||code==307||code==308){
                    String location=conn.getHeaderField("Location");if(location==null||++redirects>5)throw new IOException("Ошибка перенаправления");
                    url=new URL(url,location);continue;
                }
                if(code!=200)throw new IOException("HTTP "+code);
                if(conn.getContentLengthLong()>MAX_BYTES)throw new IOException("APK слишком большой");
                try(InputStream in=conn.getInputStream();OutputStream out=new FileOutputStream(dst)){
                    byte[] buffer=new byte[16384];long count=0;int n;while((n=in.read(buffer))!=-1){count+=n;if(count>MAX_BYTES)throw new IOException("APK слишком большой");out.write(buffer,0,n);}
                    if(count<1000)throw new IOException("Неполный APK");
                }
                return;
            }finally{conn.disconnect();}
        }
    }
}
