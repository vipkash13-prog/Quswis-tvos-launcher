package com.techgenius.tvoslauncher;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import java.io.File;
import java.io.FileOutputStream;

/** Runs against the actual Activity and ScrollView on Android, not a geometry mock. */
public final class ViewportInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    static void check(boolean value, String detail) {
        if (!value) throw new AssertionError(detail);
    }
    @Override public void onStart() {
        Bundle result = new Bundle();
        MainActivity[] activity = new MainActivity[1];
        try {
            Context c=getTargetContext();
            c.getSharedPreferences("setup",Context.MODE_PRIVATE).edit().putBoolean(SetupActivity.K_DONE,true).commit();
            Scenes.setLook(c,Scenes.LOOK_CLASSIC);
            PrayerTimes.setEnabled(c,true);
            Weather.prefs(c).edit().putBoolean("enabled",true).commit();
            FootballScores.setEnabled(c,false);
            WorldNews.setEnabled(c,false);
            LiveStream.setEnabled(c,false);
            activity[0]=(MainActivity)startActivitySync(new Intent(c,MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            SystemClock.sleep(3000);
            MainActivity a=activity[0];
            runOnMainSync(() -> {
                a.apps.clear(); a.otherApps.clear();
                for(int i=0;i<42;i++) {
                    MainActivity.AppEntry app=new MainActivity.AppEntry();
                    app.key="test.app."+i;app.pkg="test.app."+i;app.label="App "+i;
                    a.apps.add(app);
                }
                a.buildGrid(); a.focusIndex(0);
                a.weatherCard.setVisibility(View.VISIBLE);
                a.prayerCard.setVisibility(View.VISIBLE);
                a.updateClassicAppClip();
            });
            waitForIdleSync(); SystemClock.sleep(600);
            runOnMainSync(() -> {
                int expected=(a.screenW-2*a.side-(MainActivity.COLS-1)*a.gap)/MainActivity.COLS;
                check(a.tileW==expected,"Original tile size was changed");
                check(a.scroll.getWidth()==a.screenW,"Original full-width grid was changed");
                check(a.scroll.getTop()>=a.prayerCard.getBottom(),"Viewport starts behind prayer widget");
                int dockY=a.scroll.getTop()+a.scroll.getPaddingTop()+a.shelfView.getHeight();
                check(dockY==a.shelfH,"Original resting dock position was changed");
            });
            // Walk through each row in a long app list, including the last row and the return trip.
            int[] targets={6,12,18,24,30,36,30,18,6,0};
            for(int index:targets) {
                runOnMainSync(() -> a.focusIndex(index));
                waitForIdleSync(); SystemClock.sleep(450);
                runOnMainSync(() -> {
                    check(a.scroll.getTop()>=a.classicHeaderBottom(),"Scroll viewport crossed header");
                    View focused=a.tiles.get(index);
                    int[] position=new int[2], viewport=new int[2];
                    focused.getLocationOnScreen(position); a.scroll.getLocationOnScreen(viewport);
                    check(position[1]>=viewport[1]-2,"Focused tile is clipped behind widgets at index "+index);
                    check(position[1]+focused.getHeight()<=viewport[1]+a.scroll.getHeight()+2,
                            "Focused tile exceeds viewport at index "+index);
                });
            }
            runOnMainSync(() -> {
                a.focusIndex(24);
            });
            SystemClock.sleep(500);waitForIdleSync();
            runOnMainSync(() -> {
                try {
                    Bitmap image=Bitmap.createBitmap(a.root.getWidth(),a.root.getHeight(),Bitmap.Config.ARGB_8888);
                    a.root.draw(new Canvas(image));
                    try(FileOutputStream out=new FileOutputStream(new File(a.getExternalFilesDir(null),"viewport.png"))) {
                        image.compress(Bitmap.CompressFormat.PNG,100,out);
                    }
                    image.recycle();
                } catch(Exception e) { throw new RuntimeException(e); }
            });
            result.putString("stream","VIEWPORT_TEST_PASSED: real Android layout, original sizes, all rows and screenshot\n");
            finish(Activity.RESULT_OK,result);
        } catch(Throwable error) {
            result.putString("stream","VIEWPORT_TEST_FAILED: "+android.util.Log.getStackTraceString(error));
            finish(Activity.RESULT_CANCELED,result);
        }
    }
}
