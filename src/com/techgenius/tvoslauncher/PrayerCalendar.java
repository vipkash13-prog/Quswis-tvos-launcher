package com.techgenius.tvoslauncher;

import android.content.*;
import org.json.*;
import java.net.*;
import java.io.*;
import java.util.*;

/** Current and next month's dated timings survive process death and offline nights. */
final class PrayerCalendar {
 static String location(Context c){android.content.SharedPreferences p=PrayerTimes.prefs(c);return PrayerTimes.city(c)+"|"+PrayerTimes.countryApi(c)+"|"+p.getString("latitude","")+"|"+p.getString("longitude","");}
 static JSONObject days(Context c){
  android.content.SharedPreferences p=PrayerTimes.prefs(c);
  if(!location(c).equals(p.getString("calendar_location","")))return new JSONObject();
  try{return new JSONObject(p.getString("calendar_days","{}"));}catch(Exception e){return new JSONObject();}
 }
 static String zone(Context c){JSONObject d=days(c);Iterator<String> it=d.keys();if(it.hasNext()){JSONObject first=d.optJSONObject(it.next());if(first!=null)return first.optString("timezone",TimeZone.getDefault().getID());}return TimeZone.getDefault().getID();}
 static void applyToday(Context c){
  JSONObject days=days(c);Iterator<String> dates=days.keys();
  while(dates.hasNext()){
   String date=dates.next();JSONObject d=days.optJSONObject(date);if(d==null)continue;String zone=d.optString("timezone","");
   if(!date.equals(PrayerPlan.day(System.currentTimeMillis(),zone)))continue;
   JSONObject meta=new JSONObject();try{
    meta.put("day",date).put("timezone",zone);JSONObject hijri=d.optJSONObject("hijri");if(hijri!=null)meta.put("hijri",hijri.optString("date"));
    PrayerTimes.prefs(c).edit().putString("data",d.getJSONObject("timings").toString()).putString("cache_key",location(c))
     .putString("cache_date",date).putString("detail_metadata",meta.toString()).putLong("at",System.currentTimeMillis()).apply();
   }catch(Exception ignored){}return;
  }
 }
 static synchronized boolean sync(Context c,boolean force){
  android.content.SharedPreferences p=PrayerTimes.prefs(c);String key=location(c);long now=System.currentTimeMillis();
  JSONObject all=days(c);
  String zone=zone(c);String today=PrayerPlan.day(now,zone);
  if(!force&&now-p.getLong("calendar_at",0)<6*3600000L&&all.length()>28&&all.has(today)){applyToday(c);return true;}
  Calendar month=Calendar.getInstance(TimeZone.getTimeZone(zone));boolean ok=true;int loaded=0;boolean corrected=false;
  for(int offset=0;offset<2;offset++){
   if(Thread.currentThread().isInterrupted())return false;
   HttpURLConnection conn=null;
   try{
    String lat=p.getString("latitude",""),lon=p.getString("longitude","");String endpoint;
    if(!lat.isEmpty()&&!lon.isEmpty())endpoint="calendar/"+month.get(Calendar.YEAR)+"/"+(month.get(Calendar.MONTH)+1)+"?latitude="+URLEncoder.encode(lat,"UTF-8")+"&longitude="+URLEncoder.encode(lon,"UTF-8");
    else endpoint="calendarByCity/"+month.get(Calendar.YEAR)+"/"+(month.get(Calendar.MONTH)+1)+"?city="+URLEncoder.encode(PrayerTimes.cityApi(c),"UTF-8")+"&country="+URLEncoder.encode(PrayerTimes.countryApi(c),"UTF-8");
    conn=(HttpURLConnection)new URL("https://api.aladhan.com/v1/"+endpoint+"&method=4").openConnection();conn.setConnectTimeout(10000);conn.setReadTimeout(12000);conn.setRequestProperty("User-Agent",Weather.UA);
    if(conn.getResponseCode()!=200)throw new IOException("HTTP "+conn.getResponseCode());
    StringBuilder b=new StringBuilder();try(BufferedReader r=new BufferedReader(new InputStreamReader(conn.getInputStream(),"UTF-8"))){String line;while((line=r.readLine())!=null){b.append(line);if(b.length()>2000000)throw new IOException("Calendar too large");}}
    PrayerPlan.merge(new JSONObject(b.toString()),all);loaded++;
    // On first setup the city's month can differ from the device's month near midnight.
    Iterator<String> found=all.keys();if(found.hasNext()){
     String actualZone=all.getJSONObject(found.next()).getString("timezone");Calendar actual=Calendar.getInstance(TimeZone.getTimeZone(actualZone));
     if(offset==0&&!corrected&&(month.get(Calendar.YEAR)!=actual.get(Calendar.YEAR)||month.get(Calendar.MONTH)!=actual.get(Calendar.MONTH))){month=actual;corrected=true;offset=-1;continue;}
    }
   }catch(Exception e){ok=false;android.util.Log.w("Quswis-Prayer","Calendar refresh failed",e);}finally{if(conn!=null)conn.disconnect();}
   month.add(Calendar.MONTH,1);
  }
  if(!key.equals(location(c)))return false;
  // Retain current/future dated entries only, avoiding an ever-growing preference file.
  JSONObject kept=new JSONObject();Iterator<String> it=all.keys();String yesterday=PrayerPlan.day(now-86400000L,"UTC");
  try{while(it.hasNext()){String date=it.next();if(date.compareTo(yesterday)>=0)kept.put(date,all.get(date));}}catch(Exception ignored){}
  if(loaded>0)p.edit().putString("calendar_location",key).putString("calendar_days",kept.toString()).putLong("calendar_at",ok?now:0).putString("calendar_status",ok?"تم تحميل تقويم الشهر الحالي والقادم":"تم تحميل جزء من التقويم؛ ستعاد المحاولة").commit();
  else p.edit().putString("calendar_status","تعذر تحديث التقويم؛ تحقق من الاتصال").apply();
  applyToday(c);return ok;
 }
}
