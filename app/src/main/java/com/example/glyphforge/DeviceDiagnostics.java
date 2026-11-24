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
import java.io.OutputStream;
import org.json.JSONObject;

/** Purely local, non-destructive diagnostics. Never reads notifications, media or contacts. */
public final class DeviceDiagnostics {
    private DeviceDiagnostics(){}
    public static JSONObject collect(Context c){
        JSONObject result=new JSONObject();
        try{
            result.put("mode","android");result.put("androidSdk",Build.VERSION.SDK_INT);
            result.put("deviceModel",Build.MANUFACTURER+" "+Build.MODEL);
            result.put("sdkInstalled",sdkInstalled());
            result.put("notificationAccess", notificationAllowed(c));
            result.put("audioPermission",c.checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED);
            result.put("callPermission",c.checkSelfPermission(Manifest.permission.READ_PHONE_STATE)==PackageManager.PERMISSION_GRANTED);
            String raw=Store.read(c);
            result.put("savedLibraryValid",raw.isEmpty()||new JSONObject(raw).optJSONArray("designs")!=null);
            result.put("downloadsWritable",checkDownloads(c));
            result.put("hardwareUnverified",true);
            // No device IDs, notification contents, location, phone number, SSIDs or media metadata.
        }catch(Exception ex){try{result.put("error",ex.getClass().getSimpleName());}catch(Exception ignored){}}
        return result;
    }
    private static boolean sdkInstalled(){try{Class.forName("com.nothing.ketchum.GlyphMatrixManager");return true;}catch(Throwable ex){return false;}}
    private static boolean notificationAllowed(Context c){
        String enabled=Settings.Secure.getString(c.getContentResolver(),"enabled_notification_listeners");
        return enabled!=null&&enabled.contains(c.getPackageName()+"/");
    }
    private static boolean checkDownloads(Context c){
        Uri file=null;
        try{
            ContentValues v=new ContentValues();v.put(MediaStore.Downloads.DISPLAY_NAME,".glyphforge-test-"+System.nanoTime()+".tmp");
            v.put(MediaStore.Downloads.MIME_TYPE,"application/octet-stream");
            v.put(MediaStore.Downloads.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/GlyphForge");
            file=c.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,v);
            if(file==null)return false;
            try(OutputStream out=c.getContentResolver().openOutputStream(file)){if(out==null)return false;out.write(42);}
            return true;
        }catch(Exception ex){return false;}
        finally{if(file!=null)try{c.getContentResolver().delete(file,null,null);}catch(Exception ignored){}}
    }
}
