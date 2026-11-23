package com.example.glyphforge;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
/** An opt-in automation bridge. External intents must include the user's random secret. */
public final class AutomationReceiver extends BroadcastReceiver {
    public static final String ACTION="com.example.glyphforge.RUN";
    public static String key(Context c){
        android.content.SharedPreferences prefs=c.getSharedPreferences("automation",0);
        String s=prefs.getString("secret",null);
        if(s==null){s=UUID.randomUUID().toString().replace("-","");prefs.edit().putString("secret",s).apply();}return s;
    }
    @Override public void onReceive(Context c,Intent intent){
        if(intent==null||!ACTION.equals(intent.getAction()))return;
        String secret=intent.getStringExtra("key");if(secret==null)return;
        if(!MessageDigest.isEqual(secret.getBytes(StandardCharsets.UTF_8),key(c).getBytes(StandardCharsets.UTF_8)))return;
        String rule=intent.getStringExtra("ruleId");
        if(rule!=null&&!rule.isEmpty())RuleDispatcher.fireById(c,rule);
        else RuleDispatcher.fire(c,"external","","");
    }
}
