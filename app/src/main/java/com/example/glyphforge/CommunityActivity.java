package com.example.glyphforge;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
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

/** External community browsing in separate WebView WITHOUT any JavaScript bridge. */
public final class CommunityActivity extends Activity {
    private WebView browser;
    @Override public void onCreate(Bundle b){super.onCreate(b);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Color.rgb(15,15,15));
        LinearLayout header=new LinearLayout(this);header.setPadding(18,12,18,12);header.setGravity(android.view.Gravity.CENTER_VERTICAL);
        Button back=new Button(this);back.setText("‹ Назад");back.setOnClickListener(v->finish());header.addView(back);
        TextView title=new TextView(this);title.setText("  GLYPHFORGE · СООБЩЕСТВО");title.setTextColor(Color.WHITE);title.setTextSize(15);header.addView(title);
        root.addView(header);browser=new WebView(this);root.addView(browser,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
        browser.getSettings().setJavaScriptEnabled(true);
        browser.getSettings().setDomStorageEnabled(true);
        browser.getSettings().setAllowFileAccess(false);
        browser.getSettings().setAllowContentAccess(false);
        browser.getSettings().setAllowFileAccessFromFileURLs(false);
        browser.getSettings().setAllowUniversalAccessFromFileURLs(false);
        browser.setWebChromeClient(new WebChromeClient());
        browser.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest req){
                Uri url=req.getUrl();String scheme=url.getScheme();
                if("https".equals(scheme))return false;
                // No file:, data:, custom deep links into privileged functions.
                return true;
            }
        });
        browser.setDownloadListener((url,userAgent,contentDisposition,mimeType,contentLength)->{
            try{
                if(!url.startsWith("https://"))throw new Exception("Скачивание доступно только по HTTPS");
                String name=URLUtil.guessFileName(url,contentDisposition,mimeType).replaceAll("[^a-zA-Zа-яА-ЯёЁ0-9_ .()-]","_");
                DownloadManager.Request request=new DownloadManager.Request(Uri.parse(url));
                request.setTitle(name);
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                request.addRequestHeader("User-Agent",userAgent);
                String cookie=CookieManager.getInstance().getCookie(url);if(cookie!=null)request.addRequestHeader("Cookie",cookie);
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,"GlyphForge/"+name);
                ((DownloadManager)getSystemService(Context.DOWNLOAD_SERVICE)).enqueue(request);
                Toast.makeText(this,"Файл загружается в Downloads/GlyphForge",Toast.LENGTH_LONG).show();
            }catch(Exception ex){Toast.makeText(this,"Не удалось скачать: "+ex.getMessage(),Toast.LENGTH_LONG).show();}
        });
        String requested=getIntent().getStringExtra("url");
        if(requested==null||(!requested.startsWith("https://app.glyphmuseum.com/")&&!requested.startsWith("https://playground.nothing.tech/")))requested="https://app.glyphmuseum.com/";
        browser.loadUrl(requested);
    }
    @Override public void onBackPressed(){if(browser!=null&&browser.canGoBack())browser.goBack();else super.onBackPressed();}
    @Override protected void onDestroy(){if(browser!=null)browser.destroy();super.onDestroy();}
}
