package com.techgenius.tvoslauncher;

import android.app.*;import android.content.*;import android.content.pm.ServiceInfo;import android.media.*;import android.os.*;

/** Separate test-APK UID requests real notification-style focus. Never packaged in the launcher. */
public final class FocusProbeService extends Service {
    private AudioManager audio;private AudioFocusRequest request;private ResultReceiver reply;
    @Override public IBinder onBind(Intent i){return null;}
    @Override public void onCreate(){super.onCreate();
        NotificationManager notifications=getSystemService(NotificationManager.class);
        notifications.createNotificationChannel(new NotificationChannel("focus-probe","Audio focus test only",NotificationManager.IMPORTANCE_LOW));
        startForeground(9701,new Notification.Builder(this,"focus-probe").setSmallIcon(android.R.drawable.ic_media_pause).setContentTitle("Transient focus test").build(),ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
    }
    @Override public int onStartCommand(Intent i,int flags,int id){
        reply=i==null?null:(ResultReceiver)i.getParcelableExtra("reply");
        audio=getSystemService(AudioManager.class);
        request=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).setOnAudioFocusChangeListener(c->{}).build();
        int granted=audio.requestAudioFocus(request);android.util.Log.i("QuswisAudioProbe","real separate-UID focus result="+granted);if(reply!=null)reply.send(granted,new Bundle());return START_NOT_STICKY;
    }
    @Override public void onDestroy(){if(audio!=null&&request!=null)audio.abandonAudioFocusRequest(request);stopForeground(true);if(reply!=null)reply.send(99,new Bundle());super.onDestroy();}
}
