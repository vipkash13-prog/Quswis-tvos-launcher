package com.techgenius.tvoslauncher;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.content.res.AssetFileDescriptor;
import android.media.*;
import android.media.session.*;
import android.os.*;
import java.util.ArrayList;
import java.util.List;

/** Offline adhan with transient audio focus and session-specific media restoration. */
public final class AdhanPlaybackService extends Service {
 static final String STOP="com.techgenius.tvoslauncher.STOP_ADHAN";
 private static final String CHANNEL="quswis_adhan_playback_mulla_v1";
 private final Handler handler=new Handler(Looper.getMainLooper());
 private final List<PausedSession> paused=new ArrayList<>();
 private MediaPlayer player;
 private PowerManager.WakeLock playbackWake;
 private AudioManager audio;
 private AudioFocusRequest focusRequest;
 private boolean hasFocus,finishing,destroyed,ownsMediaInterruption;
 private final AudioManager.OnAudioFocusChangeListener focusChange=change->{
  // A call, another app or the user took focus: do not restart the film over it.
  if(change<0){for(PausedSession session:paused)session.guard.invalidate();finish(false);}
 };
 @Override public IBinder onBind(Intent i){return null;}
 @Override public int onStartCommand(Intent intent,int flags,int id){
  if(intent!=null&&STOP.equals(intent.getAction())){finish(true);return START_NOT_STICKY;}
  if(player!=null||finishing){PrayerScheduler.releaseHandoff();return START_NOT_STICKY;}
  try{
   PowerManager power=(PowerManager)getSystemService(POWER_SERVICE);
   playbackWake=power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"Quswis:adhan-playback");
   playbackWake.setReferenceCounted(false);playbackWake.acquire(10*60*1000L);
   PrayerScheduler.releaseHandoff();
   audio=(AudioManager)getSystemService(AUDIO_SERVICE);
   NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
   if(Build.VERSION.SDK_INT>=26){
    NotificationChannel channel=new NotificationChannel(CHANNEL,"تشغيل الأذان",NotificationManager.IMPORTANCE_LOW);
    channel.setSound(null,null);nm.createNotificationChannel(channel);
   }
   PendingIntent stop=PendingIntent.getService(this,7352,new Intent(this,AdhanPlaybackService.class).setAction(STOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
   Notification n=(Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL):new Notification.Builder(this))
    .setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle("الأذان • "+AdhanRecording.title(this))
    .setContentText("تستأنف الوسائط المتوقفة عند اكتمال الأذان").setOngoing(true)
    .addAction(new Notification.Action.Builder(null,"إيقاف الأذان",stop).build()).build();
   if(Build.VERSION.SDK_INT>=29)startForeground(7351,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
   else startForeground(7351,n);
   MediaPlayer next=new MediaPlayer();player=next;
   next.setAudioAttributes(attributes());
   next.setWakeMode(getApplicationContext(),PowerManager.PARTIAL_WAKE_LOCK);
   try(AssetFileDescriptor fd=getAssets().openFd(AdhanRecording.asset(this))){
    next.setDataSource(fd.getFileDescriptor(),fd.getStartOffset(),fd.getLength());
   }
   next.setOnPreparedListener(mp->{
    if(mp!=player||finishing)return;
    try{
     capturePlayingSessions();
     PlaybackAudio.beginAdhan();ownsMediaInterruption=true;
     if(!requestFocus()){PrayerTimes.prefs(this).edit().putString("last_alarm_status","رفض النظام أولوية الصوت؛ لم يبدأ الأذان").apply();finish(false);return;}
     for(PausedSession session:paused)session.pause();
     mp.start();
     PrayerTimes.prefs(this).edit().putString("last_alarm_status","بدأ تشغيل الأذان").putLong("last_playback_at",System.currentTimeMillis()).apply();
     // Only a stuck player uses this watchdog; normal completion comes from MediaPlayer.
     handler.postDelayed(()->finish(true),Math.max(mp.getDuration()+30000L,60000L));
    }catch(Exception e){android.util.Log.e("Quswis-Adhan","Playback failed",e);finish(true);}
   });
   next.setOnCompletionListener(mp->{PrayerTimes.prefs(this).edit().putString("last_alarm_status","اكتمل الأذان؛ استعادة الوسائط المؤهلة").apply();finish(true);});
   next.setOnErrorListener((mp,what,extra)->{PrayerTimes.prefs(this).edit().putString("last_alarm_status","خطأ تشغيل الأذان: "+what+"/"+extra).apply();finish(true);return true;});
   next.prepareAsync();
   handler.postDelayed(()->{if(!hasFocus)finish(false);},30000L);
  }catch(Exception e){android.util.Log.e("Quswis-Adhan","Could not prepare adhan",e);finish(true);}
  return START_NOT_STICKY;
 }
 private AudioAttributes attributes(){return new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();}
 private boolean requestFocus(){
  if(audio==null)return false;
  if(Build.VERSION.SDK_INT>=26){
   focusRequest=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
    .setAudioAttributes(attributes()).setWillPauseWhenDucked(true).setOnAudioFocusChangeListener(focusChange,handler).build();
   hasFocus=audio.requestAudioFocus(focusRequest)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
  }else hasFocus=audio.requestAudioFocus(focusChange,AudioManager.STREAM_ALARM,AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
  return hasFocus;
 }
 private void capturePlayingSessions(){
  if(!MediaWatchService.granted(this))return;
  try{
   MediaSessionManager manager=(MediaSessionManager)getSystemService(MEDIA_SESSION_SERVICE);
   if(manager==null)return;
   for(MediaController controller:manager.getActiveSessions(new ComponentName(this,MediaWatchService.class))){
    PlaybackState state=controller.getPlaybackState();
    if(getPackageName().equals(controller.getPackageName())||state==null||state.getState()!=PlaybackState.STATE_PLAYING)continue;
    long actions=state.getActions();
    boolean canPause=(actions&(PlaybackState.ACTION_PAUSE|PlaybackState.ACTION_PLAY_PAUSE))!=0;
    boolean canPlay=(actions&(PlaybackState.ACTION_PLAY|PlaybackState.ACTION_PLAY_PAUSE))!=0;
    if(canPause&&canPlay)paused.add(new PausedSession(controller));
   }
  }catch(SecurityException e){android.util.Log.w("Quswis-Adhan","Notification access is needed for session control");}
 }
 private static String content(MediaController c){
  MediaMetadata m=c.getMetadata();if(m==null)return "";
  return String.valueOf(m.getString(MediaMetadata.METADATA_KEY_MEDIA_ID))+"|"+
   String.valueOf(m.getString(MediaMetadata.METADATA_KEY_TITLE))+"|"+
   String.valueOf(m.getString(MediaMetadata.METADATA_KEY_ARTIST));
 }
 private final class PausedSession {
  final MediaController controller;
  final MediaResumeGuard guard;
  final MediaController.Callback callback=new MediaController.Callback(){
   @Override public void onPlaybackStateChanged(PlaybackState state){observe(state);}
   @Override public void onMetadataChanged(MediaMetadata metadata){
    if(!identity.equals(content(controller)))guard.invalidate();
   }
   @Override public void onSessionDestroyed(){guard.invalidate();}
  };
  final String identity;
  PausedSession(MediaController c){controller=c;identity=content(c);guard=new MediaResumeGuard(identity);c.registerCallback(callback,handler);}
  void observe(PlaybackState s){if(s==null)guard.invalidate();else guard.observe(s.getState(),s.getPosition(),content(controller));}
  void pause(){
   try{
    PlaybackState state=controller.getPlaybackState();
    if(state!=null&&state.getState()==PlaybackState.STATE_PLAYING)controller.getTransportControls().pause();
    observe(controller.getPlaybackState());
   }catch(Exception e){guard.invalidate();}
  }
  void restore(){
   try{
    PlaybackState state=controller.getPlaybackState();
    if(state!=null&&guard.canResume(state.getState(),state.getPosition(),content(controller)))controller.getTransportControls().play();
   }catch(Exception ignored){}finally{close();}
  }
  void close(){try{controller.unregisterCallback(callback);}catch(Exception ignored){}}
 }
 private void releasePlayerAndFocus(boolean restore){
  if(player!=null){MediaPlayer old=player;player=null;old.release();}
  if(audio!=null&&hasFocus){
   hasFocus=false;
   if(Build.VERSION.SDK_INT>=26&&focusRequest!=null)audio.abandonAudioFocusRequest(focusRequest);
   else audio.abandonAudioFocus(focusChange);
  }
  if(ownsMediaInterruption){ownsMediaInterruption=false;PlaybackAudio.endAdhan(restore);}
 }
 private void finish(boolean restore){
  if(finishing||destroyed)return;
  finishing=true;handler.removeCallbacksAndMessages(null);releasePlayerAndFocus(restore);
  // Focus restoration may resume the player itself; recheck state before sending play.
  handler.postDelayed(()->{
   for(PausedSession session:paused){if(restore)session.restore();else session.close();}
   paused.clear();releaseWake();stopForeground(true);stopSelf();
  },350L);
 }
 private void releaseWake(){if(playbackWake!=null){if(playbackWake.isHeld())playbackWake.release();playbackWake=null;}PrayerScheduler.releaseHandoff();}
 @Override public void onDestroy(){
  destroyed=true;handler.removeCallbacksAndMessages(null);releasePlayerAndFocus(false);
  for(PausedSession session:paused)session.close();paused.clear();releaseWake();super.onDestroy();
 }
}
