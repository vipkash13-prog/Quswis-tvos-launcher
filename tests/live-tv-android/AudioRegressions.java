package com.techgenius.tvoslauncher;

import android.content.*;import android.media.AudioManager;import android.os.*;import android.view.*;import android.widget.*;import java.lang.reflect.*;import java.util.*;import java.util.concurrent.atomic.AtomicBoolean;
import androidx.media3.common.*;import androidx.media3.exoplayer.*;import androidx.media3.exoplayer.analytics.*;

/** Real streams + AudioTrack progress, actual cross-UID focus, actual full offline adhan completion. */
@androidx.media3.common.util.UnstableApi
final class AudioRegressions {
    private final AudioPerfInstrumentation t;private final MainActivity home;
    AudioRegressions(AudioPerfInstrumentation t,MainActivity home){this.t=t;this.home=home;}
    interface Check{boolean ok();}
    void await(String name,long timeout,Check test){long end=SystemClock.elapsedRealtime()+timeout;boolean[] yes={false};while(!yes[0]&&SystemClock.elapsedRealtime()<end){t.main(()->yes[0]=test.ok());if(!yes[0])SystemClock.sleep(100);}t.check(yes[0],name);t.status("AUDIO_CHECK "+name);}
    Object field(Object o,String key)throws Exception{Field f=o.getClass().getDeclaredField(key);f.setAccessible(true);return f.get(o);}
    ExoPlayer radio(){try{Field f=QuranRadio.class.getDeclaredField("player");f.setAccessible(true);return(ExoPlayer)f.get(null);}catch(Exception e){throw new RuntimeException(e);}}
    PlaybackAudio radioFocus(){try{Field f=QuranRadio.class.getDeclaredField("audioFocus");f.setAccessible(true);return(PlaybackAudio)f.get(null);}catch(Exception e){throw new RuntimeException(e);}}
    void playing(ExoPlayer player,String label)throws Exception{
        AtomicBoolean advancing=new AtomicBoolean();t.main(()->player.addAnalyticsListener(new AnalyticsListener(){@Override public void onAudioPositionAdvancing(EventTime event,long start){advancing.set(true);}}));
        await(label+" real audio format + playback",45000,()->player.isPlaying()&&player.getAudioFormat()!=null&&player.getCurrentPosition()>800&&player.getVolume()==1f);
        // Audio position may have started before this listener attached, so observe actual played position too.
        long[] initial={0};t.main(()->initial[0]=player.getCurrentPosition());SystemClock.sleep(800);t.main(()->{t.check(player.getCurrentPosition()>initial[0],label+" stalled playhead");Format f=player.getAudioFormat();t.status("AUDIO_ROUTE "+label+" volume="+player.getVolume()+" usage="+player.getAudioAttributes().usage+" content="+player.getAudioAttributes().contentType+" channels="+f.channelCount+" sample_rate="+f.sampleRate+" audio_session="+player.getAudioSessionId()+" position_event="+advancing.get());});
    }
    LiveTvData.Channel channel(String id,String name,String url){LiveTvData.Channel c=new LiveTvData.Channel();c.id=id;c.name=name;LiveTvData.Endpoint e=new LiveTvData.Endpoint();e.url=url;c.streams.add(e);return c;}
    void live(LiveTvDialog dialog,LiveTvData.Channel c)throws Exception{t.main(()->dialog.play(c,0));await("actual video "+c.id,45000,()->dialog.engine.rendered);playing(dialog.engine.player,c.id);}
    void probe(Check stopped,Check restored)throws Exception{probe(stopped,restored,0);}
    void probe(Check stopped,Check restored,long hold)throws Exception{
        Intent intent=new Intent().setComponent(new ComponentName(t.getContext().getPackageName(),FocusProbeService.class.getName()));
        boolean[] granted={false},released={false};
        intent.putExtra("reply",new ResultReceiver(new Handler(Looper.getMainLooper())){@Override protected void onReceiveResult(int code,Bundle data){if(code==AudioManager.AUDIOFOCUS_REQUEST_GRANTED)granted[0]=true;if(code==99)released[0]=true;}});
        t.main(()->home.startForegroundService(intent));
        try{
            await("separate-UID service confirms foreground + granted focus",15000,()->granted[0]);
            await("separate-UID transient notification focus pauses owner",15000,stopped);
            if(hold>0){SystemClock.sleep(hold);t.main(()->t.check(stopped.ok(),"Paused adhan lost while notification retains focus"));}
        }finally{t.main(()->home.stopService(intent));}
        await("separate-UID service confirms focus released",15000,()->released[0]);
        await("focus gain restores owner at unity",15000,restored);
    }
    void startAdhan(){t.main(()->home.startForegroundService(new Intent(home,AdhanPlaybackService.class)));await("actual adhan owns transient priority",15000,PlaybackAudio::adhanRunning);}
    void stopAdhan(){t.main(()->home.startService(new Intent(home,AdhanPlaybackService.class).setAction(AdhanPlaybackService.STOP)));await("adhan focus relinquished",10000,()->!PlaybackAudio.adhanRunning());SystemClock.sleep(800);}
    private TextView text(View v,String label){if(v instanceof TextView&&((TextView)v).getText().toString().equals(label))return(TextView)v;if(v instanceof ViewGroup){ViewGroup group=(ViewGroup)v;for(int n=0;n<group.getChildCount();n++){TextView found=text(group.getChildAt(n),label);if(found!=null)return found;}}return null;}
    void run()throws Exception{
        AudioManager manager=home.getSystemService(AudioManager.class);int music=manager.getStreamVolume(AudioManager.STREAM_MUSIC),alarm=manager.getStreamVolume(AudioManager.STREAM_ALARM);
        t.main(()->{QuranRadio.stop();QuranRadio.prefs(home).edit().putString("list","[]").putString("url",QuranRadio.FALLBACK[0][1]).putLong("list_updated",System.currentTimeMillis()).apply();home.openLiveBroadcast();});SystemClock.sleep(500);LiveTvDialog dialog=home.liveTvDialog;t.check(dialog!=null,"Live window missing");
        java.util.List<LiveTvDubbed.Audit> audits=LiveTvDubbed.parseAudits(new String(read(home.getAssets().open("live-tv/dubbed-verified.json")),"UTF-8"));
        for(LiveTvDubbed.Audit a:audits)if(a.channel.id.equals("MBCBollywood.ae")||a.channel.id.equals("MBC3USA.us"))live(dialog,a.channel);
        LiveTvData.Channel nhk=channel("NHKWorldJapan.jp","NHK World Japan","https://masterpl.hls.nhkworld.jp/hls/w/live/smarttv.m3u8");live(dialog,nhk);
        probe(()->!dialog.engine.player.isPlaying()&&dialog.engine.audioFocus.interrupted(),()->dialog.engine.player.isPlaying()&&dialog.engine.player.getVolume()==1f&&dialog.engine.audioFocus.focused());
        t.main(()->QuranRadio.play(home));await("radio prepares",15000,()->radio()!=null);ExoPlayer radio=radio();playing(radio,"Quran Cairo");t.main(()->t.check(!dialog.engine.player.isPlaying()&&!dialog.engine.audioFocus.wantsPlay(),"Live and radio overlap"));
        probe(()->!radio.isPlaying()&&radioFocus().interrupted(),()->radio.isPlaying()&&radio.getVolume()==1f&&radioFocus().focused());
        int index=AdhanRecording.index(home);t.main(()->PrayerTimes.prefs(home).edit().putInt("adhan_recording",0).apply());startAdhan();await("radio paused for real adhan",10000,()->!radio.isPlaying());
        probe(()->PlaybackAudio.adhanRunning()&&!radio.isPlaying()&&PrayerTimes.prefs(home).getString("last_alarm_status","").contains("توقف الأذان مؤقتًا"),()->PlaybackAudio.adhanRunning()&&!radio.isPlaying()&&PrayerTimes.prefs(home).getString("last_alarm_status","").contains("استؤنف الأذان"),35000);
        // Wait for the entire shipped 246-second recording; no seek, fake completion or shortened asset.
        await("natural full adhan completion resumes radio",300000,()->!PlaybackAudio.adhanRunning()&&radio.isPlaying()&&radio.getVolume()==1f&&PrayerTimes.prefs(home).getString("last_alarm_status","").contains("اكتمل الأذان"));
        t.main(()->PrayerTimes.prefs(home).edit().putInt("adhan_recording",index).apply());playing(radio,"Quran after complete adhan");
        live(dialog,nhk);t.main(()->t.check(QuranRadio.state()==QuranRadio.IDLE&&radio()==null,"Radio not released on TV replacement"));
        startAdhan();await("TV paused during adhan",10000,()->!dialog.engine.player.isPlaying());stopAdhan();await("TV resumes unity after adhan stop path",15000,()->dialog.engine.player.isPlaying()&&dialog.engine.player.getVolume()==1f);
        startAdhan();t.main(dialog.engine::pause);stopAdhan();SystemClock.sleep(1500);t.main(()->t.check(!dialog.engine.player.getPlayWhenReady(),"User pause was undone after adhan"));
        t.key(KeyEvent.KEYCODE_MEDIA_PLAY);await("hardware Play reconnects stopped live source",20000,()->dialog.engine.player.isPlaying());t.key(KeyEvent.KEYCODE_MEDIA_PAUSE);await("hardware Pause cancels play intent",5000,()->!dialog.engine.player.getPlayWhenReady()&&!dialog.engine.audioFocus.wantsPlay());
        t.main(dialog::dismiss);SystemClock.sleep(500);t.check(dialog.engine.released,"TV decoder not released on close");
        for(int n=0;n<4;n++){t.main(home::openLiveBroadcast);SystemClock.sleep(350);LiveTvDialog opened=home.liveTvDialog;t.main(opened::dismiss);SystemClock.sleep(350);t.check(opened.engine.released,"Repeated close retained player");}
        t.main(home::showPrayerPanel);SystemClock.sleep(400);PrayerGlassDialog prayer=home.prayerGlassDialog;
        TextView countdown=(TextView)field(prayer,"next");String[] firstCountdown={""};t.main(()->firstCountdown[0]=countdown.getText().toString());SystemClock.sleep(1600);t.main(()->t.check(!firstCountdown[0].equals(countdown.getText().toString()),"Prayer countdown stopped updating"));
        t.main(()->{try{Method open=PrayerGlassDialog.class.getDeclaredMethod("openRadio");open.setAccessible(true);open.invoke(prayer);TextView station=text(prayer.getWindow().getDecorView(),QuranRadio.FALLBACK[0][0]);t.check(station!=null,"Radio station button absent");station.requestFocus();}catch(Exception e){throw new RuntimeException(e);}});t.key(KeyEvent.KEYCODE_DPAD_CENTER);await("remote OK starts Quran from prayer window",45000,()->radio()!=null&&radio().isPlaying());t.key(KeyEvent.KEYCODE_BACK);await("Back closes Quran player and releases audio",5000,()->radio()==null&&QuranRadio.state()==QuranRadio.IDLE);t.key(KeyEvent.KEYCODE_BACK);SystemClock.sleep(400);
        t.main(()->{t.check(manager.getStreamVolume(AudioManager.STREAM_MUSIC)==music,"System media volume was changed");t.check(manager.getStreamVolume(AudioManager.STREAM_ALARM)==alarm,"System alarm volume was changed");t.check(home.liveTvDialog==null&&QuranRadio.state()==QuranRadio.IDLE,"Audio/window remains after closing");});
        t.status("AUDIO_TEST_SUMMARY three real TV channels, real Quran audio, external notification focus, 35-second notification pause inside full natural adhan, switching, user-pause guard, repeated close, unchanged system volume; no microphone or physical HDMI measurement");
    }
    static byte[] read(java.io.InputStream in)throws Exception{try(java.io.InputStream stream=in){java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();byte[] data=new byte[4096];int n;while((n=stream.read(data))!=-1)out.write(data,0,n);return out.toByteArray();}}
    static void run(AudioPerfInstrumentation t,MainActivity home)throws Exception{new AudioRegressions(t,home).run();}
}
