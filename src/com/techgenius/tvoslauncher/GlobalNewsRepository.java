package com.techgenius.tvoslauncher;

import android.content.*;
import android.os.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/** The existing Arabic newsrooms, plus verified sports/economy feeds from the same newsroom. */
final class GlobalNewsRepository {
    private static final String[][] FEEDS={
        {"bbc","BBC عربي","https://feeds.bbci.co.uk/arabic/rss.xml","0"},
        {"france","فرانس 24","https://www.france24.com/ar/rss","0"},
        {"jazeera","الجزيرة","https://www.aljazeera.net/aljazeerarss/a7c186be-1baa-4bd4-9d80-a84db769f779/73d0e1b4-532f-45ef-b7e0-8d5e5ab4a3a0","0"},
        {"sky","سكاي نيوز عربية","https://www.skynewsarabia.com/web/rss","0"},
        {"france-economy","فرانس 24","https://www.france24.com/ar/اقتصاد/rss","3"},
        {"france-sport","فرانس 24","https://www.france24.com/ar/رياضة/rss","4"}};
    static final long TTL=120000L;
    private static final ExecutorService COORDINATOR=Executors.newSingleThreadExecutor(),WORK=Executors.newFixedThreadPool(3);
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static volatile long backoff;
    private final Context app;private final SharedPreferences prefs;
    static final class Snapshot{final List<GlobalNewsData.Story> stories=new ArrayList<>();boolean cached,failed;long updated;int sources;}
    interface Callback{void loaded(Snapshot snapshot);}
    GlobalNewsRepository(Context c){app=c.getApplicationContext();prefs=app.getSharedPreferences("global_news_center",Context.MODE_PRIVATE);}
    int section(){return Math.max(0,Math.min(4,prefs.getInt("section",0)));}
    void section(int value){prefs.edit().putInt("section",value).apply();}
    private JSONObject slot(String id){try{return new JSONObject(prefs.getString("feed_"+id,"{}"));}catch(Exception e){return new JSONObject();}}
    Snapshot cached(){Snapshot result=new Snapshot();long now=System.currentTimeMillis();
        for(String[] feed:FEEDS){JSONObject slot=slot(feed[0]);long at=slot.optLong("at");result.failed|=slot.optBoolean("failed");if(at==0)continue;result.sources++;result.updated=result.updated==0?at:Math.min(result.updated,at);boolean stale=slot.optBoolean("failed")||now-at>TTL*2;result.cached|=stale;
            JSONArray array=slot.optJSONArray("stories");if(array!=null)for(int i=0;i<array.length();i++){JSONObject o=array.optJSONObject(i);if(o==null)continue;GlobalNewsData.Story s=GlobalNewsData.Story.from(o);s.cached=stale;result.stories.add(s);}}
        List<GlobalNewsData.Story> merged=GlobalNewsData.merge(result.stories);result.stories.clear();result.stories.addAll(merged);return result;}
    Future<?> refresh(boolean force,Callback callback){return COORDINATOR.submit(()->{
        android.net.ConnectivityManager network=(android.net.ConnectivityManager)app.getSystemService(Context.CONNECTIVITY_SERVICE);
        if(network!=null&&network.getActiveNetwork()==null){Snapshot offline=cached();offline.cached=true;offline.failed=true;for(GlobalNewsData.Story s:offline.stories)s.cached=true;MAIN.post(()->callback.loaded(offline));return;}
        List<Future<?>> jobs=new ArrayList<>();boolean incomplete=false;
        try{for(String[] feed:FEEDS)jobs.add(WORK.submit(()->read(feed,force)));long deadline=System.currentTimeMillis()+25000;
            for(Future<?> job:jobs){long remaining=deadline-System.currentTimeMillis();if(remaining<=0){incomplete=true;break;}try{job.get(remaining,TimeUnit.MILLISECONDS);}catch(ExecutionException|TimeoutException error){incomplete=true;}}}
        catch(InterruptedException error){Thread.currentThread().interrupt();return;}
        finally{for(Future<?> job:jobs)if(!job.isDone()){incomplete=true;job.cancel(true);}}
        Snapshot snapshot=cached();snapshot.failed|=incomplete;snapshot.cached|=incomplete;if(!Thread.currentThread().isInterrupted())MAIN.post(()->callback.loaded(snapshot));
    });}
    private void read(String[] feed,boolean force){
        JSONObject old=slot(feed[0]);long now=System.currentTimeMillis(),age=now-old.optLong("at");
        if(age<(force?15000:TTL)&&old.has("at"))return;
        HttpURLConnection c=null;
        try{
            if(now<backoff)throw new IOException("Provider retry backoff");if(Thread.currentThread().isInterrupted())return;
            c=(HttpURLConnection)new URL(new URI(feed[2]).toASCIIString()).openConnection();c.setConnectTimeout(4500);c.setReadTimeout(6000);c.setUseCaches(false);
            c.setRequestProperty("User-Agent","Mozilla/5.0");c.setRequestProperty("Accept","application/rss+xml, application/atom+xml, application/xml, text/xml");c.setRequestProperty("Cache-Control","no-cache");
            int code=c.getResponseCode();if(code==429||code==503){long seconds=120;try{seconds=Math.max(60,Long.parseLong(c.getHeaderField("Retry-After")));}catch(Exception ignored){}backoff=System.currentTimeMillis()+seconds*1000L;}if(code!=200)throw new IOException("HTTP "+code);
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(InputStream in=c.getInputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){bytes.write(b,0,n);if(bytes.size()>1800000)throw new IOException("RSS too large");}}
            List<GlobalNewsData.Story> parsed=GlobalNewsData.parse(bytes.toString("UTF-8"),feed[1],Integer.parseInt(feed[3]));JSONArray stories=new JSONArray();
            // Archive entries cannot be presented as freshly published stories by a live feed.
            for(GlobalNewsData.Story s:parsed)if(s.published==0||s.published>=now-14*86400000L)stories.put(s.json());
            JSONObject saved=new JSONObject().put("at",System.currentTimeMillis()).put("failed",false).put("stories",stories);
            if(!Thread.currentThread().isInterrupted())prefs.edit().putString("feed_"+feed[0],saved.toString()).apply();
        }catch(Exception error){try{old.put("failed",true);if(!Thread.currentThread().isInterrupted())prefs.edit().putString("feed_"+feed[0],old.toString()).apply();}catch(Exception ignored){}android.util.Log.w("Quswis-News","RSS unavailable: "+feed[0],error);}
        finally{if(c!=null)c.disconnect();}
    }
}
