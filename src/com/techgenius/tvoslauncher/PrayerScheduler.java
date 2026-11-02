package com.techgenius.tvoslauncher;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import org.json.*;
import java.util.*;

/** Persisted, dated exact prayer alarms; a tomorrow Fajr is booked before midnight. */
public final class PrayerScheduler extends BroadcastReceiver {
 static final String[] KEYS=PrayerPlan.KEYS,NAMES={"الفجر","الظهر","العصر","المغرب","العشاء"};
 static final String ACTION="com.techgenius.tvoslauncher.PRAYER_ALARM",REFRESH="com.techgenius.tvoslauncher.PRAYER_REFRESH";
 private static PowerManager.WakeLock handoff;
 static boolean exact(Context c){AlarmManager a=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);return a!=null&&(Build.VERSION.SDK_INT<31||a.canScheduleExactAlarms());}
 static synchronized void releaseHandoff(){if(handoff!=null){if(handoff.isHeld())handoff.release();handoff=null;}}
 private static synchronized void hold(Context c){releaseHandoff();PowerManager p=(PowerManager)c.getSystemService(Context.POWER_SERVICE);handoff=p.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"Quswis:adhan-handoff");handoff.setReferenceCounted(false);handoff.acquire(30000L);}
 private static PendingIntent operation(Context c,PrayerPlan.Event event,String location){
  Intent i=new Intent(c,PrayerScheduler.class).setAction(ACTION).setData(Uri.parse("quswis://prayer/"+event.day+"/"+event.index+"/"+event.iqama));
  i.putExtra("event",event.key).putExtra("index",event.index).putExtra("iqama",event.iqama).putExtra("at",event.at).putExtra("location",location);
  return PendingIntent.getBroadcast(c,event.request,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
 }
 private static PendingIntent showIntent(Context c){return PendingIntent.getActivity(c,7300,new Intent(c,SettingsActivity.class).putExtra("page",SettingsActivity.PRAYER),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
 static synchronized void schedule(Context c){
  SharedPreferences p=PrayerTimes.prefs(c);AlarmManager a=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);if(a==null)return;
  // Remove the old, date-less PendingIntents once; they could never reliably roll over to tomorrow.
  if(p.getInt("alarm_revision",0)<200){for(int id=0;id<10;id++)a.cancel(PendingIntent.getBroadcast(c,id,new Intent(c,PrayerScheduler.class).setAction(ACTION).putExtra("index",id/2).putExtra("iqama",id%2==1),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));p.edit().putInt("alarm_revision",200).apply();}
  Set<String> old=new HashSet<>(p.getStringSet("alarm_records",Collections.emptySet())),current=new HashSet<>();
  int[] delays=new int[5];for(int i=0;i<5;i++)delays[i]=p.getInt("iqama_"+KEYS[i],15);
  boolean enabled=p.getBoolean("adhan_enabled",false),iqama=p.getBoolean("iqama_enabled",false);String location=PrayerCalendar.location(c);
  List<PrayerPlan.Event> plan=PrayerPlan.events(PrayerCalendar.days(c),System.currentTimeMillis(),enabled,iqama,delays);
  long next=0;String nextName="";int booked=0;boolean precise=exact(c);
  for(PrayerPlan.Event event:plan){
   try{
    PendingIntent pi=operation(c,event,location);
    if(precise){
     if(!event.iqama)a.setAlarmClock(new AlarmManager.AlarmClockInfo(event.at,showIntent(c)),pi);
     else a.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,event.at,pi);
    }else a.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,event.at,pi);
    current.add(event.day+","+event.index+","+event.iqama);booked++;
    if(!event.iqama&&next==0){next=event.at;nextName=NAMES[event.index];}
   }catch(Exception e){p.edit().putString("alarm_error",e.getClass().getSimpleName()+": "+e.getMessage()).apply();}
  }
  old.removeAll(current);for(String record:old){try{String[] bits=record.split(",");PrayerPlan.Event event=new PrayerPlan.Event(bits[0],Integer.parseInt(bits[1]),Boolean.parseBoolean(bits[2]),0);a.cancel(operation(c,event,location));}catch(Exception ignored){}}
  p.edit().putStringSet("alarm_records",current).putInt("alarm_count",booked).putLong("next_alarm",next).putString("next_alarm_name",nextName).putBoolean("alarms_exact",precise).apply();
  PendingIntent refresh=PendingIntent.getBroadcast(c,7301,new Intent(c,PrayerScheduler.class).setAction(REFRESH),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
  try{if(enabled||iqama){
   Calendar tomorrow=Calendar.getInstance(TimeZone.getTimeZone(zone(c)));tomorrow.add(Calendar.DAY_OF_MONTH,1);tomorrow.set(Calendar.HOUR_OF_DAY,0);tomorrow.set(Calendar.MINUTE,5);tomorrow.set(Calendar.SECOND,0);tomorrow.set(Calendar.MILLISECOND,0);
   a.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,tomorrow.getTimeInMillis(),refresh);
  }else a.cancel(refresh);}catch(Exception e){p.edit().putString("alarm_error",e.getClass().getSimpleName()).apply();}
 }
 static String zone(Context c){return PrayerCalendar.zone(c);}
 static boolean test(Context c){
  if(!exact(c))return false;
  try{long at=System.currentTimeMillis()+120000L;Intent i=new Intent(c,PrayerScheduler.class).setAction(ACTION).setData(Uri.parse("quswis://prayer/test"));
  i.putExtra("test",true).putExtra("index",0).putExtra("at",at).putExtra("event","test|"+at).putExtra("location",PrayerCalendar.location(c));
  PendingIntent pi=PendingIntent.getBroadcast(c,7303,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
  ((AlarmManager)c.getSystemService(Context.ALARM_SERVICE)).setAlarmClock(new AlarmManager.AlarmClockInfo(at,showIntent(c)),pi);return true;}catch(Exception e){PrayerTimes.prefs(c).edit().putString("alarm_error",e.getClass().getSimpleName()).apply();return false;}
 }
 static String diagnostic(Context c){
  SharedPreferences p=PrayerTimes.prefs(c);long next=p.getLong("next_alarm",0);
  if(!exact(c))return "يلزم السماح بالمنبهات الدقيقة";
  if(!p.getBoolean("adhan_enabled",false))return "الأذان التلقائي متوقف";
  if(next==0)return "لا يوجد أذان محجوز؛ حدّث التقويم";
  java.text.SimpleDateFormat f=new java.text.SimpleDateFormat("dd/MM HH:mm",Locale.US);f.setTimeZone(TimeZone.getTimeZone(zone(c)));
  return p.getString("next_alarm_name","")+" "+f.format(new Date(next))+" • "+p.getInt("alarm_count",0)+" تنبيه محجوز";
 }
 @Override public void onReceive(Context c,Intent i){
  if(i==null)return;
  if(!ACTION.equals(i.getAction())){
   PrayerCalendar.applyToday(c);schedule(c);PrayerSyncService.request(c,true);return;
  }
  int index=i.getIntExtra("index",-1);if(index<0||index>=5)return;
  boolean test=i.getBooleanExtra("test",false),iqama=i.getBooleanExtra("iqama",false);SharedPreferences p=PrayerTimes.prefs(c);
  if(!test&&!p.getBoolean(iqama?"iqama_enabled":"adhan_enabled",false))return;
  String event=i.getStringExtra("event"),location=i.getStringExtra("location");long expected=i.getLongExtra("at",0),now=System.currentTimeMillis();
  Set<String> handled=new HashSet<>(p.getStringSet("handled_alarms",Collections.emptySet()));String ledger=location+"|"+event;
  if(!PrayerPlan.deliver(PrayerCalendar.location(c),location==null?"":location,now,expected,handled.contains(ledger))){p.edit().putString("last_alarm_status","تم تجاهل تنبيه قديم أو مكرر").apply();schedule(c);return;}
  if(handled.size()>90)handled.clear();handled.add(ledger);p.edit().putStringSet("handled_alarms",handled).putLong("last_alarm_at",now).putLong("last_alarm_delay_ms",now-expected).putString("last_alarm_status","وصل تنبيه "+(test?"الاختبار":NAMES[index])).commit();
  if(iqama)notify(c,"حان وقت إقامة صلاة "+NAMES[index],true);
  else{
   try{
    hold(c);Intent playback=new Intent(c,AdhanPlaybackService.class).putExtra("event",ledger).putExtra("prayer_index",index);
    if(Build.VERSION.SDK_INT>=26)c.startForegroundService(playback);else c.startService(playback);
   }catch(Exception e){releaseHandoff();p.edit().putString("last_alarm_status","فشل تشغيل الأذان: "+e.getClass().getSimpleName()).apply();notify(c,"تعذر تشغيل الأذان؛ راجع صلاحيات الخلفية والمنبهات",false);}
  }
  PrayerCalendar.applyToday(c);schedule(c);
  if(now-p.getLong("calendar_at",0)>6*3600000L)PrayerSyncService.request(c,true);
 }
 private static void notify(Context c,String text,boolean sound){
  try{NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);String id=sound?"prayer_iqama_v3":"prayer_status_v3";
   if(Build.VERSION.SDK_INT>=26){NotificationChannel ch=new NotificationChannel(id,"مواقيت الصلاة",NotificationManager.IMPORTANCE_HIGH);if(!sound)ch.setSound(null,null);nm.createNotificationChannel(ch);}
   Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(c,id):new Notification.Builder(c);
   nm.notify(7350,b.setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle(text).setContentText(PrayerTimes.city(c)).setContentIntent(showIntent(c)).setAutoCancel(true).build());
  }catch(Exception ignored){}
 }
}
