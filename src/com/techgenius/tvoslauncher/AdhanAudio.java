package com.techgenius.tvoslauncher;

import android.content.Context;
import android.content.Intent;
import android.os.Build;

/** Settings preview uses the exact same offline audio and media handling as an alarm. */
final class AdhanAudio {
 private AdhanAudio(){}
 static void play(Context context){
  Context app=context.getApplicationContext();
  Intent intent=new Intent(app,AdhanPlaybackService.class);
  try{
   if(Build.VERSION.SDK_INT>=26)app.startForegroundService(intent);else app.startService(intent);
  }catch(Exception e){android.widget.Toast.makeText(app,"تعذر تشغيل الأذان",android.widget.Toast.LENGTH_SHORT).show();}
 }
 static void stop(Context context){
  Context app=context.getApplicationContext();
  // StartService is legal from this visible settings action and delivers STOP to the active service.
  try{app.startService(new Intent(app,AdhanPlaybackService.class).setAction(AdhanPlaybackService.STOP));}catch(Exception ignored){}
 }
}
