package com.techgenius.tvoslauncher;

import android.content.Context;import android.os.*;import android.view.TextureView;
import androidx.media3.common.*;import androidx.media3.datasource.DefaultHttpDataSource;import androidx.media3.exoplayer.*;import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;

/** A bounded Media3 player. Only an actual decoded video frame marks a channel as watched. */
@androidx.media3.common.util.UnstableApi
final class LiveTvPlayer {
 interface Listener {void status(String text);void video(int width,int height);void played(LiveTvData.Channel channel,LiveTvData.Endpoint endpoint);}
 final ExoPlayer player;final PlaybackAudio audioFocus;private final LiveTvRepository repository;private final Listener listener;private final Handler ui=new Handler(Looper.getMainLooper());
 LiveTvData.Channel channel;LiveTvData.Endpoint endpoint;int source;boolean rendered,released;private String audioLanguage="";private int ticket;private Runnable deadline;private int fallbackAttempts;
 LiveTvPlayer(Context context,TextureView video,LiveTvRepository data,Listener callback){repository=data;listener=callback;
  DefaultLoadControl buffers=new DefaultLoadControl.Builder().setBufferDurationsMs(5000,15000,1000,1500).setTargetBufferBytes(16*1024*1024).setPrioritizeTimeOverSizeThresholds(false).build();
  player=new ExoPlayer.Builder(context).setLoadControl(buffers).build();player.setVideoTextureView(video);player.setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),false);player.setHandleAudioBecomingNoisy(true);player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon().setPreferredAudioLanguage("ar").build());
  player.setVolume(1.0f);
  audioFocus=new PlaybackAudio(context,android.media.AudioAttributes.CONTENT_TYPE_MOVIE,new PlaybackAudio.Client(){
   public void pauseForFocus(){if(!released){cancelDeadline();player.pause();if(PlaybackAudio.adhanRunning())player.stop();}}
   public void resumeAtUnity(){if(!released){player.setVolume(1.0f);if(player.getPlaybackState()==Player.STATE_IDLE){rendered=false;player.seekToDefaultPosition();player.prepare();}player.play();watchdog();}}
   public void replaced(){if(!released){ticket++;cancelDeadline();player.pause();player.stop();rendered=false;listener.status("توقف البث عند الانتقال إلى مشغل صوت آخر");}}
  });
  player.addListener(new Player.Listener(){
   @Override public void onPlayWhenReadyChanged(boolean ready,int reason){if(!ready&&reason==Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY)audioFocus.pauseIntent();}
   @Override public void onPlaybackStateChanged(int state){if(released||channel==null)return;if(state==Player.STATE_BUFFERING)listener.status("جارٍ تحميل البث…");else if(state==Player.STATE_READY)listener.status(rendered?(player.isPlaying()?"● مباشر":"متوقف مؤقتًا"):"جارٍ التحقق من التشغيل الفعلي…");else if(state==Player.STATE_ENDED){audioFocus.pauseIntent();listener.status("انتهى البث من المصدر؛ يمكنك إعادة المحاولة");}}
   @Override public void onIsPlayingChanged(boolean playing){if(rendered)listener.status(playing?"● مباشر":"متوقف مؤقتًا");}
   @Override public void onVideoSizeChanged(VideoSize size){listener.video(Math.round(size.width*size.pixelWidthHeightRatio),size.height);}
   @Override public void onRenderedFirstFrame(){if(released||channel==null)return;rendered=true;cancelDeadline();fallbackAttempts=0;repository.success(channel,endpoint);if(LiveTvData.hasArabic(audioLanguage))repository.confirmArabic(channel,endpoint,"selected_audio_track:"+audioLanguage);listener.status("● مباشر • تم التحقق من التشغيل");listener.played(channel,endpoint);}
   @Override public void onTracksChanged(Tracks tracks){audioLanguage="";for(Tracks.Group group:tracks.getGroups())if(group.getType()==C.TRACK_TYPE_AUDIO)for(int i=0;i<group.length;i++)if(group.isTrackSelected(i)){String lang=group.getTrackFormat(i).language;if(lang!=null)audioLanguage=lang;}if(rendered&&LiveTvData.hasArabic(audioLanguage))repository.confirmArabic(channel,endpoint,"selected_audio_track:"+audioLanguage);}
   @Override public void onPlayerError(PlaybackException error){if(released||endpoint==null)return;rendered=false;android.util.Log.e("Quswis-Live",diagnostic(error));repository.failure(endpoint);if(!nextSource(message(error))){audioFocus.pauseIntent();listener.status(message(error)+"\n"+diagnostic(error));}}
  });
 }
 void play(LiveTvData.Channel selected,int index){fallbackAttempts=0;try{play1(selected,index);}catch(Throwable t){android.util.Log.e("Quswis-Live","Player failed to start",t);rendered=false;listener.status("تعذّر تشغيل المشغل الداخلي ("+t.getClass().getSimpleName()+"). أعد المحاولة أو اختر قناة أخرى.");}}
 private void play1(LiveTvData.Channel selected,int index){if(released||selected==null||selected.streams.isEmpty())return;ticket++;channel=selected;source=Math.floorMod(index,selected.streams.size());endpoint=selected.streams.get(source);rendered=false;audioLanguage="";listener.status("جارٍ الاتصال بالمصدر…");
  DefaultHttpDataSource.Factory http=new DefaultHttpDataSource.Factory().setUserAgent(endpoint.agent.isEmpty()?"Mozilla/5.0 QuswisLiveTV":endpoint.agent).setConnectTimeoutMs(7000).setReadTimeoutMs(9000).setAllowCrossProtocolRedirects(false);
  if(!endpoint.referrer.isEmpty())http.setDefaultRequestProperties(java.util.Collections.singletonMap("Referer",endpoint.referrer));
  player.stop();String lower=endpoint.url.toLowerCase(java.util.Locale.ROOT);String mime=lower.contains(".m3u8")?MimeTypes.APPLICATION_M3U8:lower.contains(".mpd")?MimeTypes.APPLICATION_MPD:MimeTypes.VIDEO_MP2T;MediaItem item=new MediaItem.Builder().setUri(endpoint.url).setMimeType(mime).setMediaId(channel.key()).build();android.util.Log.i("Quswis-Live","Opening stream type="+mime+" source="+source);player.setMediaSource(new DefaultMediaSourceFactory(http).createMediaSource(item));player.prepare();resume();

 }
 private boolean nextSource(String reason){if(released||channel==null||rendered||fallbackAttempts>=channel.streams.size()-1)return false;fallbackAttempts++;listener.status("تعذّر المصدر الحالي؛ جارٍ تجربة المصدر الاحتياطي…");try{play1(channel,source+1);return true;}catch(Throwable t){android.util.Log.e("Quswis-Live","Fallback player error",t);return false;}}
 private void cancelDeadline(){if(deadline!=null)ui.removeCallbacks(deadline);deadline=null;}
 private void watchdog(){cancelDeadline();if(rendered)return;final int request=ticket;deadline=()->{if(!released&&ticket==request&&!rendered&&audioFocus.focused()&&audioFocus.wantsPlay()){player.stop();repository.failure(endpoint);if(!nextSource("timeout")){audioFocus.pauseIntent();listener.status("لم يبدأ البث من المصادر المتاحة. جرّب قناة أخرى أو تحقق من الاشتراك.");}}};ui.postDelayed(deadline,25000);}
 String audio(){return audioLanguage.isEmpty()?"لغة مسار الصوت غير موثقة من المصدر":LiveTvData.hasArabic(audioLanguage)?"مسار الصوت المحدد عربي":"لغة مسار الصوت المحدد: "+audioLanguage;}
 void pause(){cancelDeadline();audioFocus.pauseIntent();player.pause();}
 void resume(){if(released)return;if(player.getPlaybackState()==Player.STATE_IDLE&&channel!=null){play(channel,source);return;}player.setVolume(1.0f);if(audioFocus.playIntent()){player.play();watchdog();}else{player.pause();listener.status(PlaybackAudio.adhanRunning()?"متوقف مؤقتًا أثناء الأذان":"بانتظار أولوية الصوت؛ يمكنك إعادة المحاولة");}}
 void toggle(){if(player.getPlayWhenReady()||audioFocus.wantsPlay())pause();else if(player.getPlaybackState()==Player.STATE_IDLE&&channel!=null)play(channel,source);else resume();}
 void release(){if(released)return;released=true;audioFocus.release();ticket++;ui.removeCallbacksAndMessages(null);player.clearVideoSurface();player.release();}
 private String diagnostic(PlaybackException e){StringBuilder b=new StringBuilder("تشخيص البث: "+PlaybackException.getErrorCodeName(e.errorCode)+" ("+e.errorCode+")");Throwable cause=e.getCause();int depth=0;while(cause!=null&&depth++<3){b.append(" | ").append(cause.getClass().getSimpleName());cause=cause.getCause();}b.append(" | المصدر ").append(source+1).append("/").append(channel==null?0:channel.streams.size());return b.toString();}
 private String message(PlaybackException e){int code=e.errorCode;if(code==PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS)return "المصدر يرفض الاتصال أو غير متاح في منطقتك. لم يُثبت نجاح التشغيل.";if(code>=6000&&code<7000)return "بث محمي أو يحتاج اشتراكًا؛ لا يتم تجاوز DRM.";if(code==PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED||code==PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED)return "صيغة البث غير مدعومة على هذا الجهاز.";return "تعذّر تشغيل المصدر ("+code+"). تحقق من الإنترنت أو جرّب مصدرًا آخر.";}
}
