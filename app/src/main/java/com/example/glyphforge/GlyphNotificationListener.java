package com.example.glyphforge;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.app.Notification;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.BatteryManager;
import org.json.JSONObject;
import org.json.JSONArray;
/** Android sends notification events to an explicitly authorized service. Optional sensor triggers run here. */
public final class GlyphNotificationListener extends NotificationListenerService implements SensorEventListener {
    private BroadcastReceiver receiver;
    private SensorManager sensors;
    private boolean shakeActive;
    private long lastShake;
    private int lastLevel=-1;
    private static GlyphNotificationListener active;
    private static volatile boolean faceDown=false,faceUp=false;
    public static boolean isFaceDown(){return faceDown;}
    public static boolean isFaceUp(){return faceUp;}
    @Override public void onListenerConnected(){super.onListenerConnected();active=this;registerEvents();refreshSensors();}
    public static void refreshRegistrations(){GlyphNotificationListener obj=active;if(obj!=null)obj.refreshSensors();}
    private void registerEvents(){
        if(receiver!=null)return;
        receiver=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){if(i==null)return;
            String action=i.getAction();if(Intent.ACTION_SCREEN_ON.equals(action))RuleDispatcher.fire(c,"screen_on","","");
            else if(Intent.ACTION_SCREEN_OFF.equals(action))RuleDispatcher.fire(c,"screen_off","","");
            else if(Intent.ACTION_HEADSET_PLUG.equals(action)&&i.getIntExtra("state",0)==1)RuleDispatcher.fire(c,"headphones","","");
            else if(Intent.ACTION_BATTERY_CHANGED.equals(action)){
                int level=i.getIntExtra(BatteryManager.EXTRA_LEVEL,-1),scale=i.getIntExtra(BatteryManager.EXTRA_SCALE,100);
                int pct=scale>0?Math.round(level*100f/scale):-1;
                if(lastLevel>=0&&lastLevel<100&&pct==100)RuleDispatcher.fire(c,"battery_full","","");
                lastLevel=pct;
            }
        }};
        IntentFilter f=new IntentFilter();f.addAction(Intent.ACTION_SCREEN_ON);f.addAction(Intent.ACTION_SCREEN_OFF);
        f.addAction(Intent.ACTION_HEADSET_PLUG);f.addAction(Intent.ACTION_BATTERY_CHANGED);
        registerReceiver(receiver,f);
    }
    private void refreshSensors(){
        if(sensors==null)sensors=(SensorManager)getSystemService(Context.SENSOR_SERVICE);
        if(sensors==null)return;
        boolean need=false;
        try{JSONObject state=new JSONObject(Store.read(this));JSONArray rr=state.optJSONArray("rules");if(rr!=null)for(int i=0;i<rr.length();i++){
            JSONObject r=rr.optJSONObject(i);if(r!=null&&r.optBoolean("enabled",true)&&("shake".equals(r.optString("event"))||!"any".equals(r.optString("face","any")))){need=true;break;}}
        }catch(Exception ignored){}
        if(need&&!shakeActive){Sensor accelerometer=sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);if(accelerometer!=null)shakeActive=sensors.registerListener(this,accelerometer,SensorManager.SENSOR_DELAY_NORMAL);}
        else if(!need&&shakeActive){sensors.unregisterListener(this);shakeActive=false;}
    }
    @Override public void onSensorChanged(SensorEvent e){if(e.sensor.getType()!=Sensor.TYPE_ACCELEROMETER)return;
        float x=e.values[0],y=e.values[1],z=e.values[2];float magnitude=(float)Math.sqrt(x*x+y*y+z*z);
        if(magnitude>7&&magnitude<13){faceDown=z< -7;faceUp=z>7;}
        long now=android.os.SystemClock.elapsedRealtime();if(magnitude>21&&now-lastShake>3000){lastShake=now;RuleDispatcher.fire(this,"shake","","");}
    }
    @Override public void onAccuracyChanged(Sensor sensor,int accuracy){}
    @Override public void onNotificationPosted(StatusBarNotification sbn){
        if(sbn==null||sbn.getPackageName().equals(getPackageName()))return;
        Notification n=sbn.getNotification();if(n==null||(n.flags & Notification.FLAG_GROUP_SUMMARY)!=0)return;
        CharSequence title=n.extras.getCharSequence(Notification.EXTRA_TITLE,"");
        CharSequence body=n.extras.getCharSequence(Notification.EXTRA_TEXT,"");
        RuleDispatcher.fire(this,"notification",sbn.getPackageName(),title+" "+body);
    }
    @Override public void onListenerDisconnected(){cleanup();super.onListenerDisconnected();}
    @Override public void onDestroy(){cleanup();super.onDestroy();}
    private void cleanup(){if(active==this)active=null;if(shakeActive&&sensors!=null)sensors.unregisterListener(this);shakeActive=false;
        if(receiver!=null){try{unregisterReceiver(receiver);}catch(Exception ignored){}receiver=null;}}
}
