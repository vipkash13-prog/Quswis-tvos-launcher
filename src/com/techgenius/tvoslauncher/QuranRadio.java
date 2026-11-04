package com.techgenius.tvoslauncher;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioManager;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.common.*;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Quran radio for the Home shortcut: a stream player (no key) with a station list from the public
 * mp3quran.net radios service, a built-in fallback list, and an optional custom stream link.
 */
final class QuranRadio {
    static final String[][] FALLBACK = {
            {"إذاعة القرآن الكريم من القاهرة", "https://stream.radiojar.com/8s5u5tpdtwzuv"},
            {"إذاعة مشاري العفاسي", "https://backup.qurango.net/radio/mishary_alafasi"},
            {"إذاعة عبدالباسط عبدالصمد (مجوّد)", "https://backup.qurango.net/radio/abdulbasit_abdulsamad_mojawwad"},
            {"إذاعة التراتيل", "https://backup.qurango.net/radio/tarateel"}};
    private static final String API = "https://www.mp3quran.net/api/v3/radios?language=ar";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    static final int IDLE = 0, LOADING = 1, PLAYING = 2;
    private static ExoPlayer player;
    private static PlaybackAudio audioFocus;
    private static int session;
    private static boolean lifecycleRegistered;
    private static java.lang.ref.WeakReference<android.app.Activity> owner=new java.lang.ref.WeakReference<>(null);
    static int session(){return session;}
    static void stopOwned(int value){if(session==value)stop();}
    private static void observeOwner(Context context){
        owner=new java.lang.ref.WeakReference<>(context instanceof android.app.Activity?(android.app.Activity)context:null);
        if(lifecycleRegistered)return;lifecycleRegistered=true;
        ((android.app.Application)context.getApplicationContext()).registerActivityLifecycleCallbacks(new android.app.Application.ActivityLifecycleCallbacks(){
            public void onActivityCreated(android.app.Activity a,android.os.Bundle b){}public void onActivityStarted(android.app.Activity a){}public void onActivityResumed(android.app.Activity a){}public void onActivityPaused(android.app.Activity a){}
            public void onActivityStopped(android.app.Activity a){if(owner.get()==a)stop();}
            public void onActivitySaveInstanceState(android.app.Activity a,android.os.Bundle b){}
            public void onActivityDestroyed(android.app.Activity a){if(owner.get()==a)stop();}
        });
    }
    private static int state = IDLE;
    private static Runnable listener;
    private static final java.util.concurrent.atomic.AtomicBoolean refreshing=new java.util.concurrent.atomic.AtomicBoolean();
    private QuranRadio() {
    }

