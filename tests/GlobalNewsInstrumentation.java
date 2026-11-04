package com.techgenius.tvoslauncher;
import android.app.*;import android.content.*;import android.graphics.*;import android.os.*;import android.view.*;import android.widget.*;import java.io.*;import java.lang.reflect.*;import java.util.*;import java.util.concurrent.Future;
/** Native Android 14 remote, source data, cache and protected Home geometry checks. */
public final class GlobalNewsInstrumentation extends Instrumentation {
 private MainActivity host;private GlobalNewsDialog dialog;private boolean offline;
 public void onCreate(Bundle a){super.onCreate(a);offline=a!=null&&"true".equals(a.getString("offline"));start();}
 private Object uncheckedField(String n){try{return field(n);}catch(Exception e){throw new RuntimeException(e);}}
 private void check(boolean b,String why){if(!b)throw new AssertionError(why);}
 private Object field(String n)throws Exception{Field f=GlobalNewsDialog.class.getDeclaredField(n);f.setAccessible(true);return f.get(dialog);}
 private void mainCheck(Runnable r){Throwable[] e={null};runOnMainSync(()->{try{r.run();}catch(Throwable x){e[0]=x;}});if(e[0]!=null)throw new AssertionError(e[0]);}
 private void key(int code){sendKeyDownUpSync(code);waitForIdleSync();}
 private int id(){int[] r={-1};runOnMainSync(()->{if(dialog.getCurrentFocus()!=null)r[0]=dialog.getCurrentFocus().getId();});return r[0];}
 private int[] bounds(View v){int[] p=new int[2];v.getLocationOnScreen(p);return new int[]{p[0],p[1],v.getWidth(),v.getHeight()};}
 private void cycle(int code){Set<Integer> ids=new HashSet<>();int first=id();for(int i=0;i<5;i++){ids.add(id());key(code);}check(ids.size()==5&&first==id(),"Five tabs remote traversal "+code);}
 private View row(Object row)throws Exception{Field f=row.getClass().getDeclaredField("view");f.setAccessible(true);return(View)f.get(row);}
 private void unclipped(TextView t,String label){check(t.getLayout()!=null,label+" missing layout");check(t.getLayout().getHeight()<=t.getHeight()-t.getCompoundPaddingTop()-t.getCompoundPaddingBottom(),label+" vertically clipped");check(t.getLayout().getEllipsisCount(0)==0,label+" ellipsized");}
 public void onStart(){Bundle result=new Bundle();try{
  Context app=getTargetContext();app.getSharedPreferences("setup",0).edit().putBoolean("setup_done",true).commit();Profiles.homePrefs(app).edit().putBoolean("asked_default_home",true).commit();WorldNews.setEnabled(app,true);getUiAutomation();
  String football=FootballScores.selectedLeagues(app).toString();host=(MainActivity)startActivitySync(new Intent(app,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));waitForIdleSync();SystemClock.sleep(2200);key(KeyEvent.KEYCODE_DPAD_RIGHT);
  runOnMainSync(()->host.newsShortcut.requestFocus());SystemClock.sleep(300);int[][] before=new int[4][];runOnMainSync(()->{View[] v={host.wallView,host.dockRow,host.footballShortcut,host.prayerCard};for(int i=0;i<4;i++)before[i]=bounds(v[i]);});key(KeyEvent.KEYCODE_DPAD_CENTER);runOnMainSync(()->dialog=host.globalNewsDialog);SystemClock.sleep(600);waitForIdleSync();check(dialog!=null&&dialog.isShowing(),"Original news lens OK did not open");
  mainCheck(()->{View[] v={host.wallView,host.dockRow,host.footballShortcut,host.prayerCard};for(int i=0;i<4;i++)check(Arrays.equals(before[i],bounds(v[i])),"Home geometry changed: "+i);});cycle(KeyEvent.KEYCODE_DPAD_LEFT);cycle(KeyEvent.KEYCODE_DPAD_RIGHT);
  View[] tabs=(View[])field("tabs");for(int i=0;i<5;i++){final int n=i;runOnMainSync(()->tabs[n].requestFocus());key(KeyEvent.KEYCODE_DPAD_CENTER);check((Integer)field("section")==i&&new GlobalNewsRepository(app).section()==i,"Tab activation/persistence "+i);}
  runOnMainSync(()->{tabs[0].performClick();tabs[0].requestFocus();});Future<?> request=(Future<?>)field("request");long until=SystemClock.elapsedRealtime()+35000;while(request!=null&&!request.isDone()&&SystemClock.elapsedRealtime()<until)SystemClock.sleep(250);waitForIdleSync();SystemClock.sleep(3000);
  GlobalNewsRepository.Snapshot data=(GlobalNewsRepository.Snapshot)field("snapshot");check(data!=null,"Snapshot missing");if(!offline)check(!data.stories.isEmpty(),"All real news feeds failed");else check(data.cached&&data.failed&&!data.stories.isEmpty(),"Offline stored real news unavailable");for(GlobalNewsData.Story s:data.stories)check(!s.title.isEmpty()&&s.url.startsWith("https://"),"Invalid source data");
  mainCheck(()->{try{View surface=(View)field("surface");int[] b=bounds(surface);check(Math.abs(b[2]-host.screenW*.625)<8,"Reference width");check(Math.abs(b[3]-host.screenH*1050.0/2160)<8,"Reference height");check(b[1]+b[3]<host.screenH*.78,"Popup covers dock");unclipped((TextView)field("windowTitle"),"News title");unclipped((TextView)field("refreshButton"),"Update footer");}catch(Exception e){throw new RuntimeException(e);}});
  List<?> rows=(List<?>)field("rows");check(rows.size()==GlobalNewsData.select(data.stories,0,System.currentTimeMillis()).size(),"List silently capped");if(!rows.isEmpty()){View first=row(rows.get(0));runOnMainSync(first::requestFocus);int count=rows.size();for(int i=1;i<count;i++){key(KeyEvent.KEYCODE_DPAD_DOWN);check(id()==row(rows.get(i)).getId(),"Cannot reach story "+i);}ScrollView scroll=(ScrollView)field("list");check(count<=5||scroll.getScrollY()>0,"Remote did not scroll list");runOnMainSync(first::requestFocus);key(KeyEvent.KEYCODE_DPAD_LEFT);check(id()==((View)field("feature")).getId(),"Feature unreachable");key(KeyEvent.KEYCODE_DPAD_CENTER);check(field("details")!=null,"Native article summary did not open");key(KeyEvent.KEYCODE_BACK);check(field("details")==null&&dialog.isShowing(),"Article BACK closed entire popup");}
  // Select a true carousel photo after traversing the entire feed; it must not starve behind obsolete requests.
  List<?> featured=(List<?>)field("featured");if(!featured.isEmpty()){
   View[] dots=(View[])field("dots");runOnMainSync(()->{dots[0].performClick();((View)uncheckedField("feature")).requestFocus();});
   ImageView hero=(ImageView)field("featureImage");GlobalNewsData.Story first=(GlobalNewsData.Story)featured.get(0);
   if(!first.image.isEmpty()){boolean[] shown={false};long photoDeadline=SystemClock.elapsedRealtime()+18000;
    while(!shown[0]&&SystemClock.elapsedRealtime()<photoDeadline){runOnMainSync(()->shown[0]=GlobalNewsImages.ready(hero));if(!shown[0])SystemClock.sleep(200);}
    check(shown[0],"Current hero photo starved behind old image requests");}
  }
  runOnMainSync(()->((View)uncheckedField("feature")).requestFocus());waitForIdleSync();SystemClock.sleep(1000);check(football.equals(FootballScores.selectedLeagues(app).toString()),"Existing match preferences changed");
  Bitmap image=getUiAutomation().takeScreenshot();check(image!=null,"Screenshot missing");try(FileOutputStream out=new FileOutputStream(new File(app.getExternalFilesDir(null),"global-news.png"))){image.compress(Bitmap.CompressFormat.PNG,100,out);}Bitmap small=Bitmap.createScaledBitmap(image,960,540,true);ByteArrayOutputStream bytes=new ByteArrayOutputStream();small.compress(Bitmap.CompressFormat.JPEG,80,bytes);result.putString("preview_base64",android.util.Base64.encodeToString(bytes.toByteArray(),android.util.Base64.NO_WRAP));small.recycle();image.recycle();
  key(KeyEvent.KEYCODE_BACK);SystemClock.sleep(450);waitForIdleSync();check(!dialog.isShowing(),"Popup BACK did not close");boolean[] restored={false};String[] focus={""};long focusDeadline=SystemClock.elapsedRealtime()+2200;
  while(!restored[0]&&SystemClock.elapsedRealtime()<focusDeadline){runOnMainSync(()->{restored[0]=host.getCurrentFocus()==host.newsShortcut;focus[0]=String.valueOf(host.getCurrentFocus());});if(!restored[0])SystemClock.sleep(80);}
  check(restored[0],"Focus not restored to news lens; actual="+focus[0]);result.putString("stream","GLOBAL_NEWS_UI_OK: "+host.screenW+"x"+host.screenH+"; real stories="+data.stories.size()+"; offline="+offline+"\n");finish(Activity.RESULT_OK,result);
 }catch(Throwable e){result.putString("stream","GLOBAL_NEWS_UI_FAILED: "+e+"\n");finish(Activity.RESULT_CANCELED,result);}}
}
