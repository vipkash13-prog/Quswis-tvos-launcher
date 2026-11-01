package com.techgenius.tvoslauncher;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.media.*;
import android.os.*;
import android.view.KeyEvent;

/** Foreground adhan playback; pauses active media and resumes it on completion. */
public final class AdhanPlaybackService extends Service {
 private static final String CHANNEL="quswis_adhan_playback";
 private static final String AUDIO="https://commons.wikimedia.org/wiki/Special:Redirect/file/Beautiful_adhan.ogg";
 private final Handler handler=new Handler(Looper.getMainLooper());
 private MediaPlayer player;
 private AudioManager audio;
 private AudioFocusRequest focusRequest;
 private boolean pausedOther=false,hasFocus=false;
 private final AudioManager.OnAudioFocusChangeListener focusChange=change->{};
 @Override public IBinder onBind(Intent i){return null;}
 @Override public int onStartCommand(Intent intent,int flags,int id){
  if(player!=null)return START_NOT_STICKY;
  audio=(AudioManager)getSystemService(AUDIO_SERVICE);
  NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
  if(Build.VERSION.SDK_INT>=26)nm.createNotificationChannel(new NotificationChannel(CHANNEL,"الأذان",NotificationManager.IMPORTANCE_LOW));
  Notification n=(Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL):new Notification.Builder(this))
    .setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle("حان وقت الأذان").setOngoing(true).build();
  if(Build.VERSION.SDK_INT>=29)startForeground(7351,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
  else startForeground(7351,n);
  pausedOther=audio!=null&&audio.isMusicActive();
  if(pausedOther)mediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE);
  if(audio!=null){
   if(Build.VERSION.SDK_INT>=26){
    focusRequest=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
      .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
      .setOnAudioFocusChangeListener(focusChange).build();
    hasFocus=audio.requestAudioFocus(focusRequest)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
   }else hasFocus=audio.requestAudioFocus(focusChange,AudioManager.STREAM_MUSIC,AudioManager.AUDIOFOCUS_GAIN)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
  }
  try{
   player=new MediaPlayer();
   player.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
   player.setDataSource(AUDIO);
   player.setOnPreparedListener(MediaPlayer::start);
   player.setOnCompletionListener(mp->finish());
   player.setOnErrorListener((mp,what,extra)->{finish();return true;});
   player.prepareAsync();
   handler.postDelayed(this::finish,9*60*1000L);
  }catch(Exception e){finish();}
  return START_NOT_STICKY;
 }
 private void mediaKey(int code){
  if(audio==null)return;
  audio.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,code));
  audio.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,code));
 }
 private void finish(){
  handler.removeCallbacksAndMessages(null);
  if(player!=null){try{player.release();}catch(Exception ignored){}player=null;}
  if(audio!=null&&hasFocus){
   if(Build.VERSION.SDK_INT>=26&&focusRequest!=null)audio.abandonAudioFocusRequest(focusRequest);
   else audio.abandonAudioFocus(focusChange);
  }
  hasFocus=false;
  if(pausedOther){pausedOther=false;handler.postDelayed(()->mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY),400);}
  handler.postDelayed(this::stopSelf,900);
 }
 @Override public void onDestroy(){if(player!=null)finish();super.onDestroy();}
}
