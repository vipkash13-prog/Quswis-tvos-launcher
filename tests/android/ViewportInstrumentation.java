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
    private void ui(Runnable action) {
        final Throwable[] failure=new Throwable[1];
        runOnMainSync(() -> {
            try { action.run(); } catch(Throwable error) { failure[0]=error; }
        });
        if(failure[0]!=null) throw new RuntimeException(failure[0]);
    }
    @Override public void onStart() {
        Bundle result = new Bundle();
        MainActivity[] activity = new MainActivity[1];
        try {
            Context c=getTargetContext();
            c.getSharedPreferences("setup",Context.MODE_PRIVATE).edit().putBoolean(SetupActivity.K_DONE,true).commit();
            Profiles.homePrefs(c).edit().putBoolean("asked_default_home",true).commit();
            AppUpdater.setAutoUpdateEnabled(c,false);
            Scenes.setLook(c,Scenes.LOOK_CLASSIC);
            PrayerTimes.setEnabled(c,true);
            Weather.prefs(c).edit().putBoolean("enabled",true).commit();
            FootballScores.setEnabled(c,false);
            WorldNews.setEnabled(c,false);
            LiveStream.setEnabled(c,false);
            activity[0]=(MainActivity)startActivitySync(new Intent(c,MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            SystemClock.sleep(3000);
            // Inject through Android's test automation identity, not the app's permissions.
            try (android.os.ParcelFileDescriptor command=getUiAutomation().executeShellCommand("wm dismiss-keyguard");
                 java.io.InputStream input=new android.os.ParcelFileDescriptor.AutoCloseInputStream(command)) {
                byte[] buffer=new byte[1024];
                while(input.read(buffer)!=-1) { }
            }
            long time=SystemClock.uptimeMillis();
            getUiAutomation().injectInputEvent(new android.view.KeyEvent(time,time,
                    android.view.KeyEvent.ACTION_DOWN,android.view.KeyEvent.KEYCODE_DPAD_DOWN,0),true);
            getUiAutomation().injectInputEvent(new android.view.KeyEvent(time,time+10,
                    android.view.KeyEvent.ACTION_UP,android.view.KeyEvent.KEYCODE_DPAD_DOWN,0),true);
            MainActivity a=activity[0];
            ui(() -> {
                a.apps.clear(); a.otherApps.clear();
                for(int i=0;i<42;i++) {
                    MainActivity.AppEntry app=new MainActivity.AppEntry();
                    app.key="test.app."+i;app.pkg="test.app."+i;app.label="App "+i;
                    Bitmap art=Bitmap.createBitmap(240,144,Bitmap.Config.ARGB_8888);
                    Canvas canvas=new Canvas(art);
                    canvas.drawColor(android.graphics.Color.HSVToColor(new float[]{i*37%360,0.65f,0.75f}));
                    android.graphics.Paint paint=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
                    paint.setColor(android.graphics.Color.WHITE);paint.setTextSize(32);
                    canvas.drawText(app.label,25,85,paint);
                    app.banner=new android.graphics.drawable.BitmapDrawable(a.getResources(),art);
                    a.apps.add(app);
                }
                a.buildGrid(); a.focusIndex(0);
                a.weatherCard.setVisibility(View.VISIBLE);
                a.prayerCard.setVisibility(View.VISIBLE);
                a.updateClassicAppClip();
            });
            waitForIdleSync(); SystemClock.sleep(600);
            ui(() -> {
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
                ui(() -> a.focusIndex(index));
                waitForIdleSync(); SystemClock.sleep(450);
                ui(() -> {
                    check(a.scroll.getTop()>=a.classicHeaderBottom(),"Scroll viewport crossed header");
                    View focused=a.tiles.get(index);
                    check(a.hasWindowFocus(),"Another window is covering the launcher");
                    check(focused.isFocused(),"DPAD focus did not reach tile "+index);
                    check(a.scroll.isShown() && a.scroll.getAlpha()>0.9f,"App viewport is hidden");
                    int[] position=new int[2], viewport=new int[2];
                    focused.getLocationOnScreen(position); a.scroll.getLocationOnScreen(viewport);
                    check(position[1]>=viewport[1]-2,"Focused tile is clipped behind widgets at index "+index);
                    check(position[1]+focused.getHeight()<=viewport[1]+a.scroll.getHeight()+2,
                            "Focused tile exceeds viewport at index "+index+" tileY="+position[1]
                            +" tileH="+focused.getHeight()+" viewportY="+viewport[1]+" viewportH="+a.scroll.getHeight()
                            +" scrollY="+a.scroll.getScrollY()+" target="+a.gridScrollTarget);
                });
            }
            ui(() -> {
                a.focusIndex(24);
            });
            SystemClock.sleep(500);waitForIdleSync();
            Bitmap image=getUiAutomation().takeScreenshot();
            check(image!=null,"Surface screenshot could not be captured");
            try(FileOutputStream out=new FileOutputStream(new File(a.getExternalFilesDir(null),"viewport.png"))) {
                image.compress(Bitmap.CompressFormat.PNG,100,out);
            }
            image.recycle();
            result.putString("stream","VIEWPORT_TEST_PASSED: real Android layout, original sizes, all rows and screenshot\n");
            finish(Activity.RESULT_OK,result);
        } catch(Throwable error) {
            result.putString("stream","VIEWPORT_TEST_FAILED: "+android.util.Log.getStackTraceString(error));
            finish(Activity.RESULT_CANCELED,result);
        }
    }
}
