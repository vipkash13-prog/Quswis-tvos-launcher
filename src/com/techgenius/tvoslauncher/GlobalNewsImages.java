package com.techgenius.tvoslauncher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;
import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.lang.ref.WeakReference;

/** Actual feed image URLs only. Separate bounded photo caches; late responses cannot swap stories. */
final class GlobalNewsImages {
    private static final long MAX_DISK=64*1024*1024L, MAX_AGE=7*86400000L;
    private static final ExecutorService WORK=Executors.newFixedThreadPool(2);
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static final LruCache<String,Bitmap> MEMORY=new LruCache<String,Bitmap>(16*1024*1024){
        @Override protected int sizeOf(String key,Bitmap value){return value.getByteCount();}
    };
    private static final Map<String,List<WeakReference<ImageView>>> PENDING=new HashMap<>();
    static void load(Context context,ImageView view,String raw,int side){
        String url=GlobalNewsData.webUrl(raw);String key=url+"|"+side;
        if(key.equals(view.getTag())&&view.getDrawable()!=null)return;
        view.setTag(key);view.setImageDrawable(null);
        if(url.isEmpty())return;
        Bitmap hit=MEMORY.get(key);if(hit!=null){view.setImageBitmap(hit);return;}
        synchronized(PENDING){
            List<WeakReference<ImageView>> waiting=PENDING.get(key);
            if(waiting!=null){waiting.add(new WeakReference<>(view));return;}
            waiting=new ArrayList<>();waiting.add(new WeakReference<>(view));PENDING.put(key,waiting);
        }
        Context app=context.getApplicationContext();WORK.execute(()->{
            Bitmap image=null;
            try{
                File directory=new File(app.getCacheDir(),"global-news-images");directory.mkdirs();
                File cached=new File(directory,hash(key)+".png");
                if(cached.isFile())image=BitmapFactory.decodeFile(cached.getAbsolutePath());
                if(image==null||System.currentTimeMillis()-cached.lastModified()>MAX_AGE){
                    Bitmap received=download(url,side);
                    if(received!=null){image=received;File tmp=new File(directory,cached.getName()+".tmp");
                        try(FileOutputStream out=new FileOutputStream(tmp)){image.compress(Bitmap.CompressFormat.PNG,100,out);}
                        if(!tmp.renameTo(cached))tmp.delete();trim(directory);
                    }
                }
                if(image!=null)MEMORY.put(key,image);
            }catch(Exception ignored){}
            final Bitmap result=image;
            MAIN.post(()->{
                List<WeakReference<ImageView>> waiting;synchronized(PENDING){waiting=PENDING.remove(key);}
                if(waiting!=null&&result!=null)for(WeakReference<ImageView> ref:waiting){ImageView target=ref.get();if(target!=null&&target.isAttachedToWindow()&&key.equals(target.getTag()))target.setImageBitmap(result);}
            });
        });
    }
    private static String hash(String key)throws Exception{
        byte[] digest=MessageDigest.getInstance("SHA-256").digest(key.getBytes("UTF-8"));StringBuilder result=new StringBuilder();for(byte b:digest)result.append(String.format(Locale.US,"%02x",b&255));return result.toString();
    }
    private static Bitmap download(String url,int side)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
        try{
            c.setConnectTimeout(4000);c.setReadTimeout(4500);c.setRequestProperty("User-Agent",Weather.UA);
            if(c.getResponseCode()!=200)return null;
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(InputStream in=c.getInputStream()){byte[] buffer=new byte[4096];int n;while((n=in.read(buffer))!=-1){bytes.write(buffer,0,n);if(bytes.size()>5*1024*1024)return null;}}
            byte[] data=bytes.toByteArray();BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(data,0,data.length,bounds);
            if(bounds.outWidth<=0||bounds.outHeight<=0||bounds.outWidth>8192||bounds.outHeight>8192)return null;
            BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=1;while(Math.max(bounds.outWidth,bounds.outHeight)/options.inSampleSize>side)options.inSampleSize*=2;
            return BitmapFactory.decodeByteArray(data,0,data.length,options);
        }finally{c.disconnect();}
    }
    private static void trim(File directory){
        File[] files=directory.listFiles();if(files==null)return;Arrays.sort(files,Comparator.comparingLong(File::lastModified));long size=0;for(File f:files)size+=f.length();for(File f:files){if(size<=MAX_DISK)break;long length=f.length();if(f.delete())size-=length;}
    }
}
