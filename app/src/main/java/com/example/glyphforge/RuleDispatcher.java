package com.example.glyphforge;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.PowerManager;
import android.app.NotificationManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.media.AudioManager;
import android.media.AudioDeviceInfo;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Calendar;
import java.util.Locale;

/** Purely on-device rule matcher. Combines WHEN event and AND conditions; higher-priority rules win. */
public final class RuleDispatcher {
    private RuleDispatcher(){}
    public static void fire(Context context,String event,String app,String contents){
        try{
            JSONObject state=new JSONObject(Store.read(context));JSONArray rules=state.optJSONArray("rules");
            if(rules==null)return;
            JSONObject best=null;int score=Integer.MIN_VALUE;
            for(int i=0;i<rules.length();i++){
                JSONObject r=rules.optJSONObject(i);
                if(r==null||!r.optBoolean("enabled",true)||!event.equals(r.optString("event")))continue;
                if(!conditionsMatch(context,r)||!notificationMatch(event,r,app,contents))continue;
                if(!ready(context,r))continue;
                int n=r.optInt("priority",50)*100 + specificity(r);
                if(n>score){best=r;score=n;}
            }
            if(best!=null)play(context,state,best);
        }catch(Exception e){android.util.Log.w("GlyphForge.Rule","Cannot process event "+event,e);}
    }
    public static void fireById(Context context,String id){
        if(id==null)return;
        try{
            JSONObject state=new JSONObject(Store.read(context));JSONArray rules=state.optJSONArray("rules");if(rules==null)return;
            for(int i=0;i<rules.length();i++){
                JSONObject r=rules.optJSONObject(i);
                if(r!=null&&r.optBoolean("enabled",true)&&id.equals(r.optString("id"))&&conditionsMatch(context,r)&&ready(context,r)){
                    play(context,state,r);return;
                }
            }
        }catch(Exception e){android.util.Log.w("GlyphForge.Rule","Cannot play rule by id",e);}
    }
    private static boolean notificationMatch(String event,JSONObject r,String app,String content){
        if(!"notification".equals(event))return true;
        String pkg=r.optString("app","*").trim();if(!"*".equals(pkg)&&!pkg.equalsIgnoreCase(app))return false;
        String word=r.optString("keyword","").trim().toLowerCase(Locale.ROOT);
        return word.isEmpty() || (content!=null&&content.toLowerCase(Locale.ROOT).contains(word));
    }
    private static int specificity(JSONObject r){int n=0;
        if(!"*".equals(r.optString("app","*")))n+=10;
        if(!r.optString("keyword","").isEmpty())n+=20;
        for(String key:new String[]{"charge","screen","face","wifi","headphones","dnd"})if(!"any".equals(r.optString(key,"any")))n++;
        if(!r.isNull("minBattery")||!r.isNull("maxBattery"))n++;
        return n;
    }
    private static boolean conditionsMatch(Context c,JSONObject r){
        try{
            JSONArray days=r.optJSONArray("days");
            if(days!=null){int today=(Calendar.getInstance().get(Calendar.DAY_OF_WEEK)+5)%7+1;boolean found=false;
                for(int i=0;i<days.length();i++)if(days.optInt(i)==today)found=true;
                if(!found)return false;
            }
            Intent b=c.registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if(b==null)return false;
            int batStatus=b.getIntExtra(BatteryManager.EXTRA_STATUS,-1);
            boolean charging=batStatus==BatteryManager.BATTERY_STATUS_CHARGING||batStatus==BatteryManager.BATTERY_STATUS_FULL;
            String charge=r.optString("charge","any");if("yes".equals(charge)&&!charging)return false;
            if("no".equals(charge)&&charging)return false;
            int lvl=b.getIntExtra(BatteryManager.EXTRA_LEVEL,-1),scale=b.getIntExtra(BatteryManager.EXTRA_SCALE,100);
            int battery=scale>0&&lvl>=0?Math.round(lvl*100f/scale):-1;
            if(!r.isNull("minBattery")&&r.has("minBattery")&&(battery<0||battery<r.optInt("minBattery")))return false;
            if(!r.isNull("maxBattery")&&r.has("maxBattery")&&(battery<0||battery>r.optInt("maxBattery")))return false;
            String start=r.optString("from",""),end=r.optString("to","");
            if(start.matches("\\d{2}:\\d{2}")&&end.matches("\\d{2}:\\d{2}")&&!start.equals(end)){
                Calendar now=Calendar.getInstance();String t=String.format(Locale.ROOT,"%02d:%02d",now.get(Calendar.HOUR_OF_DAY),now.get(Calendar.MINUTE));
                if(start.compareTo(end)<0){if(t.compareTo(start)<0||t.compareTo(end)>=0)return false;}
                else if(t.compareTo(start)<0&&t.compareTo(end)>=0)return false;
            }
            String screen=r.optString("screen","any");if(!"any".equals(screen)){
                PowerManager pm=(PowerManager)c.getSystemService(Context.POWER_SERVICE);
                boolean on=pm!=null&&pm.isInteractive();if("on".equals(screen)!=on)return false;
            }
            String face=r.optString("face","any");if(!"any".equals(face)){
                if("down".equals(face)&&!GlyphNotificationListener.isFaceDown())return false;
                if("up".equals(face)&&!GlyphNotificationListener.isFaceUp())return false;
            }
            String wifi=r.optString("wifi","any");if(!"any".equals(wifi)){
                ConnectivityManager cm=(ConnectivityManager)c.getSystemService(Context.CONNECTIVITY_SERVICE);
                boolean on=false;if(cm!=null){Network net=cm.getActiveNetwork();NetworkCapabilities cap=cm.getNetworkCapabilities(net);on=cap!=null&&cap.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);}
                if("yes".equals(wifi)!=on)return false;
            }
            String headset=r.optString("headphones","any");if(!"any".equals(headset)){
                AudioManager am=(AudioManager)c.getSystemService(Context.AUDIO_SERVICE);boolean plugged=false;
                if(am!=null)for(AudioDeviceInfo info:am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)){
                    int type=info.getType();if(type==AudioDeviceInfo.TYPE_WIRED_HEADPHONES||type==AudioDeviceInfo.TYPE_WIRED_HEADSET||type==AudioDeviceInfo.TYPE_BLUETOOTH_A2DP||type==AudioDeviceInfo.TYPE_BLUETOOTH_SCO||type==AudioDeviceInfo.TYPE_USB_HEADSET){plugged=true;break;}}
                if("yes".equals(headset)!=plugged)return false;
            }
            String dnd=r.optString("dnd","any");if(!"any".equals(dnd)){
                NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
                if(nm==null)return false;
                boolean active=nm.getCurrentInterruptionFilter()!=NotificationManager.INTERRUPTION_FILTER_ALL;
                if("yes".equals(dnd)!=active)return false;
            }
            return true;
        }catch(Exception e){android.util.Log.w("GlyphForge.Rule","Condition not available",e);return false;}
    }
    private static boolean ready(Context c,JSONObject r){
        int secs=Math.max(0,r.optInt("cooldown",0));if(secs<=0)return true;
        long last=c.getSharedPreferences("cooldown",0).getLong(r.optString("id"),0);
        return last==0 || android.os.SystemClock.elapsedRealtime()-last>=secs*1000L;
    }
    private static void play(Context c,JSONObject state,JSONObject r)throws Exception{
        JSONArray frames=framesFor(state,r.optString("target",""),0);if(frames.length()==0)return;
        int brightness=Math.max(1,Math.min(100,r.optInt("brightness",100)));
        JSONArray scaled=new JSONArray();for(int i=0;i<frames.length();i++){
            JSONObject frame=frames.optJSONObject(i);if(frame==null)continue;
            JSONArray vals=frame.optJSONArray("pixels");if(vals==null)continue;
            JSONArray output=new JSONArray();for(int k=0;k<vals.length();k++)output.put(vals.optInt(k)*brightness/100);
            JSONObject copy=new JSONObject();copy.put("pixels",output);copy.put("duration",frame.optInt("duration",180));scaled.put(copy);
        }
        JSONObject req=new JSONObject();req.put("frames",scaled);req.put("repeat",Math.max(1,Math.min(5,r.optInt("repeat",1))));
        c.getSharedPreferences("cooldown",0).edit().putLong(r.optString("id"),android.os.SystemClock.elapsedRealtime()).apply();
        GlyphPlayer.get(c).playJson(req.toString(),false,null);
    }
    public static JSONArray framesFor(JSONObject state,String target,int depth){
        JSONArray out=new JSONArray();if(depth>2||target.length()<3)return out;
        JSONArray designs=state.optJSONArray("designs");
        if(target.startsWith("d:")&&designs!=null){
            for(int i=0;i<designs.length();i++){
                JSONObject d=designs.optJSONObject(i);
                if(d!=null&&target.substring(2).equals(d.optString("id"))){
                    JSONArray all=d.optJSONArray("frames");if(all==null)return out;
                    for(int k=0;k<Math.min(400,all.length());k++)out.put(all.opt(k));return out;
                }
            }
        }
        if(target.startsWith("s:")){
            JSONArray scenes=state.optJSONArray("scenes");if(scenes==null)return out;
            for(int i=0;i<scenes.length();i++){
                JSONObject scene=scenes.optJSONObject(i);if(scene==null||!scene.optString("id").equals(target.substring(2)))continue;
                JSONArray steps=scene.optJSONArray("steps");if(steps==null)return out;
                for(int j=0;j<Math.min(35,steps.length());j++){
                    JSONObject step=steps.optJSONObject(j);if(step==null)continue;
                    JSONArray sub=framesFor(state,"d:"+step.optString("assetId"),depth+1);
                    int repeats=Math.min(5,Math.max(1,step.optInt("repeat",1)));
                    for(int rep=0;rep<repeats;rep++)for(int k=0;k<sub.length()&&out.length()<400;k++)out.put(sub.opt(k));
                    int pause=step.optInt("pause",0);
                    if(pause>=60&&out.length()<400){JSONArray empty=new JSONArray();for(int x=0;x<169;x++)empty.put(0);JSONObject frame=new JSONObject();
                        try{frame.put("pixels",empty);frame.put("duration",Math.min(3000,pause));out.put(frame);}catch(Exception ignored) {}}
                }return out;
            }
        }
        return out;
    }
}
