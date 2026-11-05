package com.techgenius.tvoslauncher;

import android.app.Activity;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.VideoView;

/** In-app live stream player. Back returns to the IPTV channel list. */
public final class XtreamPlayerActivity extends Activity {
    private VideoView video;
    private TextView message;
    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        FrameLayout frame=new FrameLayout(this);
        frame.setBackgroundColor(Color.BLACK);
        video=new VideoView(this);
        frame.addView(video,new FrameLayout.LayoutParams(-1,-1));
        message=new TextView(this);
        message.setTextColor(Color.WHITE);message.setTextSize(19);
        message.setGravity(Gravity.CENTER);message.setPadding(24,24,24,24);
        frame.addView(message,new FrameLayout.LayoutParams(-1,-1));
        setContentView(frame);
        String url=getIntent().getStringExtra("stream_url");
        if(url==null||url.isEmpty()){message.setText("رابط القناة غير متوفر");return;}
        message.setText("جاري تشغيل "+getIntent().getStringExtra("channel_name")+"...");
        video.setOnPreparedListener(mp->{
            message.setVisibility(View.GONE);
            mp.setOnInfoListener((player,what,extra)->{
                if(what==MediaPlayer.MEDIA_INFO_BUFFERING_START){message.setText("جاري التحميل...");message.setVisibility(View.VISIBLE);}
                if(what==MediaPlayer.MEDIA_INFO_BUFFERING_END){message.setVisibility(View.GONE);}
                return false;
            });
        });
        video.setOnErrorListener((mp,what,extra)->{
            message.setText("تعذر تشغيل البث. قد لا يدعم مشغل الجهاز ترميز هذه القناة.");
            message.setVisibility(View.VISIBLE);
            return true;
        });
        try{video.setVideoURI(Uri.parse(url));video.start();}
        catch(Exception e){message.setText("تعذر بدء تشغيل القناة");}
    }
    @Override protected void onPause(){super.onPause();if(video!=null)video.pause();}
    @Override protected void onResume(){super.onResume();if(video!=null&&!video.isPlaying())video.start();}
    @Override protected void onDestroy(){if(video!=null)video.stopPlayback();super.onDestroy();}
}
