package com.techgenius.tvoslauncher;

import android.app.Activity;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.VideoView;

/** In-app live IPTV player with bounded HLS/TS fallback. */
public final class XtreamPlayerActivity extends Activity {
    private VideoView video;
    private TextView message;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private String hlsUrl, tsUrl, channelName;
    private boolean usingTs, prepared, stopped;
    private int firstWhat, firstExtra;
    private final Runnable startupTimeout = () -> {
        if (!prepared && !stopped) fail(-1001, 0);
    };

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(Color.BLACK);
        video = new VideoView(this);
        frame.addView(video, new FrameLayout.LayoutParams(-1, -1));
        message = new TextView(this);
        message.setTextColor(Color.WHITE);
        message.setTextSize(19);
        message.setGravity(Gravity.CENTER);
        message.setPadding(24, 24, 24, 24);
        frame.addView(message, new FrameLayout.LayoutParams(-1, -1));
        setContentView(frame);
        hlsUrl = getIntent().getStringExtra("stream_url");
        tsUrl = getIntent().getStringExtra("stream_url_ts");
        channelName = getIntent().getStringExtra("channel_name");
        if (hlsUrl == null || hlsUrl.isEmpty()) {
            message.setText("رابط القناة غير متوفر");
            return;
        }
        startStream(false);
    }

    private void startStream(boolean ts) {
        handler.removeCallbacks(startupTimeout);
        usingTs = ts;
        prepared = false;
        String url = ts ? tsUrl : hlsUrl;
        if (url == null || url.isEmpty()) {
            showFailure(-1002, 0);
            return;
        }
        video.stopPlayback();
        message.setText("جاري تشغيل " + (channelName == null ? "القناة" : channelName)
                + (ts ? " (TS)..." : " (HLS)..."));
        message.setVisibility(View.VISIBLE);
        video.setOnPreparedListener(mp -> {
            prepared = true;
            handler.removeCallbacks(startupTimeout);
            message.setVisibility(View.GONE);
            mp.setOnInfoListener((player, what, extra) -> {
                if (what == MediaPlayer.MEDIA_INFO_BUFFERING_START) {
                    message.setText("جاري التحميل...");
                    message.setVisibility(View.VISIBLE);
                } else if (what == MediaPlayer.MEDIA_INFO_BUFFERING_END) {
                    message.setVisibility(View.GONE);
                }
                return false;
            });
        });
        video.setOnErrorListener((mp, what, extra) -> {
            fail(what, extra);
            return true;
        });
        try {
            video.setVideoURI(Uri.parse(url));
            video.start();
            handler.postDelayed(startupTimeout, 20000);
        } catch (Exception e) {
            fail(-1003, 0);
        }
    }

    private void fail(int what, int extra) {
        if (stopped) return;
        handler.removeCallbacks(startupTimeout);
        if (!usingTs && tsUrl != null && !tsUrl.isEmpty()) {
            firstWhat = what;
            firstExtra = extra;
            startStream(true);
        } else {
            showFailure(what, extra);
        }
    }

    private void showFailure(int what, int extra) {
        video.stopPlayback();
        message.setText("تعذر تشغيل البث بصيغتي HLS وTS. "
                + "رمز HLS: " + firstWhat + "/" + firstExtra
                + "، رمز TS: " + what + "/" + extra
                + ". تحقق من صلاحية البث أو توافق ترميز القناة.");
        message.setVisibility(View.VISIBLE);
    }

    @Override protected void onPause() {
        super.onPause();
        handler.removeCallbacks(startupTimeout);
        if (video != null) video.pause();
    }
    @Override protected void onResume() {
        super.onResume();
        if (video != null && !stopped && prepared && !video.isPlaying()) video.start();
    }
    @Override protected void onDestroy() {
        stopped = true;
        handler.removeCallbacks(startupTimeout);
        if (video != null) video.stopPlayback();
        super.onDestroy();
    }
}
