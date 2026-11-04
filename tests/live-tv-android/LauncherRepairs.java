package com.techgenius.tvoslauncher;

import android.app.*;import android.content.*;import android.graphics.*;import android.os.*;import android.view.*;import java.io.*;import java.lang.reflect.*;import java.util.*;

/** Exercises real expansion/Back and real rendering; no production focus or input delays are changed. */
final class LauncherRepairs {
    final Instrumentation test; final MainActivity home; final Bundle output;
    LauncherRepairs(Instrumentation test, MainActivity home, Bundle output) { this.test=test;this.home=home;this.output=output; }
    void check(boolean value,String why) { if(!value)throw new AssertionError(why); }
    void main(Runnable task) { Throwable[] error={null};test.runOnMainSync(()->{try{task.run();}catch(Throwable e){error[0]=e;}});if(error[0]!=null)throw new AssertionError(error[0]); }
    void key(int code) {test.sendKeyDownUpSync(code);test.waitForIdleSync();}
    void status(String s) {Bundle b=new Bundle();b.putString("stream",s+"\n");test.sendStatus(1,b);}
    View[] pills(){return new View[]{home.prayerCard,home.footballShortcut,home.newsShortcut,home.liveShortcut};}
    int[] rect(View v){int[] p=new int[2];v.getLocationOnScreen(p);return new int[]{p[0],p[1],v.getWidth(),v.getHeight()};}
    void visible(boolean show){main(()->{for(View v:pills())check(v.getVisibility()==(show?View.VISIBLE:View.GONE),"Lens visibility disagrees with expanded state: "+v);});}
    void capture(String name)throws Exception {Bitmap b=test.getUiAutomation().takeScreenshot();check(b!=null,"No native screenshot");try(FileOutputStream out=new FileOutputStream(new File(home.getExternalFilesDir(null),name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,out);}Bitmap small=Bitmap.createScaledBitmap(b,960,540,true);ByteArrayOutputStream data=new ByteArrayOutputStream();small.compress(Bitmap.CompressFormat.JPEG,80,data);output.putString("preview_"+name,android.util.Base64.encodeToString(data.toByteArray(),android.util.Base64.NO_WRAP));small.recycle();b.recycle();}
    void awaitScene() {long end=SystemClock.elapsedRealtime()+15000;boolean[] ready={false};while(SystemClock.elapsedRealtime()<end&&!ready[0]){main(()->ready[0]=home.sceneHome.sceneView.getCurrentBgBitmap()!=null);if(!ready[0])SystemClock.sleep(50);}check(ready[0],"Asynchronous scene did not load");test.waitForIdleSync();SystemClock.sleep(300);}
    void burst(String name,Runnable focus)throws Exception {
        main(focus);long[] ns=new long[80];main(()->{for(int n=0;n<ns.length;n++){int code=n%2==0?KeyEvent.KEYCODE_DPAD_RIGHT:KeyEvent.KEYCODE_DPAD_LEFT;long now=SystemClock.uptimeMillis(),start=System.nanoTime();home.dispatchKeyEvent(new KeyEvent(now,now,KeyEvent.ACTION_DOWN,code,0));home.dispatchKeyEvent(new KeyEvent(now,now,KeyEvent.ACTION_UP,code,0));ns[n]=System.nanoTime()-start;}});Arrays.sort(ns);status("REMOTE_DISPATCH "+name+" p50_us="+ns[40]/1000+" p95_us="+ns[76]/1000+" max_us="+ns[79]/1000);
        for(int n=0;n<12;n++)key(n%2==0?KeyEvent.KEYCODE_DPAD_RIGHT:KeyEvent.KEYCODE_DPAD_LEFT);
    }
    void verify(boolean persisted)throws Exception {
        Context app=test.getTargetContext();android.content.SharedPreferences settings=app.getSharedPreferences("settings",0);
        if(persisted){check(Scenes.getEnabledSceneIds(app).contains("uhd_night-lake"),"New scene choice lost across process restart");check(Profiles.homePrefs(app).getString("wallpaper","").equals("photo:uhd/morocco-atlas.webp"),"Classic wallpaper choice lost across process restart");}
        main(()->{Scenes.setLook(home,Scenes.LOOK_SCENES);home.applyLookMode();});awaitScene();visible(true);
        int[][] positions=new int[4][];main(()->{View[] vs=pills();for(int n=0;n<4;n++)positions[n]=rect(vs[n]);});
        burst("night-dock",()->home.sceneHome.focusDock(0));visible(true);
        main(()->home.sceneHome.focusDock(home.sceneHome.dockTiles.size()-1));key(KeyEvent.KEYCODE_DPAD_CENTER);check(home.sceneHome.moreExpanded,"Real OK did not expand app grid");visible(false);
        burst("night-all-apps",()->home.sceneHome.focusDock(0));visible(false);capture("night-app-grid");
        // Lifecycle return must honor expansion, rather than a focus heuristic.
        SettingsActivity settingsPage=(SettingsActivity)test.startActivitySync(new Intent(app,SettingsActivity.class).putExtra("page","Look and scenes").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));test.waitForIdleSync();SystemClock.sleep(300);
        long[] settingsNs=new long[24];main(()->{for(int n=0;n<24;n++){long now=SystemClock.uptimeMillis(),start=System.nanoTime();int code=n%2==0?KeyEvent.KEYCODE_DPAD_DOWN:KeyEvent.KEYCODE_DPAD_UP;settingsPage.dispatchKeyEvent(new KeyEvent(now,now,0,code,0));settingsPage.dispatchKeyEvent(new KeyEvent(now,now,1,code,0));settingsNs[n]=System.nanoTime()-start;}});Arrays.sort(settingsNs);status("REMOTE_DISPATCH settings p95_us="+settingsNs[22]/1000);
        main(settingsPage::finish);test.waitForIdleSync();SystemClock.sleep(500);visible(false);check(home.sceneHome.moreExpanded,"Expanded state lost on settings return");
        key(KeyEvent.KEYCODE_BACK);SystemClock.sleep(200);check(!home.sceneHome.moreExpanded,"BACK did not collapse app grid");visible(true);main(()->{View[] vs=pills();for(int n=0;n<4;n++)check(Arrays.equals(positions[n],rect(vs[n])),"Lens geometry changed after grid close");});capture("night-home-restored");
        burst("night-lenses",()->home.prayerCard.requestFocus());visible(true);
        main(()->{Scenes.setLook(home,Scenes.LOOK_CLASSIC);home.applyLookMode();});test.waitForIdleSync();SystemClock.sleep(600);check(!home.nightAppsOpen(),"Classic treated as expanded Night Scenes");visible(true);burst("classic-grid",()->home.focusIndex(0));capture("classic-unchanged");
        // All old entries are still in place; every new export has real UHD dimensions.
        check(Scenes.ALL.length==15&&Scenes.CLASSIC_PHOTOS.length==13,"Old scene/photo entries removed");
        check(Scenes.ALL[0].id.equals("night")&&Scenes.CLASSIC_PHOTOS[0][0].equals("tessellated-pavement.jpg"),"Original order changed");
        for(String[] item:WallpaperPack.ITEMS){BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;try(InputStream stream=app.getAssets().open(WallpaperPack.asset(item[0]))){BitmapFactory.decodeStream(stream,null,options);}check(options.outWidth==3840&&options.outHeight==2160,"Not 3840x2160: "+item[0]);}
        final android.graphics.drawable.Drawable oldWallpaper=home.wallView.getDrawable();String wallpaper="photo:uhd/morocco-atlas.webp";main(()->{Profiles.homePrefs(home).edit().putString("wallpaper",wallpaper).apply();home.setWallpaper(wallpaper);});long until=SystemClock.elapsedRealtime()+15000;boolean[] wp={false};while(!wp[0]&&SystemClock.elapsedRealtime()<until){main(()->{if(home.wallView.getDrawable()!=oldWallpaper&&home.wallView.getDrawable() instanceof android.graphics.drawable.BitmapDrawable){Bitmap b=((android.graphics.drawable.BitmapDrawable)home.wallView.getDrawable()).getBitmap();wp[0]=b!=null&&b.getWidth()==(home.screenW>=3840?3840:1920);}});SystemClock.sleep(100);}check(wp[0],"Display-sized Classic UHD decode failed");check(Profiles.homePrefs(home).getString("wallpaper","").equals(wallpaper),"Classic wallpaper choice not saved");
        main(()->{settings.edit().putString("scene_enabled","uhd_night-lake").putString("prefer_scene_id","uhd_night-lake").apply();Scenes.setLook(home,Scenes.LOOK_SCENES);home.applyLookMode();});awaitScene();main(()->check(home.sceneHome.sceneView.getCurrentBgBitmap().getWidth()==(home.screenW>=3840?3840:1920),"Scene was not decoded for actual display"));capture("night-uhd-wallpaper");
        // Warm cached frame, then make repeated paint calls with disk-read detection.
        int[] reads={0};main(()->{Bitmap frame=Bitmap.createBitmap(home.screenW,home.screenH,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(frame);home.sceneHome.sceneView.draw(canvas);StrictMode.ThreadPolicy old=StrictMode.getThreadPolicy();StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder().detectDiskReads().penaltyListener(Runnable::run,v->reads[0]++).build());try{for(int n=0;n<20;n++)home.sceneHome.sceneView.draw(canvas);}finally{StrictMode.setThreadPolicy(old);frame.recycle();}});check(reads[0]==0,"Steady-state scene draw performed disk IO");
        long[] cost=new long[2];main(()->{Ui.unit(home);long start=System.nanoTime();for(int n=0;n<1000;n++)Ui.unit(home);cost[0]=System.nanoTime()-start;start=System.nanoTime();for(int n=0;n<1000;n++)Ui.screen(home);cost[1]=System.nanoTime()-start;});status("DISPLAY_METRICS 1000 cached_us="+cost[0]/1000+" original_query_us="+cost[1]/1000);
        status("LAUNCHER_REPAIRS_OK "+home.screenW+"x"+home.screenH+" expansion/lifecycle/Back/Classic/8 UHD assets/zero steady draw disk reads; persisted="+persisted);
        main(()->home.liveShortcut.requestFocus());
    }
}
