package com.techgenius.tvoslauncher;

import android.content.Context;import android.os.*;import android.view.TextureView;
import androidx.media3.common.*;import androidx.media3.datasource.DefaultHttpDataSource;import androidx.media3.exoplayer.*;import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;

/** A bounded Media3 player. Only an actual decoded video frame marks a channel as watched. */
@androidx.media3.common.util.UnstableApi
final class LiveTvPlayer {
 interface Listener {void status(String text);void video(int width,int height);void played(LiveTvData.Channel channel,LiveTvData.Endpoint endpoint);}
 final ExoPlayer player;private final LiveTvRepository repository;private final Listener listener;private final Handler ui=new Handler(Looper.getMainLooper());
 LiveTvData.Channel channel;LiveTvData.Endpoint endpoint;int source;boolean rendered,released;private String audioLanguage="";private int ticket;
 LiveTvPlayer(Context context,TextureView video,LiveTvRepository data,Listener callback){repository=data;listener=callback;
  DefaultLoadControl buffers=new DefaultLoadControl.Builder().setBufferDurationsMs(5000,15000,1000,1500).setTargetBufferBytes(16*1024*1024).setPrioritizeTimeOverSizeThresholds(false).build();
  player=new ExoPlayer.Builder(context).setLoadControl(buffers).build();player.setVideoTextureView(video);player.setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),true);player.setHandleAudioBecomingNoisy(true);player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon().setPreferredAudioLanguage("ar").build());
  player.addListener(new Player.Listener(){
   @Override public void onPlaybackStateChanged(int state){if(released||channel==null)return;if(state==Player.STATE_BUFFERING)listener.status("جارٍ تحميل البث…");else if(state==Player.STATE_READY)listener.status(rendered?(player.isPlaying()?"● مباشر":"متوقف مؤقتًا"):"جارٍ التحقق من التشغيل الفعلي…");else if(state==Player.STATE_ENDED)listener.status("انتهى البث من المصدر؛ يمكنك إعادة المحاولة");}
   @Override public void onIsPlayingChanged(boolean playing){if(rendered)listener.status(playing?"● مباشر":"متوقف مؤقتًا");}
   @Override public void onVideoSizeChanged(VideoSize size){listener.video(Math.round(size.width*size.pixelWidthHeightRatio),size.height);}
   @Override public void onRenderedFirstFrame(){if(released||channel==null)return;rendered=true;repository.success(endpoint);if(LiveTvData.hasArabic(audioLanguage))repository.confirmArabic(channel,endpoint,"selected_audio_track:"+audioLanguage);listener.status("● مباشر • تم التحقق من التشغيل");listener.played(channel,endpoint);}
   @Override public void onTracksChanged(Tracks tracks){audioLanguage="";for(Tracks.Group group:tracks.getGroups())if(group.getType()==C.TRACK_TYPE_AUDIO)for(int i=0;i<group.length;i++)if(group.isTrackSelected(i)){String lang=group.getTrackFormat(i).language;if(lang!=null)audioLanguage=lang;}if(rendered&&LiveTvData.hasArabic(audioLanguage))repository.confirmArabic(channel,endpoint,"selected_audio_track:"+audioLanguage);}
   @Override public void onPlayerError(PlaybackException error){if(released||endpoint==null)return;rendered=false;repository.failure(endpoint);listener.status(message(error));}
  });
 }
 void play(LiveTvData.Channel selected,int index){if(released||selected==null||selected.streams.isEmpty())return;ticket++;channel=selected;source=Math.floorMod(index,selected.streams.size());endpoint=selected.streams.get(source);rendered=false;audioLanguage="";listener.status("جارٍ الاتصال بالمصدر…");
  DefaultHttpDataSource.Factory http=new DefaultHttpDataSource.Factory().setUserAgent(endpoint.agent.isEmpty()?"Mozilla/5.0 QuswisLiveTV":endpoint.agent).setConnectTimeoutMs(7000).setReadTimeoutMs(9000).setAllowCrossProtocolRedirects(false);
  if(!endpoint.referrer.isEmpty())http.setDefaultRequestProperties(java.util.Collections.singletonMap("Referer",endpoint.referrer));
  player.stop();player.setMediaSource(new DefaultMediaSourceFactory(http).createMediaSource(new MediaItem.Builder().setUri(endpoint.url).setMediaId(channel.key()).build()));player.prepare();player.play();
  final int request=ticket;ui.postDelayed(()->{if(!released&&ticket==request&&!rendered){player.stop();repository.failure(endpoint);listener.status("لم يبدأ البث. قد يكون الرابط معطّلًا أو محجوبًا جغرافيًا. جرّب مصدرًا آخر من الإعدادات.");}},25000);
 }
 String audio(){return audioLanguage.isEmpty()?"لغة مسار الصوت غير موثقة من المصدر":LiveTvData.hasArabic(audioLanguage)?"مسار الصوت المحدد عربي":"لغة مسار الصوت المحدد: "+audioLanguage;}
 void toggle(){if(player.isPlaying())player.pause();else if(player.getPlaybackState()==Player.STATE_IDLE&&channel!=null)play(channel,source);else player.play();}
 void release(){if(released)return;released=true;ticket++;ui.removeCallbacksAndMessages(null);player.clearVideoSurface();player.release();}
 private String message(PlaybackException e){int code=e.errorCode;if(code==PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS)return "المصدر يرفض الاتصال أو غير متاح في منطقتك. لم يُثبت نجاح التشغيل.";if(code>=6000&&code<7000)return "بث محمي أو يحتاج اشتراكًا؛ لا يتم تجاوز DRM.";if(code==PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED||code==PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED)return "صيغة البث غير مدعومة على هذا الجهاز.";return "تعذّر تشغيل المصدر ("+code+"). تحقق من الإنترنت أو جرّب مصدرًا آخر.";}
}
