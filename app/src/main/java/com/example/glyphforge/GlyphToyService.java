package com.example.glyphforge;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import org.json.JSONArray;
import org.json.JSONObject;
/** Official Always-on Glyph Toy hook for Phone (4a) Pro. */
public class GlyphToyService extends Service {
    @Override public IBinder onBind(Intent intent){
        try{
            JSONObject state=new JSONObject(Store.read(this));
            String target=state.optString("defaultToyId","");
            JSONArray designs=state.optJSONArray("designs");
            if(designs!=null&&designs.length()>0){
                JSONObject pick=null;
                for(int i=0;i<designs.length();i++){JSONObject d=designs.optJSONObject(i);if(d!=null&&(pick==null||d.optString("id").equals(target)))pick=d;}
                if(pick!=null){JSONObject req=new JSONObject();req.put("frames",pick.optJSONArray("frames"));req.put("repeat",2);
                    GlyphPlayer.get(this).playJson(req.toString(),true,null);}
            }
        }catch(Exception e){android.util.Log.e("GlyphForge.Toy","Cannot select AOD",e);}
        return null; // Per SDK's basic Glyph Toy service example.
    }
    @Override public boolean onUnbind(Intent intent){GlyphPlayer.get(this).stop();return false;}
}
