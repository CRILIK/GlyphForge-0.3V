package com.example.glyphforge;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import org.json.JSONObject;
import java.net.URL;
import java.net.HttpURLConnection;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/** Checks trusted GitHub release metadata. No silent installation or unknown download hosts. */
public final class UpdateChecker {
    private UpdateChecker(){}
    private static final String API="https://api.github.com/repos/CRILIK/GlyphForge-0.3V/releases/latest";
    private static final String RELEASES="https://github.com/CRILIK/GlyphForge-0.3V/releases";
    public static void check(Context c,Consumer<String> callback){
        new Thread(()->{JSONObject response=new JSONObject();HttpURLConnection conn=null;
            try{
                URL url=new URL(API);conn=(HttpURLConnection)url.openConnection();
                conn.setConnectTimeout(7000);conn.setReadTimeout(7000);
                conn.setRequestProperty("Accept","application/vnd.github+json");
                conn.setRequestProperty("User-Agent","GlyphForge-Android");
                int code=conn.getResponseCode();
                if(code==404){response.put("available",false);response.put("message","Релизов пока нет. Последняя сборка находится в GitHub Actions.");}
                else if(code!=200){response.put("available",false);response.put("message","GitHub: HTTP "+code);}
                else{
                    byte[] bytes;
                    try(InputStream in=conn.getInputStream()){bytes=in.readNBytes(200000);}
                    JSONObject release=new JSONObject(new String(bytes,StandardCharsets.UTF_8));
                    String tag=release.optString("tag_name","");
                    String html=release.optString("html_url",RELEASES);
                    if(!html.startsWith(RELEASES+"/"))throw new SecurityException("Unexpected release URL");
                    String current=c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionName;
                    boolean newer=compareVersions(tag,current)>0;
                    response.put("available",newer);response.put("version",tag);response.put("url",html);
                    org.json.JSONArray assets=release.optJSONArray("assets");
                    if(newer&&assets!=null&&tag.matches("v[0-9]+\\.[0-9]+\\.[0-9]+")){
                        for(int i=0;i<assets.length();i++){
                            JSONObject asset=assets.optJSONObject(i);if(asset==null)continue;
                            String apk=asset.optString("browser_download_url");
                            if(UpdateInstaller.allowedAsset(apk)&&apk.endsWith("/"+tag+"/GlyphForge-"+tag+".apk"))response.put("apkUrl",apk);
                        }
                    }
                    response.put("message",newer?"Новая версия опубликована. Установку Android запросит отдельно.":"Установлена последняя опубликованная версия ("+current+")");
                }
            }catch(Exception e){try{response.put("available",false);response.put("message","Не удалось проверить обновления: "+e.getClass().getSimpleName());}catch(Exception ignored){}}
            finally{if(conn!=null)conn.disconnect();callback.accept(response.toString());}
        },"GlyphForge-update-check").start();
    }
    public static int compareVersions(String a,String b){
        String[] pa=a.replaceFirst("^[vV]","").split("[.-]");String[] pb=b.replaceFirst("^[vV]","").split("[.-]");
        for(int i=0;i<3;i++){int x=component(pa,i),y=component(pb,i);if(x!=y)return Integer.compare(x,y);}return 0;
    }
    private static int component(String[] parts,int i){if(i>=parts.length)return 0;try{return Integer.parseInt(parts[i]);}catch(Exception e){return 0;}}
}
