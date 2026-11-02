package com.techgenius.tvoslauncher;

import android.app.job.*;
import android.content.*;

/** JobScheduler owns network work; exact prayer delivery never depends on a job running on time. */
public final class PrayerSyncService extends JobService {
 private final java.util.concurrent.ConcurrentHashMap<Integer,Thread> workers=new java.util.concurrent.ConcurrentHashMap<>();
 static void request(Context c,boolean immediate){
  try{JobScheduler jobs=(JobScheduler)c.getSystemService(JOB_SCHEDULER_SERVICE);if(jobs==null)return;
  ComponentName component=new ComponentName(c,PrayerSyncService.class);
  if(jobs.getPendingJob(7310)==null)jobs.schedule(new JobInfo.Builder(7310,component).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPersisted(true).setPeriodic(12*3600000L).build());
  if(immediate&&jobs.getPendingJob(7311)==null)jobs.schedule(new JobInfo.Builder(7311,component).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setBackoffCriteria(30000L,JobInfo.BACKOFF_POLICY_EXPONENTIAL).build());
  }catch(Exception e){PrayerTimes.prefs(c).edit().putString("calendar_status","تعذر بدء تحديث التقويم: "+e.getClass().getSimpleName()).apply();}
 }
 @Override public boolean onStartJob(JobParameters params){
  Thread worker=new Thread(()->{
   boolean ok=PrayerCalendar.sync(this,false);
   if(workers.remove(params.getJobId(),Thread.currentThread())){PrayerScheduler.schedule(this);jobFinished(params,!ok);}
  },"prayer-calendar-job");workers.put(params.getJobId(),worker);worker.start();return true;
 }
 @Override public boolean onStopJob(JobParameters params){Thread worker=workers.remove(params.getJobId());if(worker!=null)worker.interrupt();return true;}
}
