package com.techgenius.tvoslauncher;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/** A separate cache/selection for the new window, using the existing ESPN scoreboard provider. */
final class MatchCenterRepository {
    static final long LIVE_TTL=60000L, OTHER_TTL=300000L;
    private static final String API="https://site.api.espn.com/apis/site/v2/sports/soccer/";
    private static final ExecutorService COORDINATOR=Executors.newSingleThreadExecutor();
    private static final ExecutorService NETWORK=Executors.newFixedThreadPool(3);
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static volatile long retryAfter;
    private static final ConcurrentHashMap<String,Object> GROUP_LOCKS=new ConcurrentHashMap<>();
    private final Context app;
    private final SharedPreferences prefs;
    interface Callback{void loaded(Snapshot snapshot);}
    static final class Snapshot {
        final List<JSONObject> matches=new ArrayList<>();
        final Map<String,String> logos=new HashMap<>();
        long updatedAt;boolean failed,hasCache,stale,loading;List<JSONObject> filtered;
    }
    MatchCenterRepository(Context c){app=c.getApplicationContext();prefs=app.getSharedPreferences("match_center",Context.MODE_PRIVATE);}
    Set<String> selected(){
        if(!prefs.contains("selected")){
            Set<String> imported=FootballScores.selectedLeagues(app),known=new HashSet<>();
            for(String[] l:MatchCenterData.LEAGUES)if(imported.contains(l[0]))known.add(l[0]);
            prefs.edit().putStringSet("selected",known).apply();
        }
        return new HashSet<>(prefs.getStringSet("selected",Collections.emptySet()));
    }
    void select(Set<String> ids){prefs.edit().putStringSet("selected",new HashSet<>(ids)).apply();}
    private JSONObject group(String id){try{return new JSONObject(prefs.getString("feed_"+id,"{}"));}catch(Exception e){return new JSONObject();}}
    Snapshot cached(Set<String> ids,int tab){
        Snapshot snapshot=new Snapshot();Map<String,JSONObject> latest=new LinkedHashMap<>();Map<String,Integer> priorities=new HashMap<>();long now=System.currentTimeMillis(),ttl=tab==MatchCenterData.LIVE?LIVE_TTL:OTHER_TTL;
        for(String id:ids){
            JSONObject slots=group(id);if(slots.length()==0)snapshot.failed=true;Iterator<String> keys=slots.keys();
            while(keys.hasNext()){
                String slotKey=keys.next();if((tab==MatchCenterData.LIVE||tab==MatchCenterData.RESULTS)&&slotKey.startsWith("month:"))continue;JSONObject slot=slots.optJSONObject(slotKey);if(slot==null)continue;
                long at=slot.optLong("at");if(slot.optBoolean("failed"))snapshot.failed=true;
                if(at==0)continue;
                snapshot.hasCache=true;snapshot.updatedAt=snapshot.updatedAt==0?at:Math.min(snapshot.updatedAt,at);
                boolean failed=slot.optBoolean("failed"),stale=failed||now-at>ttl*2;
                snapshot.failed|=failed;snapshot.stale|=stale;
                String logo=slot.optString("logo");if(!logo.isEmpty())snapshot.logos.put(id,logo);
                JSONArray rows=slot.optJSONArray("matches");if(rows==null)continue;
                for(int i=0;i<rows.length();i++){
                    try{JSONObject row=new JSONObject(rows.getJSONObject(i).toString());row.getJSONObject("fixture").put("stale",stale);String eventKey=id+"|"+row.getJSONObject("fixture").optString("id",row.toString());JSONObject previous=latest.get(eventKey);int priority=!slotKey.startsWith("month:")&&!stale?1:0;if(previous==null||priority>priorities.get(eventKey)||priority==priorities.get(eventKey)&&at>previous.getJSONObject("fixture").optLong("updatedAt")){row.getJSONObject("fixture").put("updatedAt",at);latest.put(eventKey,row);priorities.put(eventKey,priority);}}catch(Exception ignored){}
                }
            }
        }
        snapshot.matches.addAll(latest.values());snapshot.filtered=MatchCenterData.select(snapshot.matches,ids,tab,now,tab==MatchCenterData.LEAGUE_TAB?5:Integer.MAX_VALUE);return snapshot;
    }
    Future<?> cachedAsync(Set<String> selection,int tab,Callback callback){Set<String> ids=new HashSet<>(selection);return COORDINATOR.submit(()->{Snapshot saved=cached(ids,tab);saved.loading=true;if(!Thread.currentThread().isInterrupted())MAIN.post(()->callback.loaded(saved));});}
    Future<?> refresh(Set<String> selection,int tab,Callback callback){
        Set<String> ids=new HashSet<>(selection);
        return COORDINATOR.submit(()->{
            android.net.ConnectivityManager connectivity=(android.net.ConnectivityManager)app.getSystemService(Context.CONNECTIVITY_SERVICE);
            if(connectivity!=null&&connectivity.getActiveNetwork()==null){
                Snapshot offline=cached(ids,tab);offline.failed=true;offline.stale=true;MAIN.post(()->callback.loaded(offline));return;
            }
            List<Future<?>> jobs=new ArrayList<>();
            try{
                for(String[] l:MatchCenterData.LEAGUES)if(ids.contains(l[0]))jobs.add(NETWORK.submit(()->fetchGroup(l[0],tab)));
                long deadline=System.currentTimeMillis()+45000L;
                for(Future<?> job:jobs){long remaining=deadline-System.currentTimeMillis();if(remaining<=0)break;try{job.get(remaining,TimeUnit.MILLISECONDS);}catch(ExecutionException|TimeoutException ignored){}}
            }catch(InterruptedException e){Thread.currentThread().interrupt();return;}
            finally{for(Future<?> job:jobs)if(!job.isDone())job.cancel(true);}
            boolean incomplete=false;for(Future<?> job:jobs)if(job.isCancelled())incomplete=true;
            Snapshot snapshot=cached(ids,tab);snapshot.failed|=incomplete;snapshot.stale|=incomplete;if(!Thread.currentThread().isInterrupted())MAIN.post(()->callback.loaded(snapshot));
        });
    }
    private void fetchGroup(String id,int tab){
        synchronized(GROUP_LOCKS.computeIfAbsent(id,k->new Object())){
            if(Thread.currentThread().isInterrupted())return;
            JSONObject slots=group(id);long now=System.currentTimeMillis();String today=MatchCenterData.day(now);
            if(MatchCenterData.NATIONAL.equals(id)){
                loadSlot(slots,id,"schedule",API+"all/teams/655/schedule",tab);
                if(tab==MatchCenterData.UPCOMING||tab==MatchCenterData.LEAGUE_TAB)loadSlot(slots,id,"fixtures",API+"all/teams/655/schedule?fixture=true",tab);
            }else{
                Calendar beforeDay=Calendar.getInstance(MatchCenterData.RIYADH);beforeDay.add(Calendar.DAY_OF_MONTH,-1);String before=MatchCenterData.day(beforeDay.getTimeInMillis());
                loadSlot(slots,id,today,API+id+"/scoreboard?dates="+today+"&limit=1000",tab);
                loadSlot(slots,id,before,API+id+"/scoreboard?dates="+before+"&limit=1000",tab);
                // ESPN supports YYYYMM for soccer. Some leagues return calendar objects,
                // not date strings; month queries handle both calendar schemas without guesses.
                String month=MatchCenterData.month(now);Calendar next=Calendar.getInstance(MatchCenterData.RIYADH);next.set(Calendar.DAY_OF_MONTH,1);next.add(Calendar.MONTH,1);String nextMonth=MatchCenterData.month(next.getTimeInMillis());
                if(tab==MatchCenterData.UPCOMING||tab==MatchCenterData.LEAGUE_TAB){
                    loadSlot(slots,id,"month:"+month,API+id+"/scoreboard?dates="+month+"&limit=1000",MatchCenterData.UPCOMING);
                    loadSlot(slots,id,"month:"+nextMonth,API+id+"/scoreboard?dates="+nextMonth+"&limit=1000",MatchCenterData.UPCOMING);
                }
                List<String> remove=new ArrayList<>();Iterator<String> keys=slots.keys();while(keys.hasNext()){String key=keys.next();if(!key.equals(today)&&!key.equals(before)&&!key.equals("month:"+month)&&!key.equals("month:"+nextMonth))remove.add(key);}for(String key:remove)slots.remove(key);
            }
            if(!Thread.currentThread().isInterrupted())prefs.edit().putString("feed_"+id,slots.toString()).apply();
        }
    }
    private void loadSlot(JSONObject slots,String id,String key,String endpoint,int tab){
        if(Thread.currentThread().isInterrupted())return;
        long now=System.currentTimeMillis(),ttl=tab==MatchCenterData.LIVE?LIVE_TTL:OTHER_TTL;
        JSONObject old=slots.optJSONObject(key);if(old!=null&&now-old.optLong("at")<ttl&&!old.optBoolean("failed"))return;
        try{
            if(now<retryAfter)throw new IOException("Provider backoff");
            JSONObject root=json(endpoint);List<JSONObject> rows=MatchCenterData.parse(root,id);
            JSONArray array=new JSONArray();for(JSONObject row:rows)array.put(row);
            JSONObject slot=new JSONObject().put("at",System.currentTimeMillis()).put("matches",array).put("failed",false);
            JSONArray leagues=root.optJSONArray("leagues");JSONObject league=leagues==null?null:leagues.optJSONObject(0);
            if(league!=null){slot.put("calendar",league.optJSONArray("calendar"));JSONArray logos=league.optJSONArray("logos");if(logos!=null&&logos.optJSONObject(0)!=null)slot.put("logo",logos.optJSONObject(0).optString("href"));}
            if(MatchCenterData.NATIONAL.equals(id))slot.put("logo",root.optJSONObject("team").optString("logo"));
            slots.put(key,slot);if(!Thread.currentThread().isInterrupted())prefs.edit().putString("feed_"+id,slots.toString()).apply();
        }catch(Exception error){
            try{if(old==null)old=new JSONObject();old.put("failed",true).put("attempt",now);slots.put(key,old);}catch(Exception ignored){}
            android.util.Log.w("Quswis-Matches","ESPN refresh failed for "+id+"/"+key,error);
        }
    }
    private static JSONObject json(String endpoint)throws Exception{
        HttpURLConnection connection=(HttpURLConnection)new URL(endpoint).openConnection();
        try{
            connection.setConnectTimeout(4500);connection.setReadTimeout(5000);
            connection.setRequestProperty("User-Agent",Weather.UA);connection.setRequestProperty("Accept","application/json");
            int code=connection.getResponseCode();
            if(code==429||code==503){long delay=120;try{delay=Math.max(60,Math.min(900,Long.parseLong(connection.getHeaderField("Retry-After"))));}catch(Exception ignored){}retryAfter=System.currentTimeMillis()+delay*1000L;}
            if(code!=200)throw new IOException("HTTP "+code);
            StringBuilder body=new StringBuilder();try(BufferedReader r=new BufferedReader(new InputStreamReader(connection.getInputStream(),"UTF-8"))){char[] buffer=new char[4096];int n;while((n=r.read(buffer))!=-1){body.append(buffer,0,n);if(body.length()>4000000)throw new IOException("Response too large");}}
            return new JSONObject(body.toString());
        }finally{connection.disconnect();}
    }
}
