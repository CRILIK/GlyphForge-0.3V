package com.example.glyphforge;
import android.content.Context;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.locks.ReentrantLock;
/** Local-only atomic file storage: no cloud and no analytics. */
public final class Store {
    private Store(){}
    private static final ReentrantLock LOCK=new ReentrantLock();
    public static String read(Context c){LOCK.lock();try{
        File f=new File(c.getFilesDir(),"glyphforge_state.json");
        if(!f.exists())return "";
        try(FileInputStream in=new FileInputStream(f)){return new String(in.readAllBytes(),StandardCharsets.UTF_8);}
    }catch(Exception e){return "";}finally{LOCK.unlock();}}
    public static void write(Context c,String data){LOCK.lock();try{
        if(data==null||data.length()>64_000_000)throw new IllegalArgumentException("Project library exceeds 64 MB; export and split library");
        // Validate before replacing existing data.
        JSONObject validated=new JSONObject(data);if(validated.optJSONArray("designs")==null||validated.optJSONArray("rules")==null||validated.optJSONArray("scenes")==null)throw new IllegalArgumentException("Invalid library");
        File temp=new File(c.getFilesDir(),"glyphforge_state.tmp");
        File dest=new File(c.getFilesDir(),"glyphforge_state.json");
        try(FileOutputStream o=new FileOutputStream(temp)){o.write(data.getBytes(StandardCharsets.UTF_8));o.getFD().sync();}
        if(!temp.renameTo(dest))throw new IllegalStateException("Atomic rename failed");
    }catch(Exception e){DiagnosticJournal.mark(c,"state_save_failed");android.util.Log.e("GlyphForge","Failed to save state");}finally{LOCK.unlock();}}
}
