package com.example.glyphforge;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;

public final class UpdateFileProvider extends ContentProvider {
    static final Uri URI=Uri.parse("content://com.example.glyphforge.update/update.apk");
    private File file(Uri uri)throws FileNotFoundException{
        if(!URI.equals(uri)||getContext()==null)throw new FileNotFoundException();
        return new File(getContext().getCacheDir(),"gf-update.apk");
    }
    @Override public boolean onCreate(){return true;}
    @Override public String getType(Uri uri){return URI.equals(uri)?"application/vnd.android.package-archive":null;}
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{
        if(!"r".equals(mode))throw new FileNotFoundException();
        return ParcelFileDescriptor.open(file(uri),ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String sortOrder){
        try{File f=file(uri);String[] cols=projection==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;
            MatrixCursor c=new MatrixCursor(cols);Object[] row=new Object[cols.length];
            for(int i=0;i<cols.length;i++){if(OpenableColumns.DISPLAY_NAME.equals(cols[i]))row[i]="GlyphForge-update.apk";else if(OpenableColumns.SIZE.equals(cols[i]))row[i]=f.length();}
            c.addRow(row);return c;
        }catch(FileNotFoundException e){return null;}
    }
    @Override public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException();}
    @Override public int update(Uri uri,ContentValues values,String s,String[] args){throw new UnsupportedOperationException();}
    @Override public int delete(Uri uri,String s,String[] args){throw new UnsupportedOperationException();}
}
