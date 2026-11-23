package com.example.glyphforge;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Calendar;
/** Inexact, best-effort daily alarms. Never requests exact alarm privileges. */
public final class RuleScheduler {
    private RuleScheduler(){}
    public static final String TIME_ACTION="com.example.glyphforge.TIME_RULE";
    public static void refresh(Context ctx){
        try{
            AlarmManager am=(AlarmManager)ctx.getSystemService(Context.ALARM_SERVICE);
            if(am==null)return;
            int count=ctx.getSharedPreferences("schedule",0).getInt("count",0);
            for(int i=0;i<count;i++){
                PendingIntent old=PendingIntent.getBroadcast(ctx,4200+i,intent(ctx,"",true),PendingIntent.FLAG_NO_CREATE|PendingIntent.FLAG_IMMUTABLE);
                if(old!=null){am.cancel(old);old.cancel();}
            }
            JSONObject state=new JSONObject(Store.read(ctx));JSONArray rules=state.optJSONArray("rules");int n=0;
            if(rules!=null)for(int i=0;i<rules.length();i++){
                JSONObject r=rules.optJSONObject(i);
                if(r==null||!r.optBoolean("enabled",true)||!"time".equals(r.optString("event")))continue;
                String[] t=r.optString("time","21:00").split(":");if(t.length!=2)continue;
                int hour=Integer.parseInt(t[0]),minute=Integer.parseInt(t[1]);if(hour<0||hour>23||minute<0||minute>59)continue;
                Calendar c=Calendar.getInstance();c.set(Calendar.HOUR_OF_DAY,hour);c.set(Calendar.MINUTE,minute);c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);
                if(c.getTimeInMillis()<=System.currentTimeMillis())c.add(Calendar.DAY_OF_YEAR,1);
                PendingIntent pi=PendingIntent.getBroadcast(ctx,4200+n,intent(ctx,r.optString("id"),false),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,c.getTimeInMillis(),pi);n++;if(n>=200)break;
            }
            ctx.getSharedPreferences("schedule",0).edit().putInt("count",n).apply();
        }catch(Exception e){android.util.Log.w("GlyphForge","Could not refresh alarms",e);}
    }
    private static Intent intent(Context c,String ruleId,boolean cancel){
        Intent i=new Intent(c,GlyphEventReceiver.class).setAction(TIME_ACTION);
        if(!cancel)i.putExtra("ruleId",ruleId);
        return i;
    }
}
