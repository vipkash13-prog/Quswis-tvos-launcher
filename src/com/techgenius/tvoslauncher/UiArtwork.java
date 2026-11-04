package com.techgenius.tvoslauncher;

import android.content.Context;import android.graphics.Bitmap;import android.os.*;import android.util.LruCache;
import java.util.*;import java.util.concurrent.*;import java.util.function.Consumer;

/** Shared, sampled, bounded dialog art. APK decoding never runs in a focus/list binding callback. */
final class UiArtwork {
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static final ExecutorService WORK=Executors.newFixedThreadPool(2,r->new Thread(()->{android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);r.run();},"ui-art-decode"));
    private static final LruCache<String,Bitmap> CACHE=new LruCache<String,Bitmap>((int)Math.min(24L*1024*1024,Runtime.getRuntime().maxMemory()/8)){
        @Override protected int sizeOf(String k,Bitmap b){return b.getAllocationByteCount();}
    };
    private static final Map<String,List<Consumer<Bitmap>>> PENDING=new HashMap<>();
    static void load(Context ctx,String path,int width,int height,Consumer<Bitmap> done){
        String key=path+"|"+width+"x"+height;Bitmap hit=CACHE.get(key);
        if(hit!=null&&!hit.isRecycled()){if(done!=null)done.accept(hit);return;}
        synchronized(PENDING){List<Consumer<Bitmap>> listeners=PENDING.get(key);if(listeners!=null){if(done!=null)listeners.add(done);return;}listeners=new ArrayList<>();if(done!=null)listeners.add(done);PENDING.put(key,listeners);}
        Context app=ctx.getApplicationContext();WORK.execute(()->{
            Bitmap b=Scenes.loadAssetBitmap(app,path,width,height);
            if(b!=null){float scale=Math.min(1f,Math.min(width/(float)b.getWidth(),height/(float)b.getHeight()));if(scale<.95f){Bitmap scaled=Bitmap.createScaledBitmap(b,Math.max(1,Math.round(b.getWidth()*scale)),Math.max(1,Math.round(b.getHeight()*scale)),true);if(scaled!=b)b.recycle();b=scaled;}CACHE.put(key,b);}
            final Bitmap result=b;MAIN.post(()->{List<Consumer<Bitmap>> waiting;synchronized(PENDING){waiting=PENDING.remove(key);}if(waiting!=null&&result!=null)for(Consumer<Bitmap> callback:waiting)callback.accept(result);});
        });
    }
    static void trim(){CACHE.evictAll();}
    static void prewarm(Context c,int screenW,int screenH){
        load(c,"prayer-reference/header.png",Math.round(screenW*.72f),Math.round(screenW*.24f),null);
        load(c,"prayer-reference/icons.png",Math.round(screenW*.24f),Math.round(screenW*.24f),null);
        load(c,"prayer-reference/hijri.png",Math.round(screenW*.28f),Math.round(screenW*.14f),null);
        load(c,"global-news/globe-header.png",Math.round(screenW*.625f),Math.round(screenW*.21f),null);
        load(c,"match-center/stadium.png",Math.round(screenW*.625f),Math.round(screenW*.21f),null);
        for(String[] item:new String[][]{{"02_main_glass_window_2400x1050.png","2400","1050"},{"04_channel_list_720x690.png","735","690"},{"05_video_frame_1560x690.png","1560","690"},{"06_channel_row_680x100.png","680","100"}})
            load(c,"live-tv/"+item[0],Math.round(Integer.parseInt(item[1])*screenW/3840f),Math.round(Integer.parseInt(item[2])*screenH/2160f),null);
    }
}
