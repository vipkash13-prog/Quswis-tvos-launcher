package com.techgenius.tvoslauncher;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
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
    private static MediaPlayer player;
    private static int state = IDLE;
    private static Runnable listener;
    private static AudioManager audio;
    private static final AudioManager.OnAudioFocusChangeListener FOCUS = change -> {
        if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) stop();
    };

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
                    if (keep.length() > 0) prefs(c).edit().putString("list", keep.toString()).apply();
                } finally {
                    conn.disconnect();
                }
            } catch (Exception ignored) {
                // keep the saved or built-in list
            }
            if (done != null) MAIN.post(done);
        }).start();
    }

    static void toggle(Context ctx) {
        if (state == IDLE) play(ctx);
        else stop();
    }

    static synchronized void play(Context ctx) {
        final Context c = ctx.getApplicationContext();
        stop();
        final String[] station = current(c);
        try {
            audio = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
            if (audio != null) audio.requestAudioFocus(FOCUS, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
            MediaPlayer mp = new MediaPlayer();
            player = mp;
            mp.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
            mp.setDataSource(station[1]);
            mp.setOnPreparedListener(p -> {
                if (player != p) return;
                p.start();
                notifyState(PLAYING);
            });
            mp.setOnErrorListener((p, what, extra) -> {
                if (player == p) {
                    stop();
                    MAIN.post(() -> Toast.makeText(c, "تعذّر تشغيل الإذاعة. جرّب محطة أخرى من الإعدادات.", Toast.LENGTH_LONG).show());
                }
                return true;
            });
            mp.setOnCompletionListener(p -> {
                if (player == p) stop();
            });
            notifyState(LOADING);
            mp.prepareAsync();
        } catch (Exception e) {
            stop();
            Toast.makeText(c, "تعذّر تشغيل الإذاعة.", Toast.LENGTH_LONG).show();
        }
    }

    static synchronized void stop() {
        MediaPlayer mp = player;
        player = null;
        if (mp != null) {
            try {
                mp.reset();
                mp.release();
            } catch (Exception ignored) {
                // already released
            }
        }
        if (audio != null) audio.abandonAudioFocus(FOCUS);
        notifyState(IDLE);
    }
}
