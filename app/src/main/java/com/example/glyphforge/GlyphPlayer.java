package com.example.glyphforge;
import android.content.Context;
import android.content.ComponentName;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

/**
 * Nothing Glyph Matrix SDK 2.0 adapter. Uses reflection so editor can be built
 * without the optional official AAR; REAL DEVICE TESTING IS STILL REQUIRED.
 * The app avoids private Android APIs, root, and any privileged system calls.
 */
public final class GlyphPlayer {
    public interface Status { void post(String msg); }
    private static volatile GlyphPlayer INSTANCE;
    public static GlyphPlayer get(Context context){
        if(INSTANCE==null){synchronized(GlyphPlayer.class){if(INSTANCE==null)INSTANCE=new GlyphPlayer(context.getApplicationContext());}}
        return INSTANCE;
    }
    private static final String TAG="GlyphForge.Glyph";
    private final Context context;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private Object manager,callback;
    private Class<?> managerClass;
    private boolean connected=false,connecting=false,toyMode=false;
    private boolean live=false;
    private int[] liveFrame;
    private final List<int[]> pixels=new ArrayList<>();
    private final List<Integer> delays=new ArrayList<>();
    private int cursor=0,loopsRemaining=0;
    private Status status;
    private final Runnable tick=new Runnable(){@Override public void run(){
        if(!connected||pixels.isEmpty()||loopsRemaining<=0)return;
        try {sendFrame(pixels.get(cursor),toyMode);}catch(Exception e){Log.e(TAG,"Matrix display failed",e);post("Glyph SDK: ошибка вывода; проверь журнал Android Studio");stop();return;}
        int delay=delays.get(cursor);
        cursor++;
        if(cursor>=pixels.size()){cursor=0;loopsRemaining--;}
        if(loopsRemaining>0)handler.postDelayed(this,delay);
        else handler.postDelayed(()->closeOutput(),delay);
    }};
    private GlyphPlayer(Context c){context=c;}
    private void post(String message){Log.i(TAG,message);if(status!=null)status.post(message);}
    public void playJson(String json,boolean asToy,Status callback){
        if(Looper.myLooper()!=Looper.getMainLooper()){
            handler.post(()->playJson(json,asToy,callback));return;
        }
        try {
            JSONObject root=new JSONObject(json);
            JSONArray frames=root.optJSONArray("frames");
            if(frames==null||frames.length()<1)throw new IllegalArgumentException("Нет кадров");
            live=false;liveFrame=null;
            stopInternal(false);
            for(int f=0;f<Math.min(frames.length(),400);f++){
                JSONObject frame=frames.optJSONObject(f);if(frame==null)continue;
                JSONArray arr=frame.optJSONArray("pixels");if(arr==null||arr.length()!=169)continue;
                int[] values=new int[169];
                for(int i=0;i<169;i++){
                    int x=i%13-6,y=i/13-6;
                    values[i]=x*x+y*y<=41?Math.max(0,Math.min(255,arr.optInt(i))):0;
                }
                pixels.add(values);delays.add(Math.max(80,Math.min(3000,frame.optInt("duration",180))));
            }
            if(pixels.isEmpty())throw new IllegalArgumentException("Требуются кадры 13×13");
            this.toyMode=asToy;
            this.loopsRemaining=Math.max(1,Math.min(5,root.optInt("repeat",1)));
            this.status=callback;
            if(connected){handler.post(tick);return;}
            connect();
        }catch(Exception e){post("Не удалось запустить глиф: "+e.getMessage());}
    }
    /** Streaming API used by the foreground music service, no repeat/loop limit. */
    public void playLiveFrame(int[] values){
        if(Looper.myLooper()!=Looper.getMainLooper()){handler.post(()->playLiveFrame(values));return;}
        if(values==null||values.length!=169)return;
        if(!live){stopInternal(true);live=true;}
        liveFrame=values.clone();
        if(connected){try{sendFrame(liveFrame,false);}catch(Exception e){Log.e(TAG,"Live matrix error",e);}}
        else if(!connecting){try{connect();}catch(Exception e){Log.e(TAG,"Cannot connect SDK",e);}}
    }
    public void stopLive(){
        if(Looper.myLooper()!=Looper.getMainLooper()){handler.post(this::stopLive);return;}
        if(live){live=false;liveFrame=null;closeOutput();}
    }
    private void connect() throws Exception {
        if(connecting)return;
        managerClass=Class.forName("com.nothing.ketchum.GlyphMatrixManager");
        manager=managerClass.getMethod("getInstance",Context.class).invoke(null,context);
        Class<?> iface=Class.forName("com.nothing.ketchum.GlyphMatrixManager$Callback");
        callback=Proxy.newProxyInstance(iface.getClassLoader(),new Class[]{iface},(proxy,method,args)->{
            if("onServiceConnected".equals(method.getName())){
                handler.post(()->{
                    try{
                        String target="A069P";
                        try{Class<?> glyph=Class.forName("com.nothing.ketchum.Glyph");target=(String)glyph.getField("DEVICE_25111p").get(null);}catch(Exception ignored){}
                        managerClass.getMethod("register",String.class).invoke(manager,target);
                        connected=true;connecting=false;
                        post("Nothing SDK подключён · Phone (4a) Pro");
                        handler.removeCallbacks(tick);
                        if(live && liveFrame!=null){try{sendFrame(liveFrame,false);}catch(Exception e){post("Ошибка вывода Music Glyph: "+e.getMessage());}}
                        else handler.post(tick);
                    }catch(Exception e){post("SDK register: "+e.getMessage());Log.e(TAG,"register failed",e);connecting=false;}
                });
            }else if("onServiceDisconnected".equals(method.getName())){
                handler.post(()->{connected=false;connecting=false;post("Glyph SDK отключён");});
            }
            if(method.getReturnType()==boolean.class)return false;
            if(method.getReturnType()==int.class)return 0;
            return null;
        });
        connecting=true;
        managerClass.getMethod("init",iface).invoke(manager,callback);
        handler.postDelayed(()->{if(connecting){connecting=false;post("Glyph SDK: подключение не завершилось. Проверь телефон и разрешения");}},5000);
    }
    private Object newBuilder(String name)throws Exception{return Class.forName(name).getDeclaredConstructor().newInstance();}
    private static Method named(Class<?> c,String name,int n)throws NoSuchMethodException{
        for(Method m:c.getMethods())if(m.getName().equals(name)&&m.getParameterTypes().length==n)return m;
        throw new NoSuchMethodException(name+"("+n+") in "+c);
    }
    private static Bitmap toBitmap(int[] pixels){
        int[] argb=new int[169];
        for(int i=0;i<169;i++){
            int v=Math.max(0,Math.min(255,pixels[i]));
            argb[i]=Color.rgb(v,v,v);
        }
        return Bitmap.createBitmap(argb,13,13,Bitmap.Config.ARGB_8888);
    }
    private void sendFrame(int[] pixels,boolean toy)throws Exception {
        // Structured frames are 13x13-safe. Raw overload is primarily 25x25.
        Bitmap bm=toBitmap(pixels);
        try{
            Object objectBuilder=newBuilder("com.nothing.ketchum.GlyphMatrixObject$Builder");
            named(objectBuilder.getClass(),"setImageSource",1).invoke(objectBuilder,bm);
            Object object=named(objectBuilder.getClass(),"build",0).invoke(objectBuilder);
            Object frameBuilder=newBuilder("com.nothing.ketchum.GlyphMatrixFrame$Builder");
            // Some SDK variants let you explicitly select a 13x13 matrix.
            for(String sizeMethod:new String[]{"setMatrixLength","setMatrixSize","setSize","setMatrixDimension"}){
                try{Method m=frameBuilder.getClass().getMethod(sizeMethod,int.class);m.invoke(frameBuilder,13);break;}catch(NoSuchMethodException ignored){}
            }
            named(frameBuilder.getClass(),"addTop",1).invoke(frameBuilder,object);
            Object frame;
            try {frame=named(frameBuilder.getClass(),"build",0).invoke(frameBuilder);}
            catch(NoSuchMethodException e){frame=named(frameBuilder.getClass(),"build",1).invoke(frameBuilder,context);}
            String method=toy?"setMatrixFrame":"setAppMatrixFrame";
            Method setter=null;
            for(Method m:managerClass.getMethods())if(m.getName().equals(method)&&m.getParameterTypes().length==1&&m.getParameterTypes()[0].isAssignableFrom(frame.getClass())){setter=m;break;}
            if(setter==null)throw new NoSuchMethodException(method+"(GlyphMatrixFrame)");
            setter.invoke(manager,frame);
        }finally{ /* tiny bitmap is intentionally kept alive until the SDK copies it */ }
    }
    private void closeOutput(){
        if(manager!=null&&connected){
            try {if(!toyMode)managerClass.getMethod("closeAppMatrix").invoke(manager);}catch(Exception e){Log.w(TAG,"closeAppMatrix",e);}
        }
    }
    private void stopInternal(boolean close){
        handler.removeCallbacks(tick);
        pixels.clear();delays.clear();cursor=0;loopsRemaining=0;
        if(close)closeOutput();
    }
    public void stop(){if(Looper.myLooper()!=Looper.getMainLooper()){handler.post(this::stop);return;}stopInternal(true);}
}
