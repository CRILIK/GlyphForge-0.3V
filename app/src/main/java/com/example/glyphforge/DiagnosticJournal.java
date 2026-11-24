package com.example.glyphforge;
import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
/** Only fixed event codes and timestamps, never URLs, contents or raw exceptions. */
public final class DiagnosticJournal {
    private DiagnosticJournal(){}
    public static synchronized void mark(Context c,String code){
        if(code==null||!code.matches("[a-z0-9_]{1,64}"))return;
        try{
            JSONArray old=read(c),fresh=new JSONArray();
            for(int i=Math.max(0,old.length()-29);i<old.length();i++)fresh.put(old.get(i));
            fresh.put(new JSONObject().put("timeMs",System.currentTimeMillis()).put("code",code));
            c.getSharedPreferences("gf_diagnostic_events",0).edit().putString("events",fresh.toString()).apply();
        }catch(Exception ignored){}
    }
    public static synchronized void markEvent(Context c,String code){
        JSONArray events=read(c);long now=System.currentTimeMillis();
        for(int i=events.length()-1;i>=0;i--){JSONObject e=events.optJSONObject(i);if(e!=null&&code.equals(e.optString("code"))&&now-e.optLong("timeMs")<300000)return;}
        mark(c,code);
    }
    public static synchronized JSONArray read(Context c){try{return new JSONArray(c.getSharedPreferences("gf_diagnostic_events",0).getString("events","[]"));}catch(Exception e){return new JSONArray();}}
}
