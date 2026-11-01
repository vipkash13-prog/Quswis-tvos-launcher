package com.techgenius.tvoslauncher;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;

/** Plays a reusable CC0 adhan recording; never opens or replaces the current TV app. */
final class AdhanAudio {
    // Adam-synagda, "Beautiful adhan.ogg", Wikimedia Commons, CC0 1.0.
    // https://commons.wikimedia.org/wiki/File:Beautiful_adhan.ogg
    private static final String CC0_URL =
        "https://commons.wikimedia.org/wiki/Special:Redirect/file/Beautiful_adhan.ogg";
    private static MediaPlayer player;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private AdhanAudio() {}

    static synchronized void stop() {
        if (player != null) {
            try { player.stop(); } catch (Exception ignored) {}
            try { player.release(); } catch (Exception ignored) {}
            player = null;
        }
    }

    static void play(Context context) {
        if (!PrayerTimes.prefs(context).getBoolean("adhan_enabled", false)) return;
        String choice = PrayerTimes.prefs(context).getString("adhan_audio", "system");
        if (!"cc0".equals(choice) && !"makkah".equals(choice)) return;
        MAIN.post(() -> {
            stop();
            try {
                MediaPlayer mp = new MediaPlayer();
                player = mp;
                mp.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
                if ("makkah".equals(choice)) {
                    android.content.res.AssetFileDescriptor afd = context.getAssets().openFd("adhan_makkah.m4a");
                    try { mp.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength()); }
                    finally { afd.close(); }
                } else {
                    mp.setDataSource(CC0_URL);
                }
                mp.setOnPreparedListener(MediaPlayer::start);
                mp.setOnCompletionListener(done -> stop());
                mp.setOnErrorListener((bad, what, extra) -> { stop(); return true; });
                mp.prepareAsync();
            } catch (Exception ignored) { stop(); }
        });
    }
}
