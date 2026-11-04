package com.techgenius.tvoslauncher;

import android.app.*;import android.content.*;import android.graphics.*;import android.os.*;import android.view.*;import java.io.*;import java.lang.reflect.*;import java.util.*;import org.json.*;

/** Same input script and sampler run against both APKs in one emulator; no production profiling code. */
public final class AudioPerfInstrumentation {
    final Instrumentation instrumentation;
    AudioPerfInstrumentation(Instrumentation instrumentation){this.instrumentation=instrumentation;}
    Context getContext(){return instrumentation.getContext();}
    Context getTargetContext(){return instrumentation.getTargetContext();}
    void waitForIdleSync(){instrumentation.waitForIdleSync();}
    android.app.Activity startActivitySync(Intent i){return instrumentation.startActivitySync(i);}
    private Bundle args; private MainActivity home; private volatile boolean sampling; private volatile String phase="idle";
    private final Map<String,List<Long>> frames=new HashMap<>(); private final Map<String,Integer> stacks=new HashMap<>();
    private final JSONArray stages=new JSONArray(); private HandlerThread metricsThread;private Thread sampler;
    void check(boolean b,String why){if(!b)throw new AssertionError(why);}
    void main(Runnable task){Throwable[] e={null};instrumentation.runOnMainSync(()->{try{task.run();}catch(Throwable error){e[0]=error;}});if(e[0]!=null)throw new AssertionError(e[0]);}
    void key(int code){instrumentation.sendKeyDownUpSync(code);waitForIdleSync();}
    void status(String text){Bundle b=new Bundle();b.putString("stream",text+"\n");instrumentation.sendStatus(1,b);}
    private void attach(Window window){main(()->window.addOnFrameMetricsAvailableListener((w,m,dropped)->{String p=phase;if(p.equals("idle"))return;long ns=m.getMetric(FrameMetrics.TOTAL_DURATION);synchronized(frames){frames.computeIfAbsent(p,k->new ArrayList<>()).add(ns);}},new Handler(metricsThread.getLooper())));}
    private long cpu(){long[] ns={0};main(()->ns[0]=Debug.threadCpuTimeNanos());return ns[0];}
    private static long percentile(List<Long> values,double fraction){if(values.isEmpty())return 0;List<Long> s=new ArrayList<>(values);Collections.sort(s);return s.get(Math.min(s.size()-1,(int)Math.ceil(s.size()*fraction)-1));}
    private interface Work {void run()throws Exception;}
    private void measure(String label,Work work)throws Exception{
        waitForIdleSync();SystemClock.sleep(250);phase=label;android.util.Log.i("QuswisProfile","START "+label);long start=SystemClock.elapsedRealtime(),used=cpu();int[] disk={0};StrictMode.ThreadPolicy[] old={null};
        main(()->{old[0]=StrictMode.getThreadPolicy();StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder().detectDiskReads().detectDiskWrites().penaltyListener(Runnable::run,v->disk[0]++).build());});
        try{work.run();waitForIdleSync();SystemClock.sleep(250);}finally{main(()->StrictMode.setThreadPolicy(old[0]));}
        long elapsed=SystemClock.elapsedRealtime()-start,consumed=cpu()-used;phase="idle";List<Long> samples;synchronized(frames){samples=new ArrayList<>(frames.getOrDefault(label,Collections.emptyList()));}
        int slow=0;for(long ns:samples)if(ns>50_000_000)slow++;
        long[] overlay={0};main(()->{for(int n=0;n<home.root.getChildCount();n++){View child=home.root.getChildAt(n);if(child instanceof AppFocusIndicator)overlay[0]=(long)child.getWidth()*child.getHeight();}});
        JSONObject row=new JSONObject().put("overlay_node_area_px",overlay[0]).put("stage",label).put("wall_ms",elapsed).put("main_cpu_ms",consumed/1e6).put("frames",samples.size()).put("frame_p50_ms",percentile(samples,.5)/1e6).put("frame_p95_ms",percentile(samples,.95)/1e6).put("frames_over_50ms",slow).put("main_disk_violations",disk[0]).put("pss_kib",Debug.getPss()).put("java_heap_bytes",Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory()).put("native_heap_bytes",Debug.getNativeHeapAllocatedSize());stages.put(row);status("PERF_STAGE "+row);
    }
    private void arrows(int a,int b,int count){for(int n=0;n<count;n++){key(n%2==0?a:b);SystemClock.sleep(18);}}
    private void fixture(LiveTvDialog dialog)throws Exception{
        String text;try(InputStream in=getContext().getAssets().open("sa.m3u")){ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[4096];int n;while((n=in.read(buffer))!=-1)bytes.write(buffer,0,n);text=bytes.toString("UTF-8");}
        LiveTvRepository.Snapshot snapshot=new LiveTvRepository.Snapshot();snapshot.channels.addAll(LiveTvData.parse(text).channels);snapshot.cached=true;snapshot.updated=1;
        main(()->{try{Field f=LiveTvDialog.class.getDeclaredField("request");f.setAccessible(true);Object request=f.get(dialog);if(request instanceof java.util.concurrent.Future)((java.util.concurrent.Future<?>)request).cancel(true);}catch(Exception e){throw new RuntimeException(e);}dialog.loadTicket++;dialog.snapshot=snapshot;dialog.adapter.items.clear();dialog.adapter.items.addAll(snapshot.channels);dialog.adapter.notifyDataSetChanged();dialog.empty.setVisibility(View.GONE);dialog.channelList.requestFocus();dialog.channelList.setSelection(0);});
    }
    private void profile()throws Exception{
        metricsThread=new HandlerThread("frame-metrics");metricsThread.start();attach(home.getWindow());sampling=true;
        sampler=new Thread(()->{while(sampling){String p=phase;if(!p.equals("idle")){StackTraceElement[] trace=Looper.getMainLooper().getThread().getStackTrace();for(StackTraceElement e:trace){String c=e.getClassName();if(c.contains("BitmapFactory")||c.contains("Glyphs")||c.contains("SharedPreferences")||c.contains("SceneView")||c.contains("AudioManager")||c.contains("AssetManager")){String k=p+"|"+c+"."+e.getMethodName();synchronized(stacks){stacks.put(k,stacks.getOrDefault(k,0)+1);}break;}}}SystemClock.sleep(5);}},"main-stack-sampler");sampler.start();
        measure("night-dock",()->{main(()->home.sceneHome.focusDock(0));arrows(KeyEvent.KEYCODE_DPAD_RIGHT,KeyEvent.KEYCODE_DPAD_LEFT,28);});
        measure("night-lenses",()->{main(()->home.prayerCard.requestFocus());arrows(KeyEvent.KEYCODE_DPAD_DOWN,KeyEvent.KEYCODE_DPAD_UP,28);});
        measure("all-apps",()->{main(()->home.sceneHome.focusDock(home.sceneHome.dockTiles.size()-1));key(KeyEvent.KEYCODE_DPAD_CENTER);arrows(KeyEvent.KEYCODE_DPAD_RIGHT,KeyEvent.KEYCODE_DPAD_LEFT,28);key(KeyEvent.KEYCODE_BACK);});
        measure("settings",()->{SettingsActivity page=(SettingsActivity)startActivitySync(new Intent(getTargetContext(),SettingsActivity.class).putExtra("page","Look and scenes").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));attach(page.getWindow());arrows(KeyEvent.KEYCODE_DPAD_DOWN,KeyEvent.KEYCODE_DPAD_UP,24);key(KeyEvent.KEYCODE_DPAD_RIGHT);arrows(KeyEvent.KEYCODE_DPAD_DOWN,KeyEvent.KEYCODE_DPAD_UP,16);main(page::finish);});
        measure("glyph-hot-path",()->{Set<Bitmap> identities=Collections.newSetFromMap(new IdentityHashMap<>());long[] elapsed={0};main(()->{long start=System.nanoTime();for(int n=0;n<320;n++)identities.add(Glyphs.get(n%2==0?"controls":"check_on",Ui.px(home,20),n%2==0?Color.WHITE:0xFF111114));elapsed[0]=System.nanoTime()-start;});status("GLYPH_IDENTITIES "+identities.size()+" calls=320 cpu_wall_us="+elapsed[0]/1000);});
        for(String name:new String[]{"prayer","matches","news","live"})measure("window-"+name,()->{for(int n=0;n<3;n++){main(()->{if(name.equals("prayer"))home.showPrayerPanel();else if(name.equals("matches"))home.showFootballPanel();else if(name.equals("news"))home.showNewsPanel();else home.openLiveBroadcast();});SystemClock.sleep(name.equals("prayer")?1500:400);if(name.equals("live")){LiveTvDialog d=home.liveTvDialog;fixture(d);arrows(KeyEvent.KEYCODE_DPAD_DOWN,KeyEvent.KEYCODE_DPAD_UP,16);}else arrows(KeyEvent.KEYCODE_DPAD_RIGHT,KeyEvent.KEYCODE_DPAD_LEFT,6);key(KeyEvent.KEYCODE_BACK);SystemClock.sleep(350);}});
        measure("classic",()->{main(()->{Scenes.setLook(home,Scenes.LOOK_CLASSIC);home.applyLookMode();});SystemClock.sleep(500);main(()->home.focusIndex(0));arrows(KeyEvent.KEYCODE_DPAD_RIGHT,KeyEvent.KEYCODE_DPAD_LEFT,28);main(()->{Scenes.setLook(home,Scenes.LOOK_SCENES);home.applyLookMode();});});
        sampling=false;sampler.join(2000);metricsThread.quitSafely();JSONObject sampled=new JSONObject();synchronized(stacks){for(Map.Entry<String,Integer> e:stacks.entrySet())sampled.put(e.getKey(),e.getValue());}
        JSONObject report=new JSONObject().put("label",args.getString("label","candidate")).put("sdk",Build.VERSION.SDK_INT).put("size",home.screenW+"x"+home.screenH).put("device",Build.MODEL).put("animations","enabled, unchanged production durations").put("network","disabled; list uses a recorded real IPTV-org fixture; no video playback in performance phase").put("stages",stages).put("main_stack_samples",sampled).put("runtime_stats",new JSONObject(Debug.getRuntimeStats()));
        File file=new File(getTargetContext().getExternalFilesDir(null),"profile-"+args.getString("label","candidate")+"-"+home.screenW+"x"+home.screenH+".json");try(FileOutputStream out=new FileOutputStream(file)){out.write(report.toString(2).getBytes("UTF-8"));}status("PROFILE_JSON "+file.getName());
    }
    public void run(Bundle a){args=a==null?new Bundle():a;Bundle result=new Bundle();try{
        Context app=getTargetContext();app.getSharedPreferences("setup",0).edit().putBoolean("setup_done",true).commit();Profiles.homePrefs(app).edit().putBoolean("asked_default_home",true).commit();app.getSharedPreferences("settings",0).edit().putString("look_mode",Scenes.LOOK_SCENES).putString("scene_enabled","uhd_night-lake").putString("prefer_scene_id","uhd_night-lake").commit();LiveStream.setEnabled(app,true);WorldNews.setEnabled(app,true);PrayerTimes.setEnabled(app,true);FootballScores.setEnabled(app,true);
        // Recorded real AlAdhan month, identical in both cohorts; no fabricated prayer times.
        try(InputStream fixture=getContext().getAssets().open("prayer-jeddah-2026-11.json")){
            ByteArrayOutputStream data=new ByteArrayOutputStream();byte[] block=new byte[4096];int count;while((count=fixture.read(block))!=-1)data.write(block,0,count);
            JSONObject month=new JSONObject();PrayerPlan.merge(new JSONObject(data.toString("UTF-8")),month);
            PrayerTimes.prefs(app).edit().putString("calendar_location",PrayerCalendar.location(app)).putString("calendar_days",month.toString()).putLong("calendar_at",System.currentTimeMillis()).commit();PrayerCalendar.applyToday(app);
        }
        home=(MainActivity)startActivitySync(new Intent(app,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));waitForIdleSync();SystemClock.sleep(2500);if(args.getString("expected_size")!=null)check(args.getString("expected_size").equals(home.screenW+"x"+home.screenH),"Unexpected test resolution");
        if("true".equals(args.getString("smoke"))){main(()->{check(home.sceneHome.getSquareAppLogo("test-only-absent-logo")==null,"Unknown logo must fall back safely");check(home.sceneHome.getSquareAppLogo("test-only-absent-logo")==null,"Negative cache must remain safe");});android.graphics.Bitmap screenshot=instrumentation.getUiAutomation().takeScreenshot();if(screenshot!=null){try(FileOutputStream image=new FileOutputStream(new File(getTargetContext().getExternalFilesDir(null),"candidate-smoke.png"))){screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,image);}screenshot.recycle();}result.putString("stream","SMOKE_OK\n");instrumentation.finish(Activity.RESULT_OK,result);return;}
        if("true".equals(args.getString("audio"))){Class<?> checks=Class.forName("com.techgenius.tvoslauncher.AudioRegressions");Method run=checks.getDeclaredMethod("run",AudioPerfInstrumentation.class,MainActivity.class);run.setAccessible(true);run.invoke(null,this,home);result.putString("stream","AUDIO_REGRESSIONS_OK\n");}
        else {profile();result.putString("stream","PROFILE_OK\n");}
        instrumentation.finish(Activity.RESULT_OK,result);
    }catch(Throwable error){sampling=false;if(metricsThread!=null)metricsThread.quitSafely();while(error instanceof java.lang.reflect.InvocationTargetException&&error.getCause()!=null)error=error.getCause();error.printStackTrace();java.io.StringWriter trace=new java.io.StringWriter();error.printStackTrace(new java.io.PrintWriter(trace));result.putString("stream","AUDIO_PERF_FAILED "+trace+"\n");instrumentation.finish(Activity.RESULT_CANCELED,result);}}
}
