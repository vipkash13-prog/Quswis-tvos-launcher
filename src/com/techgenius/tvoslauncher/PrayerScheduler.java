package com.techgenius.tvoslauncher;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** Non-disruptive prayer and iqama notifications. No app switching or overlay permission. */
public final class PrayerScheduler extends BroadcastReceiver {
 static final String CHANNEL="prayer_alerts_v2";
 static final String[] KEYS={"Fajr","Dhuhr","Asr","Maghrib","Isha"};
 static final String[] NAMES={"الفجر","الظهر","العصر","المغرب","العشاء"};
 static final String ACTION="com.techgenius.tvoslauncher.PRAYER_ALARM";
 static void schedule(Context context) {
  if(!PrayerTimes.prefs(context).getBoolean("adhan_enabled",false) && !PrayerTimes.prefs(context).getBoolean("iqama_enabled",false))return;
  JSONObject times;
  try{times=new JSONObject(PrayerTimes.prefs(context).getString("data","{}"));}catch(Exception e){return;}
  AlarmManager manager=(AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
  if(manager==null)return;
  Calendar now=Calendar.getInstance();
  for(int i=0;i<5;i++){
   String raw=times.optString(KEYS[i],"");
   if(raw.length()<5)continue;
   try{
    int hour=Integer.parseInt(raw.substring(0,2));
    int minute=Integer.parseInt(raw.substring(3,5));
    Calendar when=Calendar.getInstance();
    when.set(Calendar.HOUR_OF_DAY,hour);when.set(Calendar.MINUTE,minute);when.set(Calendar.SECOND,0);
    when.set(Calendar.MILLISECOND,0);
    if(when.before(now))continue;
    if(PrayerTimes.prefs(context).getBoolean("adhan_enabled",false))set(manager,context,i,false,when.getTimeInMillis());
    int delay=PrayerTimes.prefs(context).getInt("iqama_"+KEYS[i],15);
    if(delay>0&&PrayerTimes.prefs(context).getBoolean("iqama_enabled",false))set(manager,context,i,true,when.getTimeInMillis()+delay*60000L);
   }catch(Exception ignored){}
  }
 }
 private static void set(AlarmManager manager,Context context,int index,boolean iqama,long time){
  Intent intent=new Intent(context,PrayerScheduler.class).setAction(ACTION);
  intent.putExtra("index",index).putExtra("iqama",iqama);
  int flags=PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE;
  PendingIntent pi=PendingIntent.getBroadcast(context,index*2+(iqama?1:0),intent,flags);
  if (Build.VERSION.SDK_INT >= 31 && manager.canScheduleExactAlarms()) {
   manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,time,pi);
  } else if (Build.VERSION.SDK_INT < 31) {
   manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,time,pi);
  } else {
   manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,time,pi);
  }
 }
 @Override public void onReceive(Context context,Intent intent){
  if(Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())||Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())){
   PrayerTimes.refresh(context,null,true);
   schedule(context);return;
  }
  if(!ACTION.equals(intent.getAction()))return;
  int index=intent.getIntExtra("index",-1);if(index<0||index>=5)return;
  boolean iqama=intent.getBooleanExtra("iqama",false);
  if(!PrayerTimes.prefs(context).getBoolean(iqama?"iqama_enabled":"adhan_enabled",false))return;
  NotificationManager nm=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
  if(nm==null)return;
  if(Build.VERSION.SDK_INT>=26){
   NotificationChannel channel=new NotificationChannel(CHANNEL,"مواقيت الصلاة",NotificationManager.IMPORTANCE_HIGH);
   channel.setDescription("تنبيهات صوتية عند وقت الصلاة والإقامة بدون مغادرة التطبيقات");
   channel.enableVibration(true);
   channel.setSound(android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM),
       new android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_ALARM).build());
   nm.createNotificationChannel(channel);
  }
  String title=(iqama?"حان وقت إقامة صلاة ":"حان وقت أذان صلاة ")+NAMES[index];
  Notification.Builder builder=Build.VERSION.SDK_INT>=26?new Notification.Builder(context,CHANNEL):new Notification.Builder(context);
  builder.setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle(title)
   .setContentText(PrayerTimes.city(context)).setAutoCancel(true)
   .setCategory(Notification.CATEGORY_REMINDER).setPriority(Notification.PRIORITY_HIGH);
  try{nm.notify(7300+index*2+(iqama?1:0),builder.build());}catch(SecurityException ignored){}
  if (!iqama) AdhanAudio.play(context);
  // Refresh next-day schedule once a prayer fires; next fetch updates actual times.
  PrayerTimes.refresh(context,null,false);
 }
}
