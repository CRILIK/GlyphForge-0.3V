package com.example.glyphforge;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.JsPromptResult;
import android.webkit.URLUtil;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceRequest;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.graphics.Color;
import java.io.OutputStream;
import java.security.SecureRandom;
import org.json.JSONObject;

/** Untrusted community pages cannot access GlyphNative or application state. */
public final class CommunityActivity extends Activity {
    private WebView browser;
    private final SecureRandom random=new SecureRandom();
    private String pendingToken, pendingName, pendingMime, pendingOrigin;
    private static final int MAX_BLOB=8_000_000;

    @Override public void onCreate(Bundle b){super.onCreate(b);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Color.rgb(15,15,15));
        // Android 15 edge-to-edge otherwise puts the back button under the status bar.
        root.setOnApplyWindowInsetsListener((view,insets)->{
            android.graphics.Insets bars=insets.getInsets(android.view.WindowInsets.Type.systemBars()|android.view.WindowInsets.Type.displayCutout());
            view.setPadding(bars.left,bars.top,bars.right,bars.bottom);
            return insets;
        });
        LinearLayout header=new LinearLayout(this);header.setPadding(dp(8),dp(12),dp(8),dp(10));header.setGravity(android.view.Gravity.CENTER_VERTICAL);
        Button back=new Button(this);back.setText("‹ Назад");back.setMinHeight(dp(48));back.setOnClickListener(v->finish());header.addView(back);
        TextView title=new TextView(this);title.setText("  Сообщество");title.setTextColor(Color.WHITE);title.setTextSize(14);
        header.addView(title,new LinearLayout.LayoutParams(0,dp(48),1));
        Button external=new Button(this);external.setText("↗");external.setContentDescription("Открыть сайт во внешнем браузере");external.setMinHeight(dp(48));
        external.setOnClickListener(v->{try{Uri u=Uri.parse(browser.getUrl());if("https".equals(u.getScheme()))startActivity(new Intent(Intent.ACTION_VIEW,u));}catch(Exception ignored){}});
        header.addView(external);
        header.setMinimumHeight(dp(64));
        root.addView(header);
        browser=new WebView(this);root.addView(browser,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
        root.requestApplyInsets();
        browser.getSettings().setJavaScriptEnabled(true);browser.getSettings().setDomStorageEnabled(true);
        browser.getSettings().setMixedContentMode(android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW);browser.getSettings().setAllowFileAccess(false);browser.getSettings().setAllowContentAccess(false);
        browser.getSettings().setAllowFileAccessFromFileURLs(false);browser.getSettings().setAllowUniversalAccessFromFileURLs(false);
        browser.setWebChromeClient(new WebChromeClient(){
            // A one-shot callback for the blob: URL explicitly downloaded by the user.
            // No addJavascriptInterface() is exposed to untrusted third-party pages.
            @Override public boolean onJsPrompt(WebView view,String url,String message,String value,JsPromptResult result){
                if(pendingToken!=null&&("GF_BLOB_"+pendingToken).equals(message)&&pendingOrigin!=null&&pendingOrigin.equals(origin(url))){
                    String name=pendingName,mime=pendingMime;
                    pendingToken=null;pendingName=null;pendingMime=null;pendingOrigin=null;
                    result.confirm("");
                    if(value!=null&&value.length()<MAX_BLOB*2){saveDataUrl(value,name,mime);}
                    else toast("Этот файл слишком большой. Открой сайт кнопкой ↗.");
                    return true;
                }
                return super.onJsPrompt(view,url,message,value,result);
            }
        });
        browser.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest req){
                Uri url=req.getUrl();String scheme=url.getScheme();
                if("https".equals(scheme))return false;
                // Sites often create an in-memory blob: URL for GIF/JSON exports.
                // Blocking that navigation prevents WebView's download handler from receiving it.
                if("blob".equals(scheme)||"data".equals(scheme))return false;
                if("http".equals(scheme)){
                    // Do not navigate community browsing through an insecure transport.
                    toast("Эта ссылка использует HTTP: открой сайт через ↗ в обычном браузере.");return true;
                }
                return true;
            }
        });
        browser.setDownloadListener((url,userAgent,contentDisposition,mimeType,length)->{
            try{
                String safe=filename(URLUtil.guessFileName(url,contentDisposition,mimeType));if(!safeMedia(safe,mimeType)){DiagnosticJournal.mark(this,"community_unsupported_file");toast("Поддерживаются только изображения, видео и JSON/GFORGE");return;}
                if(url.startsWith("blob:")){if(safe.endsWith(".bin")||safe.endsWith(".dat"))safe="community-glyph-"+System.currentTimeMillis()+".gforge";downloadBlob(url,safe,mimeType);return;}
                if(!url.startsWith("https://")){
                    DiagnosticJournal.mark(this,"community_non_https_blocked");toast("Ссылка не HTTPS. Открой страницу кнопкой ↗ и скачай во внешнем браузере.");
                    return;
                }
                DownloadManager.Request request=new DownloadManager.Request(Uri.parse(url));
                request.setTitle(safe);request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                if(userAgent!=null)request.addRequestHeader("User-Agent",userAgent);
                // Authentication cookies are not copied to DownloadManager. For authenticated direct downloads use the site in the external browser.
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,"GlyphForge/"+safe);
                ((DownloadManager)getSystemService(Context.DOWNLOAD_SERVICE)).enqueue(request);
                DiagnosticJournal.mark(this,"community_https_enqueued");toast("Загрузка в Загрузки/GlyphForge…");
            }catch(Exception ex){DiagnosticJournal.mark(this,"community_download_failed");toast("Ошибка скачивания: "+ex.getMessage());}
        });
        String requested=getIntent().getStringExtra("url");
        if(requested==null||(!requested.startsWith("https://app.glyphmuseum.com/")&&!requested.startsWith("https://playground.nothing.tech/")))requested="https://app.glyphmuseum.com/";
        browser.loadUrl(requested);
    }
    private static String origin(String url){try{Uri u=Uri.parse(url);return "https".equals(u.getScheme())&&u.getHost()!=null?"https://"+u.getHost()+":"+u.getPort():null;}catch(Exception e){return null;}}
    private static boolean safeMedia(String name,String mime){String n=name.toLowerCase(java.util.Locale.ROOT);String m=mime==null?"":mime.toLowerCase(java.util.Locale.ROOT);if(n.endsWith(".apk")||m.contains("package-archive"))return false;return n.matches(".*\\.(gif|png|jpg|jpeg|webp|mp4|webm|json|gforge)$")||m.startsWith("image/")||m.startsWith("video/")||m.equals("application/json")||m.equals("application/octet-stream");}
    private int dp(int d){return (int)(d*getResources().getDisplayMetrics().density+.5f);}
    private String filename(String name){
        if(name==null||name.isBlank())name="community-glyph-"+System.currentTimeMillis()+".gif";
        String safe=name.replaceAll("[^a-zA-Zа-яА-ЯёЁ0-9_ .()-]","_");
        if(safe.length()>100)safe=safe.substring(0,100);
        return safe;
    }
    private void downloadBlob(String url,String name,String mime){
        String origin=browser.getUrl();
        if(origin==null||!origin.startsWith("https://")){toast("Не удалось подтвердить HTTPS-страницу");return;}
        // Token is valid only for this user-requested export and consumed once.
        String token=Long.toHexString(random.nextLong())+Long.toHexString(random.nextLong());
        pendingToken=token;pendingName=name;pendingMime=mime;pendingOrigin=origin(origin);
        String javascript="(async function(){try{const r=await fetch("+JSONObject.quote(url)+");"+
          "const b=await r.blob();if(b.size>"+MAX_BLOB+"){prompt('GF_BLOB_"+token+"','too-large');return;}"+
          "const fr=new FileReader();fr.onload=()=>prompt('GF_BLOB_"+token+"',String(fr.result));"+
          "fr.onerror=()=>prompt('GF_BLOB_"+token+"','error');fr.readAsDataURL(b);"+
          "}catch(e){prompt('GF_BLOB_"+token+"','error')}})();";
        browser.evaluateJavascript(javascript,null);
    }
    private void saveDataUrl(String dataUrl,String name,String mime){
        try{
            if(!dataUrl.startsWith("data:")||dataUrl.indexOf(",")<0)throw new Exception("Не удалось прочитать blob");
            String header=dataUrl.substring(0,dataUrl.indexOf(','));
            if(!header.contains(";base64"))throw new Exception("Ожидался base64");
            byte[] bytes=Base64.decode(dataUrl.substring(dataUrl.indexOf(',')+1),Base64.DEFAULT);
            if(bytes.length>MAX_BLOB)throw new Exception("Файл слишком большой");
            ContentValues values=new ContentValues();values.put(MediaStore.Downloads.DISPLAY_NAME,name);
            values.put(MediaStore.Downloads.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/GlyphForge");
            String actualMime=header.substring(5,header.indexOf(';'));if(!safeMedia(name,actualMime))throw new Exception("Неподдерживаемый тип файла");
            values.put(MediaStore.Downloads.MIME_TYPE,actualMime.isBlank()?"application/octet-stream":actualMime);
            Uri dest=getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values);
            if(dest==null)throw new Exception("Нет доступа к Загрузкам");
            try(OutputStream out=getContentResolver().openOutputStream(dest)){
                if(out==null)throw new Exception("Не удалось записать файл");out.write(bytes);
            }catch(Exception ex){getContentResolver().delete(dest,null,null);throw ex;}
            DiagnosticJournal.mark(this,"community_blob_saved");toast("Файл сохранён в Загрузки/GlyphForge: "+name);
        }catch(Exception e){DiagnosticJournal.mark(this,"community_blob_failed");toast("Ошибка экспорта: "+e.getMessage());}
    }
    private void toast(String message){runOnUiThread(()->Toast.makeText(this,message,Toast.LENGTH_LONG).show());}
    @Override public void onBackPressed(){if(browser!=null&&browser.canGoBack())browser.goBack();else finish();}
    @Override protected void onDestroy(){if(browser!=null)browser.destroy();super.onDestroy();}
}
