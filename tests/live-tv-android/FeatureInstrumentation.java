package com.techgenius.tvoslauncher;

import android.app.*;import android.content.*;import android.os.*;import android.view.*;import android.widget.*;import androidx.media3.common.Player;
import java.io.*;import java.lang.reflect.*;import java.nio.charset.StandardCharsets;import java.util.*;import org.json.*;

/** Generic test helper. Actual subscription secrets are read only from a private on-device file. */
@androidx.media3.common.util.UnstableApi
final class FeatureInstrumentation {
 final AudioPerfInstrumentation t;final MainActivity home;
 FeatureInstrumentation(AudioPerfInstrumentation t,MainActivity home){this.t=t;this.home=home;}
 interface Check{boolean ok();}
 void await(String name,long duration,Check condition){long end=SystemClock.elapsedRealtime()+duration;boolean[] result={false};while(!result[0]&&SystemClock.elapsedRealtime()<end){t.main(()->result[0]=condition.ok());if(!result[0])SystemClock.sleep(150);}t.check(result[0],name);t.status("FEATURE_CHECK "+name);}
 void invoke(Object target,String method){try{Method m=target.getClass().getDeclaredMethod(method);m.setAccessible(true);m.invoke(target);}catch(Exception e){throw new RuntimeException(e);}}
 TextView text(View view,String label){if(view instanceof TextView&&((TextView)view).getText().toString().equals(label))return(TextView)view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int n=0;n<group.getChildCount();n++){TextView found=text(group.getChildAt(n),label);if(found!=null)return found;}}return null;}
 byte[] read(File file)throws Exception{try(InputStream in=new FileInputStream(file);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] data=new byte[8192];int n;while((n=in.read(data))!=-1)out.write(data,0,n);return out.toByteArray();}}
 Object field(Object object,String key){try{Field f=object.getClass().getDeclaredField(key);f.setAccessible(true);return f.get(object);}catch(Exception e){throw new RuntimeException(e);}}
 void rendered(LiveTvDialog dialog,String label){await(label+" real decoded video",180000,()->dialog.engine.rendered&&dialog.engine.player.isPlaying()&&dialog.engine.player.getAudioFormat()!=null&&dialog.engine.player.getVolume()==1f);}
 void run(Bundle args)throws Exception{
  boolean privateAccount="true".equals(args.getString("private_xtream")),persisted="true".equals(args.getString("persisted"));
  if("true".equals(args.getString("retained"))){t.check(home.getSharedPreferences("feature-update-proof",0).getString("retained","").equals("confirmed-207-to-208"),"Update erased preferences");t.check(XtreamRepository.config(home)!=null,"Update lost Keystore-encrypted credentials");LiveTvRepository r=new LiveTvRepository(home);t.check(r.last()!=null&&r.favorite(r.last()),"Update erased last channel or favorite");t.check(XtreamRepository.saved(home)!=null&&r.favorites().stream().anyMatch(id->id.startsWith("xtream:")),"Update erased subscription catalogue or favorites");t.status("UPDATE_DATA_RETAINED_OK");return;}
  if("true".equals(args.getString("installer"))){installer();return;}
  JSONObject input;
  if(privateAccount){File secret=new File(home.getNoBackupFilesDir(),"xtream-test-input.json");t.check(secret.isFile(),"Private account input not provided");input=new JSONObject(new String(read(secret),StandardCharsets.UTF_8));t.check(secret.delete(),"Private test input was not removed");}
  else input=new JSONObject().put("server","http://10.0.2.2:8765").put("username","ci_native_user").put("password","ci_native_secret_207");
  if(!persisted)XtreamRepository.save(home,input.getString("server"),input.getString("username"),input.getString("password"));
  LiveTvRepository repo=new LiveTvRepository(home);LiveTvRepository.Snapshot snapshot=repo.read(7,!persisted);
  t.check(!snapshot.failed&&!snapshot.channels.isEmpty(),"Live subscription API must provide real channels before playback checks");
  JSONObject catalog=XtreamRepository.saved(home);t.check(catalog.getJSONArray("categories").length()>0,"Live categories missing");
  t.status("FEATURE_CHECK subscription authenticated and catalogue loaded; credentials withheld");
  String credentialsPath="/"+XtreamData.segment(input.getString("username"))+"/"+XtreamData.segment(input.getString("password"))+"/";
  for(LiveTvData.Channel c:snapshot.channels)t.check(!c.json().toString().contains(credentialsPath),"A channel persisted authenticated playback URLs");
  if(persisted){t.check(repo.last()!=null&&XtreamData.subscription(repo.last()),"Last subscription channel lost after restart");t.check(repo.favorite(repo.last()),"Subscription favorite lost after restart");}
  t.main(home::openLiveBroadcast);SystemClock.sleep(500);LiveTvDialog dialog=home.liveTvDialog;
  t.main(()->dialog.tabs[7].requestFocus());t.key(KeyEvent.KEYCODE_DPAD_CENTER);
  await("subscription tab loaded",90000,()->dialog.section==7&&dialog.adapter.getCount()>0);
  t.main(()->{dialog.channelList.requestFocus();dialog.channelList.setSelection(0);});t.key(KeyEvent.KEYCODE_DPAD_CENTER);rendered(dialog,"subscription initial");
  LiveTvData.Channel watched=dialog.playing;t.check(XtreamData.subscription(watched),"Subscription tab selected a public-list channel");
  if(!privateAccount){
   for(int n=0;n<12;n++)t.key(n%2==0?KeyEvent.KEYCODE_DPAD_DOWN:KeyEvent.KEYCODE_DPAD_UP);
   t.main(()->dialog.play(watched,0));rendered(dialog,"HLS");
   LiveTvData.Channel ts=snapshot.channels.size()>1?snapshot.channels.get(1):watched;
   t.main(()->dialog.play(ts,ts.streams.size()>1?1:0));rendered(dialog,"MPEG-TS");
   t.main(()->dialog.play(watched,0));rendered(dialog,"HLS restore");
  }
  List<String> decoded=new ArrayList<>();String current=dialog.engine.endpoint.url;decoded.add(current.endsWith(".ts")?"ts":"m3u8");
  if(privateAccount&&watched.streams.size()>1){for(int n=0;n<watched.streams.size();n++){final int index=n;t.main(()->dialog.play(watched,index));rendered(dialog,"supported subscription format "+n);String marker=dialog.engine.endpoint.url;String format=marker.endsWith(".ts")?"ts":"m3u8";if(!decoded.contains(format))decoded.add(format);}}
  t.main(()->{if(!repo.favorite(dialog.playing))dialog.favorite.performClick();});t.check(repo.favorite(dialog.playing),"Favorite was not saved");
  t.main(()->dialog.fullscreen.requestFocus());t.key(KeyEvent.KEYCODE_DPAD_CENTER);t.check(dialog.full,"Subscription fullscreen did not open");t.key(KeyEvent.KEYCODE_BACK);t.check(!dialog.full&&dialog.isShowing(),"Subscription fullscreen Back failed");
  t.main(()->dialog.search.requestFocus());t.key(KeyEvent.KEYCODE_DPAD_CENTER);SystemClock.sleep(250);t.key(KeyEvent.KEYCODE_BACK);SystemClock.sleep(300);
  t.main(()->dialog.settings.requestFocus());t.key(KeyEvent.KEYCODE_DPAD_CENTER);SystemClock.sleep(300);
  t.main(()->{View decor=dialog.getWindow().getDecorView();t.check(text(decor,"Username")!=null||hasEdit(decor),"Native subscription input fields absent");});t.key(KeyEvent.KEYCODE_BACK);SystemClock.sleep(200);
  String stored=repo.prefs.getAll().toString();t.check(!stored.contains(credentialsPath)&&!stored.contains("password="+XtreamData.segment(input.getString("password"))),"Subscription secrets escaped to ordinary preferences");
  LiveTvRepository.Snapshot refreshed=repo.read(7,true);t.check(!refreshed.failed&&!refreshed.channels.isEmpty(),"Subscription refresh lost working catalogue");t.check(repo.favorite(dialog.playing),"Refresh lost favorites");
  t.main(dialog::dismiss);SystemClock.sleep(500);t.check(dialog.engine.released,"Subscription player retained after close");
  if(privateAccount){JSONObject audit=new JSONObject().put("build",AppUpdater.getCurrentVersion(home)).put("real_subscription_playback",true).put("decoded_formats",new JSONArray(decoded)).put("remote_selection",true).put("fullscreen_back",true).put("encrypted_storage",true);File result=new File(home.getExternalFilesDir(null),"private-subscription-audit.json");try(FileOutputStream out=new FileOutputStream(result)){out.write(audit.toString(2).getBytes(StandardCharsets.UTF_8));}t.status("PRIVATE_SUBSCRIPTION_REAL_PLAYBACK_OK; no credentials or authenticated URLs emitted");return;}
  updater();matches();
 }
 boolean hasEdit(View v){if(v instanceof EditText)return true;if(v instanceof ViewGroup)for(int n=0;n<((ViewGroup)v).getChildCount();n++)if(hasEdit(((ViewGroup)v).getChildAt(n)))return true;return false;}
 void updater()throws Exception{
  t.check(!AppUpdater.UPDATES_FROZEN,"Updater remains frozen");File cache=new File(home.getCacheDir(),"approved-update.apk");cache.delete();
  t.main(()->AppUpdater.downloadAndInstall(home,"https://github.com/vipkash13-prog/Quswis-tvos-launcher/releases/download/v2.0.5/Quswis-tvOS-Launcher.apk","2.0.7",true,null));SystemClock.sleep(350);
  t.check(!AppUpdater.isDownloading()&&!cache.exists(),"A silent-mode caller downloaded before consent");t.key(KeyEvent.KEYCODE_DPAD_CENTER);SystemClock.sleep(250);t.check(!AppUpdater.isDownloading(),"Later did not prevent download");
  boolean[] complete={false},ok={false};t.main(()->AppUpdater.checkForUpdates(home,(success,v,url,newer,error)->{ok[0]=success;complete[0]=true;}));await("real GitHub release check",30000,()->complete[0]);t.check(ok[0],"GitHub release API unavailable during updater validation");
  home.getSharedPreferences("feature-update-proof",0).edit().putString("download_guard","retained").commit();
  java.util.concurrent.atomic.AtomicBoolean rejectedVerifiedApk=new java.util.concurrent.atomic.AtomicBoolean();t.instrumentation.getUiAutomation().setOnAccessibilityEventListener(event->{if(event.getEventType()==android.view.accessibility.AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED)for(CharSequence message:event.getText())if(message.toString().contains("رقم بناء")||message.toString().contains("توقيع التحديث"))rejectedVerifiedApk.set(true);});
  t.main(()->AppUpdater.downloadAndInstall(home,AppUpdater.getCachedDownloadUrl(home),"2.0.7",false,null));SystemClock.sleep(500);
  android.view.accessibility.AccessibilityNodeInfo root=t.instrumentation.getUiAutomation().getRootInActiveWindow();java.util.List<android.view.accessibility.AccessibilityNodeInfo> updates=root.findAccessibilityNodeInfosByText("تحديث الآن");t.check(!updates.isEmpty(),"Update consent button absent");t.check(updates.get(0).isFocused()||updates.get(0).performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_FOCUS),"Cannot focus update approval");t.key(KeyEvent.KEYCODE_DPAD_CENTER);
  await("approved GitHub APK download finished",120000,()->!AppUpdater.isDownloading());await("downloaded legacy APK rejected by build/signature guard",8000,rejectedVerifiedApk::get);t.instrumentation.getUiAutomation().setOnAccessibilityEventListener(null);t.check(!cache.exists(),"Incompatible legacy GitHub APK passed validation");t.check(home.getSharedPreferences("feature-update-proof",0).getString("download_guard","").equals("retained"),"Rejected update erased data");
  File valid=new File(home.getExternalFilesDir(null),"update-fixture.apk"),foreign=new File(home.getExternalFilesDir(null),"foreign-fixture.apk");
  t.check(valid.isFile()&&foreign.isFile(),"Signed update fixtures missing");
  if(valid.isFile()){AppUpdater.validateArchive(home,valid);t.status("FEATURE_CHECK higher-build matching-signature APK accepted");}
  if(foreign.isFile()){boolean refused=false;try{AppUpdater.validateArchive(home,foreign);}catch(Exception e){refused=e.getMessage()!=null&&e.getMessage().contains("توقيع");}t.check(refused,"Mismatched signer was not explicitly rejected");}
  SettingsActivity settings=(SettingsActivity)t.startActivitySync(new Intent(home,SettingsActivity.class).putExtra("page","Updates").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));SystemClock.sleep(400);t.main(()->{TextView action=text(settings.getWindow().getDecorView(),"التحقق من وجود تحديثات");t.check(action!=null,"Manual update setting absent");View focus=action;while(!focus.isFocusable()&&focus.getParent() instanceof View)focus=(View)focus.getParent();t.check(focus.requestFocus(),"Update action not focusable by remote");});long before=home.getSharedPreferences("updates",0).getLong("last_check_ms",0);t.key(KeyEvent.KEYCODE_DPAD_CENTER);await("manual Check now reached updater",30000,()->home.getSharedPreferences("updates",0).getLong("last_check_ms",0)>before);await("manual update check completed",30000,()->!AppUpdater.isChecking());t.main(settings::finish);t.status("FEATURE_CHECK consent + update setting + signature guards");
 }
 void matches()throws Exception{
  MatchCenterRepository repository=new MatchCenterRepository(home);Set<String> leagues=new HashSet<>(Arrays.asList("uefa.champions","ksa.1"));java.util.concurrent.CountDownLatch done=new java.util.concurrent.CountDownLatch(1);MatchCenterRepository.Snapshot[] received={null};repository.refresh(leagues,MatchCenterData.UPCOMING,result->{received[0]=result;done.countDown();});t.check(done.await(60,java.util.concurrent.TimeUnit.SECONDS),"Match refresh timed out");t.check(received[0]!=null&&received[0].hasCache,"No real ESPN cache returned");
  List<JSONObject> future=MatchCenterData.select(received[0].matches,leagues,MatchCenterData.UPCOMING,System.currentTimeMillis(),Integer.MAX_VALUE);t.status("FEATURE_CHECK real ESPN response parsed; upcoming count="+future.size());
  for(JSONObject m:future)t.check("pre".equals(m.getJSONObject("fixture").getString("state")),"Completed match shown as upcoming");
  repository.select(leagues);t.main(home::showFootballPanel);SystemClock.sleep(400);MatchCenterDialog dialog=home.matchCenterDialog;t.check(dialog!=null,"Match window did not open");View[] tabs=(View[])field(dialog,"tabs");
  for(int n=0;n<3;n++){final int index=n;t.main(()->tabs[index].requestFocus());t.key(KeyEvent.KEYCODE_DPAD_CENTER);await("match tab "+n+" refreshed",60000,()->!((MatchCenterRepository.Snapshot)field(dialog,"snapshot")).loading);
   MatchCenterRepository.Snapshot shown=(MatchCenterRepository.Snapshot)field(dialog,"snapshot");List<JSONObject> expected=MatchCenterData.select(shown.matches,leagues,n,System.currentTimeMillis(),Integer.MAX_VALUE);t.check(((Integer)field(dialog,"total"))==expected.size(),"Match UI differs from real provider/status selection");t.status("FEATURE_MATCH_TAB "+n+" count="+expected.size());
   if(n==0&&expected.size()>5){Object[] rows=(Object[])field(dialog,"rows");View last=(View)field(rows[4],"view");t.main(last::requestFocus);t.key(KeyEvent.KEYCODE_DPAD_DOWN);t.check(((Integer)field(dialog,"page"))==1,"Upcoming matches still capped at five");t.key(KeyEvent.KEYCODE_DPAD_UP);t.check(((Integer)field(dialog,"page"))==0,"Match pagination lost reverse focus");}
  }
  t.key(KeyEvent.KEYCODE_BACK);SystemClock.sleep(300);t.check(home.matchCenterDialog==null,"Match Back failed");

 }
 void installer()throws Exception{
  File source=new File(home.getExternalFilesDir(null),"update-fixture.apk"),target=new File(home.getCacheDir(),"approved-update.apk");AppUpdater.validateArchive(home,source);
  try(InputStream in=new FileInputStream(source);OutputStream out=new FileOutputStream(target)){byte[] block=new byte[32768];int n;while((n=in.read(block))!=-1)out.write(block,0,n);}
  home.getSharedPreferences("updates",0).edit().putString("pending_version","2.0.7").apply();home.getSharedPreferences("feature-update-proof",0).edit().putString("retained","confirmed-207-to-208").commit();
  t.main(()->AppUpdater.installDownloaded(home));t.status("INSTALLER_USER_CONFIRMATION_REQUESTED");SystemClock.sleep(90000);
 }
}