    static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("quranradio", Context.MODE_PRIVATE);
    }

    static boolean enabled(Context c) {
        return prefs(c).getBoolean("enabled", false);
    }

    static void setEnabled(Context c, boolean on) {
        prefs(c).edit().putBoolean("enabled", on).apply();
        if (!on) stop();
    }

    static int state() {
        return state;
    }

    /** The Home pill updates itself from this; pass null to detach. */
    static void setListener(Runnable r) {
        listener = r;
    }

    private static void notifyState(int s) {
        state = s;
        if (listener != null) MAIN.post(listener);
    }

    /** Names and stream links: the service's list when it was loaded, otherwise the built-in one, then the custom link. */
    static List<String[]> stations(Context c) {
        List<String[]> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs(c).getString("list", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                out.add(new String[]{o.getString("name"), o.getString("url")});
            }
        } catch (Exception ignored) {
            out.clear();
        }
        if (out.isEmpty()) for (String[] s : FALLBACK) out.add(s);
        String custom = prefs(c).getString("custom", "");
        if (!custom.isEmpty()) out.add(new String[]{"رابط مخصص", custom});
        return out;
    }

    static String[] current(Context c) {
        List<String[]> all = stations(c);
        String url = prefs(c).getString("url", "");
        for (String[] s : all) if (s[1].equals(url)) return s;
        return all.get(0);
    }

    static void choose(Context c, String url) {
        boolean was = state != IDLE;
        prefs(c).edit().putString("url", url).apply();
        if (was) {
            stop();
            play(c);
        }
    }

    static void setCustom(Context c, String url) {
        prefs(c).edit().putString("custom", url.trim()).apply();
        if (!url.trim().isEmpty()) choose(c, url.trim());
    }

    /** Loads the station list in the background; {@code done} runs on the main thread. */
    static void refreshStations(Context ctx, Runnable done) {
        Context c = ctx.getApplicationContext();
        if(!refreshing.compareAndSet(false,true)){if(done!=null)MAIN.post(done);return;}
        new Thread(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(API).openConnection();
                conn.setConnectTimeout(6000);
                conn.setReadTimeout(6000);
                try (InputStream in = conn.getInputStream()) {
                    java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                    byte[] buf = new byte[4096];
                    for (int n; (n = in.read(buf)) > 0; ) bos.write(buf, 0, n);
                    JSONArray radios = new JSONObject(bos.toString("UTF-8")).getJSONArray("radios");
                    JSONArray keep = new JSONArray();
                    for (int i = 0; i < radios.length() && keep.length() < 40; i++) {
                        JSONObject r = radios.getJSONObject(i);
                        String url = r.optString("url");
                        String name = r.optString("name").trim();
                        if (url.startsWith("http") && !name.isEmpty()) {
                            keep.put(new JSONObject().put("name", name).put("url", url));
                        }
                    }
                    if (keep.length() > 0) prefs(c).edit().putString("list", keep.toString()).putLong("list_updated",System.currentTimeMillis()).apply();
                } finally {
                    conn.disconnect();
                }
            } catch (Exception ignored) {
                // keep the saved or built-in list
            }
            refreshing.set(false);
            if (done != null) MAIN.post(done);
        },"quran-stations").start();
    }

    static void refreshStationsIfStale(Context c){if(System.currentTimeMillis()-prefs(c).getLong("list_updated",0)>86400000L)refreshStations(c,null);}

    static void toggle(Context ctx) {
        if (state == IDLE) play(ctx);
        else stop();
    }

    static synchronized void play(Context ctx) {
        if (Looper.myLooper() != Looper.getMainLooper()) { MAIN.post(() -> play(ctx)); return; }
        final Context c = ctx.getApplicationContext();
        stop();session++;observeOwner(ctx);
        final String[] station = current(c);
        try {
            DefaultLoadControl buffer = new DefaultLoadControl.Builder().setBufferDurationsMs(1000,8000,500,1000).setTargetBufferBytes(2*1024*1024).build();
            ExoPlayer mp = new ExoPlayer.Builder(c).setLoadControl(buffer).build();
            player = mp;
            mp.setAudioAttributes(new androidx.media3.common.AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(),false);
            mp.setHandleAudioBecomingNoisy(true);
            mp.setVolume(1.0f);
            audioFocus = new PlaybackAudio(c, AudioAttributes.CONTENT_TYPE_SPEECH,new PlaybackAudio.Client(){
                public void pauseForFocus(){if(player==mp){mp.pause();if(PlaybackAudio.adhanRunning())mp.stop();}}
                public void resumeAtUnity(){if(player==mp){mp.setVolume(1.0f);if(mp.getPlaybackState()==Player.STATE_IDLE){mp.seekToDefaultPosition();mp.prepare();}mp.play();}}
                public void replaced(){if(player==mp)stop();}
            });
            mp.addListener(new Player.Listener(){
                @Override public void onPlayWhenReadyChanged(boolean ready,int reason){if(player==mp&&!ready&&reason==Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY&&audioFocus!=null)audioFocus.pauseIntent();}
                @Override public void onIsPlayingChanged(boolean active){if(player==mp)notifyState(active?PLAYING:LOADING);}
                @Override public void onPlaybackStateChanged(int value){if(player==mp&&value==Player.STATE_ENDED)stop();}
                @Override public void onPlayerError(PlaybackException error){if(player!=mp)return;android.util.Log.w("QuswisAudio","Radio source error "+error.errorCode,error);stop();Toast.makeText(c,"تعذّر تشغيل الإذاعة. جرّب محطة أخرى من الإعدادات.",Toast.LENGTH_LONG).show();}
            });
            notifyState(LOADING);
            // Radiojar redirects the saved HTTPS entry to an HTTP audio/mpeg edge.
            // Preserve the chosen station URL and follow its public stream redirect.
            androidx.media3.datasource.DefaultHttpDataSource.Factory http=new androidx.media3.datasource.DefaultHttpDataSource.Factory().setUserAgent("Quswis Quran Radio").setConnectTimeoutMs(10000).setReadTimeoutMs(15000).setAllowCrossProtocolRedirects(true);
            mp.setMediaSource(new androidx.media3.exoplayer.source.DefaultMediaSourceFactory(http).createMediaSource(MediaItem.fromUri(station[1])));mp.prepare();
            if(audioFocus.playIntent())mp.play();
        } catch (Exception error) {
            stop();Toast.makeText(c,"تعذّر تشغيل الإذاعة.",Toast.LENGTH_LONG).show();
        }
    }

    static synchronized void stop() {
        if (Looper.myLooper() != Looper.getMainLooper()) { MAIN.post(QuranRadio::stop); return; }
        ExoPlayer old=player;player=null;owner.clear();
        if(audioFocus!=null){audioFocus.release();audioFocus=null;}
        if(old!=null)old.release();
        notifyState(IDLE);
    }
}
