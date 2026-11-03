package com.techgenius.tvoslauncher;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import java.io.File;
import java.io.FileOutputStream;
import java.util.HashSet;

/** Framework-level smoke test for the reported remote focus regression. */
public final class PrayerWindowInstrumentation extends Instrumentation {
    private MainActivity host;
    private PrayerGlassDialog dialog;
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }
    private void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private int focusedId() {
        int[] id = {-1}; runOnMainSync(() -> { if (dialog.getCurrentFocus() != null) id[0]=dialog.getCurrentFocus().getId(); }); return id[0];
    }
    private void direction(int key) {
        int first=focusedId(); HashSet<Integer> visited=new HashSet<>();
        for(int i=0;i<4;i++) {
            visited.add(focusedId()); sendKeyDownUpSync(key); waitForIdleSync();
        }
        check(visited.size()==4,"All four shortcuts must be reachable, key="+key);
        check(focusedId()==first,"DPAD must wrap back to its start");
    }
    @Override public void onStart() {
        Bundle result=new Bundle();
        try {
            getTargetContext().getSharedPreferences("setup",0).edit().putBoolean("setup_done",true).commit();
            Profiles.homePrefs(getTargetContext()).edit().putBoolean("asked_default_home",true).commit();
            PrayerTimes.setEnabled(getTargetContext(),true);
            Intent launch=new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            host=(MainActivity)startActivitySync(launch); waitForIdleSync();
            runOnMainSync(() -> { host.showPrayerPanel(); dialog=host.prayerGlassDialog; });
            SystemClock.sleep(600); waitForIdleSync();
            check(dialog!=null&&dialog.isShowing(),"Prayer window did not open");
            check(host.quranShortcut==null,"Duplicate Home Quran lens still exists");
            direction(KeyEvent.KEYCODE_DPAD_RIGHT); direction(KeyEvent.KEYCODE_DPAD_LEFT);
            Bitmap screenshot=getUiAutomation().takeScreenshot();
            check(screenshot!=null,"No screenshot");
            File file=new File(getTargetContext().getExternalFilesDir(null),"prayer-window-1080.png");
            try(FileOutputStream output=new FileOutputStream(file)){screenshot.compress(Bitmap.CompressFormat.PNG,100,output);} screenshot.recycle();
            sendKeyDownUpSync(KeyEvent.KEYCODE_BACK); SystemClock.sleep(400); waitForIdleSync();
            check(!dialog.isShowing(),"BACK did not close the window");
            runOnMainSync(() -> check(host.getCurrentFocus()==host.prayerCard,"BACK did not restore prayer lens focus"));
            result.putString("stream","PRAYER_UI_OK: both DPAD directions, wraparound, Home lens removal and BACK focus passed\n");
            finish(Activity.RESULT_OK,result);
        }catch(Throwable error){result.putString("stream","PRAYER_UI_FAILED: "+error+"\n");finish(Activity.RESULT_CANCELED,result);}
    }
}
