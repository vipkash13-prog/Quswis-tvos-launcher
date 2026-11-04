package com.techgenius.tvoslauncher;

import android.content.*;import android.os.*;import android.util.JsonReader;
import org.json.*;import java.io.*;import java.net.*;import java.util.*;import java.util.concurrent.*;

/** Daily, atomic on-disk source snapshots. A bad response never destroys a successful catalogue. */
final class LiveTvRepository {
 static final ExecutorService WORK=Executors.newFixedThreadPool(2);private static final Handler MAIN=new Handler(Looper.getMainLooper());
 private static final ConcurrentHashMap<String,Object> FILE_LOCKS=new ConcurrentHashMap<>();
 private static final Object META_LOCK=new Object();private static Map<String,LiveTvData.Meta> metadata;private static Map<String,String> languages;
 private final Context app;private final File directory;final SharedPreferences prefs;
 static final class Snapshot {final List<LiveTvData.Channel> channels=new ArrayList<>(),candidates=new ArrayList<>();long updated;boolean cached,failed;String epg="",message="";int section;}
 interface Callback{void loaded(Snapshot result);}
 LiveTvRepository(Context c){app=c.getApplicationContext();directory=new File(app.getFilesDir(),"live-tv-center");directory.mkdirs();prefs=app.getSharedPreferences("live_tv_center",0);}
 Future<?> load(int section,boolean force,Callback callback){return WORK.submit(()->{Snapshot s=read(section,force);MAIN.post(()->callback.loaded(s));});}
 Snapshot read(int section,boolean force){Snapshot s=new Snapshot();s.section=section;android.net.ConnectivityManager network=(android.net.ConnectivityManager)app.getSystemService(Context.CONNECTIVITY_SERVICE);if(network!=null&&network.getActiveNetwork()==null){s.cached=true;s.failed=true;}try{
  ensureMetadata(force);int[] sources=section==6?new int[]{5,6}:new int[]{section};Map<String,LiveTvData.Channel> unique=new LinkedHashMap<>();
  for(int source:sources){File f=new File(directory,"list-"+source+".m3u");boolean fresh=fetch("https://iptv-org.github.io/iptv/"+LiveTvData.LISTS[source]+".m3u",f,12*1024*1024,force);s.failed|=!fresh;s.cached|=!fresh||System.currentTimeMillis()-f.lastModified()>LiveTvData.DAY;if(!f.exists())continue;
   s.updated=s.updated==0?f.lastModified():Math.min(s.updated,f.lastModified());LiveTvData.Playlist p=LiveTvData.parse(readText(f,12*1024*1024));if(!p.epg.isEmpty())s.epg=p.epg;
   for(LiveTvData.Channel c:LiveTvData.enrich(p.channels,metadata,languages,section))if(!unique.containsKey(c.key()))unique.put(c.key(),c);
  }
  for(LiveTvData.Channel c:unique.values())if(section==6){if(c.arabicDeclared()&&c.animation())s.candidates.add(c);if(c.animation()&&arabicConfirmed(c))s.channels.add(c);}else s.channels.add(c);
  if(s.channels.isEmpty())s.message=section==6?"لا توجد قنوات كرتون عربية مؤكدة قابلة للتشغيل حاليًا. لغة الاسم لا تثبت الصوت. يمكنك فحص القنوات المرشحة من إعدادات المركز.":s.failed?"تعذّر تحميل القنوات. تحقق من الاتصال أو حاول التحديث.":"لا توجد قنوات متاحة في هذا القسم حاليًا";
  if(metadata.isEmpty()){s.channels.clear();s.failed=true;}
  if(metadata.isEmpty())s.message="تعذّر التحقق من بيانات القنوات؛ أُخفيت القنوات إلى حين توفر البيانات.";
 }catch(Exception e){s.failed=true;s.cached=true;s.message="تعذّر تحديث القنوات. آخر قائمة ناجحة تبقى محفوظة.";android.util.Log.w("Quswis-Live","Catalogue",e);}return s;}
 private void ensureMetadata(boolean force)throws Exception{synchronized(META_LOCK){File channels=new File(directory,"channels.json"),feeds=new File(directory,"feeds.json");boolean changed=fetch("https://iptv-org.github.io/api/channels.json",channels,16*1024*1024,force);changed|=fetch("https://iptv-org.github.io/api/feeds.json",feeds,24*1024*1024,force);
   if(metadata!=null&&languages!=null&&(!force||!changed))return;Map<String,LiveTvData.Meta> m=new HashMap<>();Map<String,String> l=new HashMap<>();
   if(channels.exists())try(JsonReader r=new JsonReader(new InputStreamReader(new FileInputStream(channels),"UTF-8"))){r.beginArray();while(r.hasNext()){LiveTvData.Meta c=new LiveTvData.Meta();r.beginObject();while(r.hasNext()){String k=r.nextName();if(r.peek()==android.util.JsonToken.NULL){r.nextNull();continue;}switch(k){case"id":c.id=r.nextString();break;case"name":c.name=r.nextString();break;case"country":c.country=r.nextString();break;case"website":c.website=LiveTvData.web(r.nextString());break;case"network":c.network=r.nextString();break;case"is_nsfw":c.blocked|=r.nextBoolean();break;case"closed":c.blocked=true;r.skipValue();break;case"categories":c.categories=strings(r);break;case"alt_names":String names=strings(r);for(String n:names.split(";"))if(n.matches(".*[\\u0600-\\u06FF].*")){c.arabic=n;break;}break;default:r.skipValue();}}r.endObject();m.put(c.id,c);}r.endArray();}
   if(feeds.exists())try(JsonReader r=new JsonReader(new InputStreamReader(new FileInputStream(feeds),"UTF-8"))){r.beginArray();while(r.hasNext()){String id="",feed="",lang="";boolean main=false;r.beginObject();while(r.hasNext()){String k=r.nextName();if(r.peek()==android.util.JsonToken.NULL){r.nextNull();continue;}switch(k){case"channel":id=r.nextString();break;case"id":feed=r.nextString();break;case"languages":lang=strings(r);break;case"is_main":main=r.nextBoolean();break;default:r.skipValue();}}r.endObject();l.put(id+"@"+feed,lang);if(main)l.put(id,lang);}r.endArray();}
   metadata=m;languages=l;
  }}
 private static String strings(JsonReader r)throws IOException{StringBuilder s=new StringBuilder();r.beginArray();while(r.hasNext()){if(s.length()>0)s.append(';');s.append(r.nextString());}r.endArray();return s.toString();}
 private boolean fetch(String url,File file,int limit,boolean force){synchronized(FILE_LOCKS.computeIfAbsent(file.getAbsolutePath(),key->new Object())){return fetchLocked(url,file,limit,force);}}
 private boolean fetchLocked(String url,File file,int limit,boolean force){long age=System.currentTimeMillis()-file.lastModified();if(file.exists()&&age<(force?60000:LiveTvData.DAY))return true;File temp=new File(file.getPath()+".tmp");HttpURLConnection c=null;try{
   if(Thread.currentThread().isInterrupted())return false;c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(6000);c.setReadTimeout(9000);c.setRequestProperty("User-Agent","Quswis-LiveTV/1.0");if(c.getResponseCode()!=200)throw new IOException("HTTP "+c.getResponseCode());int total=0;try(InputStream in=c.getInputStream();FileOutputStream out=new FileOutputStream(temp)){byte[] b=new byte[16384];int n;while((n=in.read(b))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();total+=n;if(total>limit)throw new IOException("Source exceeds limit");out.write(b,0,n);}out.getFD().sync();}
   // Validate before replacing the last successful source.
   if(url.endsWith(".m3u"))LiveTvData.parse(readText(temp,limit));else try(JsonReader r=new JsonReader(new FileReader(temp))){r.beginArray();while(r.hasNext())r.skipValue();r.endArray();}
   if(!temp.renameTo(file))throw new IOException("Atomic source replace");return true;
  }catch(Exception e){temp.delete();return false;}finally{if(c!=null)c.disconnect();}}
 static String readText(File f,int limit)throws IOException{if(f.length()>limit)throw new IOException("File too large");try(InputStream in=new FileInputStream(f);ByteArrayOutputStream b=new ByteArrayOutputStream()){byte[] a=new byte[8192];int n;while((n=in.read(a))!=-1){b.write(a,0,n);if(b.size()>limit)throw new IOException("Source too large");}return b.toString("UTF-8");}}
 Set<String> favorites(){return new HashSet<>(prefs.getStringSet("favorites",Collections.emptySet()));}
 boolean favorite(LiveTvData.Channel c){return favorites().contains(c.key());}
 void toggle(LiveTvData.Channel c){Set<String> ids=favorites();boolean add=!ids.remove(c.key());SharedPreferences.Editor e=prefs.edit();if(add){ids.add(c.key());try{e.putString("favorite_"+c.key(),c.json().toString());}catch(Exception ignored){}}else e.remove("favorite_"+c.key());e.putStringSet("favorites",ids).apply();}
 List<LiveTvData.Channel> savedFavorites(){List<LiveTvData.Channel> out=new ArrayList<>();for(String id:favorites())try{out.add(LiveTvData.Channel.from(new JSONObject(prefs.getString("favorite_"+id,"{}"))));}catch(Exception ignored){}return out;}
 void remember(LiveTvData.Channel c,int section){try{prefs.edit().putString("last_channel",c.json().toString()).putInt("last_section",section).apply();}catch(Exception ignored){}}
 LiveTvData.Channel last(){try{String s=prefs.getString("last_channel","");return s.isEmpty()?null:LiveTvData.Channel.from(new JSONObject(s));}catch(Exception e){return null;}}
 void success(LiveTvData.Endpoint e){prefs.edit().putLong("success_"+e.url,System.currentTimeMillis()).remove("failure_"+e.url).apply();}
 void failure(LiveTvData.Endpoint e){prefs.edit().putLong("failure_"+e.url,System.currentTimeMillis()).apply();}
 void confirmArabic(LiveTvData.Channel c,LiveTvData.Endpoint e,String evidence){if(!c.animation())return;prefs.edit().putString("arabic_"+e.url,evidence).putLong("arabic_at_"+e.url,System.currentTimeMillis()).apply();}
 boolean arabicConfirmed(LiveTvData.Channel c){for(LiveTvData.Endpoint e:c.streams)if(prefs.getLong("success_"+e.url,0)>prefs.getLong("failure_"+e.url,0)&&prefs.getLong("success_"+e.url,0)>System.currentTimeMillis()-LiveTvData.DAY&&!prefs.getString("arabic_"+e.url,"").isEmpty())return true;return false;}
 String state(LiveTvData.Channel c){for(LiveTvData.Endpoint e:c.streams){long ok=prefs.getLong("success_"+e.url,0),bad=prefs.getLong("failure_"+e.url,0);if(ok>bad&&ok>System.currentTimeMillis()-LiveTvData.DAY)return "نجح التشغيل سابقًا • "+LiveTvData.time(ok);}for(LiveTvData.Endpoint e:c.streams)if(prefs.getLong("failure_"+e.url,0)>0)return "تعذّر التشغيل في آخر محاولة";return "لم يُختبر التشغيل بعد";}
 int section(){return Math.max(0,Math.min(6,prefs.getInt("section",0)));}void section(int n){prefs.edit().putInt("section",n).apply();}
}
