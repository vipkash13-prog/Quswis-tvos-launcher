package com.techgenius.tvoslauncher;

import org.json.*;
import java.text.SimpleDateFormat;
import java.util.*;

/** Date-bound prayer instants; no device-timezone or next-day timing guesses. */
final class PrayerPlan {
 static final String[] KEYS={"Fajr","Dhuhr","Asr","Maghrib","Isha"};
 static final class Event {
  final String day,key;final int index,request;final boolean iqama;final long at;
  Event(String day,int index,boolean iqama,long at){this.day=day;this.index=index;this.iqama=iqama;this.at=at;
   key=day+"|"+KEYS[index]+(iqama?"|iqama":"|adhan");request=Integer.parseInt(day.replace("-",""))*10+index*2+(iqama?1:0);}
 }
 private static final Set<String> ZONES=new HashSet<>(Arrays.asList(TimeZone.getAvailableIDs()));
 static String day(long at,String zone){SimpleDateFormat f=new SimpleDateFormat("yyyy-MM-dd",Locale.US);f.setTimeZone(TimeZone.getTimeZone(zone));return f.format(new Date(at));}
 static void merge(JSONObject response,JSONObject days)throws Exception{
  if(response.optInt("code")!=200)throw new IllegalArgumentException("Calendar response failed");
  JSONArray data=response.optJSONArray("data");if(data==null||data.length()==0)throw new IllegalArgumentException("Calendar is missing");
  for(int i=0;i<data.length();i++){
   JSONObject d=data.getJSONObject(i);String raw=d.getJSONObject("date").getJSONObject("gregorian").getString("date");
   if(!raw.matches("\\d{2}-\\d{2}-\\d{4}"))continue;
   String zone=d.getJSONObject("meta").getString("timezone");if(!ZONES.contains(zone))throw new IllegalArgumentException("Unknown city timezone");
   String date=raw.substring(6)+"-"+raw.substring(3,5)+"-"+raw.substring(0,2);
   JSONObject times=d.getJSONObject("timings");for(String key:KEYS)if(instant(date,times.optString(key),zone)<=0)throw new IllegalArgumentException("Invalid prayer time");
   days.put(date,new JSONObject().put("timings",times).put("timezone",zone).put("hijri",d.getJSONObject("date").optJSONObject("hijri")));
  }
 }
 static long instant(String day,String raw,String zone){
  if(raw==null||!raw.matches("(?:[01][0-9]|2[0-3]):[0-5][0-9](?: .*)?")||!ZONES.contains(zone))return -1;
  try{SimpleDateFormat f=new SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.US);f.setLenient(false);f.setTimeZone(TimeZone.getTimeZone(zone));return f.parse(day+" "+raw.substring(0,5)).getTime();}catch(Exception e){return -1;}
 }
 static List<Event> events(JSONObject days,long now,boolean adhan,boolean iqama,int[] delays){
  List<Event> result=new ArrayList<>();Iterator<String> keys=days.keys();
  while(keys.hasNext()){
   String day=keys.next();JSONObject d=days.optJSONObject(day);if(d==null)continue;
   String zone=d.optString("timezone","");if(!ZONES.contains(zone))continue;
   Calendar end=Calendar.getInstance(TimeZone.getTimeZone(zone));end.setTimeInMillis(now);end.add(Calendar.DAY_OF_MONTH,7);
   if(day.compareTo(day(now,zone))<0||day.compareTo(day(end.getTimeInMillis(),zone))>0)continue;
   JSONObject times=d.optJSONObject("timings");if(times==null)continue;
   for(int i=0;i<5;i++){
    long at=instant(day,times.optString(KEYS[i]),zone);if(at<=0)continue;
    if(adhan&&at>=now)result.add(new Event(day,i,false,at));
    int delay=delays[i];long iqamaAt=at+delay*60000L;
    if(iqama&&delay>0&&delay<=180&&iqamaAt>=now)result.add(new Event(day,i,true,iqamaAt));
   }
  }
  result.sort(Comparator.comparingLong(e->e.at));return result;
 }
 static boolean deliver(String expected,String actual,long now,long scheduled,boolean handled){
  return expected.equals(actual)&&!handled&&scheduled>0&&now>=scheduled-1000L&&now-scheduled<=120000L;
 }
}
