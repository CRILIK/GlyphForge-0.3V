package com.example.glyphforge;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telephony.TelephonyManager;
import android.Manifest;
import android.content.pm.PackageManager;
/** Best-effort system broadcasts. Android background policies may suppress them. */
public final class GlyphEventReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent){
        if(intent==null)return;
        String a=intent.getAction();
        if(Intent.ACTION_BOOT_COMPLETED.equals(a))RuleScheduler.refresh(context);
        else if(RuleScheduler.TIME_ACTION.equals(a)){
            RuleDispatcher.fireById(context,intent.getStringExtra("ruleId"));
            RuleScheduler.refresh(context); // schedule the next day
        }
        else if(Intent.ACTION_POWER_CONNECTED.equals(a))RuleDispatcher.fire(context,"charging","","");
        else if(Intent.ACTION_POWER_DISCONNECTED.equals(a))RuleDispatcher.fire(context,"charge_removed","","");
        else if(Intent.ACTION_BATTERY_LOW.equals(a))RuleDispatcher.fire(context,"battery_low","","");
        else if(TelephonyManager.ACTION_PHONE_STATE_CHANGED.equals(a)
            &&context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE)==PackageManager.PERMISSION_GRANTED){
            if(TelephonyManager.EXTRA_STATE_RINGING.equals(intent.getStringExtra(TelephonyManager.EXTRA_STATE)))
                RuleDispatcher.fire(context,"call","","");
        }
    }
}
