package com.techgenius.tvoslauncher;

import android.app.*;import android.content.*;import android.content.pm.*;import android.net.Uri;import android.os.*;import android.provider.Settings;import android.widget.Toast;
import org.json.*;import java.io.*;import java.net.*;import java.security.MessageDigest;import java.util.*;import java.util.concurrent.atomic.AtomicBoolean;

/** GitHub release checks are automatic; download and installer always need user consent. */
public final class AppUpdater {
 public static final String GITHUB_API_LATEST="https://api.github.com/repos/vipkash13-prog/Quswis-tvos-launcher/releases/latest";
 public static final String GITHUB_FALLBACK_APK="https://github.com/vipkash13-prog/Quswis-tvos-launcher/releases/latest/download/Quswis-tvOS-Launcher.apk";
 public static final boolean UPDATES_FROZEN=false;
 private static final Handler MAIN=new Handler(Looper.getMainLooper());
 private static final AtomicBoolean CHECKING=new AtomicBoolean(),DOWNLOADING=new AtomicBoolean(),INSTALLING=new AtomicBoolean();
 private static boolean offering;
 public interface CheckCallback{void onResult(boolean success,String latestVersion,String downloadUrl,boolean available,String error);}
 private static SharedPreferences prefs(Context c){return c.getApplicationContext().getSharedPreferences("updates",Context.MODE_PRIVATE);}
 private static void migrate(Context c){SharedPreferences p=prefs(c);if(!p.getBoolean("consent_checks_v1",false))p.edit().putBoolean("consent_checks_v1",true).putBoolean("auto_update",true).apply();}
 public static boolean isAutoUpdateEnabled(Context c){migrate(c);return prefs(c).getBoolean("auto_update",true);}
 public static void setAutoUpdateEnabled(Context c,boolean enabled){migrate(c);prefs(c).edit().putBoolean("auto_update",enabled).apply();}
 public static boolean isChecking(){return CHECKING.get();}public static boolean isDownloading(){return DOWNLOADING.get();}
 public static String getCurrentVersion(Context c){try{return c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionName;}catch(Exception e){return "";}}
 public static String getCachedLatestVersion(Context c){return prefs(c).getString("latest_version","");}
 public static String getCachedDownloadUrl(Context c){return prefs(c).getString("latest_apk_url","");}
 public static String cleanVersion(String v){return AppUpdateData.cleanVersion(v);}
 public static boolean isNewerVersion(String current,String remote){return AppUpdateData.isNewer(current,remote);}
 public static void checkForUpdates(Context context,CheckCallback callback){
  Context app=context.getApplicationContext();if(!CHECKING.compareAndSet(false,true)){if(callback!=null)MAIN.post(()->callback.onResult(false,"","",false,"جارٍ التحقق بالفعل"));return;}
  prefs(app).edit().putLong("last_check_ms",System.currentTimeMillis()).apply();
  new Thread(()->{try{
   HttpURLConnection http=(HttpURLConnection)new URL(GITHUB_API_LATEST).openConnection();JSONObject release;
   try{http.setConnectTimeout(10000);http.setReadTimeout(15000);http.setRequestProperty("Accept","application/vnd.github+json");http.setRequestProperty("User-Agent","Quswis-Updater");if(http.getResponseCode()!=200)throw new IOException();release=new JSONObject(new String(read(http.getInputStream(),2*1024*1024),"UTF-8"));}finally{http.disconnect();}
   AppUpdateData.Release found=AppUpdateData.release(release);prefs(app).edit().putString("latest_version",found.version).putString("latest_apk_url",found.url).putString("latest_apk_sha256",found.digest).apply();
   CHECKING.set(false);if(callback!=null)MAIN.post(()->callback.onResult(true,found.version,found.url,isNewerVersion(getCurrentVersion(app),found.version),null));
  }catch(Exception e){CHECKING.set(false);if(callback!=null)MAIN.post(()->callback.onResult(false,getCachedLatestVersion(app),getCachedDownloadUrl(app),false,"تعذّر التحقق من GitHub. تحقق من الاتصال أو حاول لاحقًا."));}},"github-update-check").start();
 }
 public static void checkAutoUpdateIfNeeded(Activity activity){
  if(activity==null||activity.isFinishing()||!isAutoUpdateEnabled(activity)||CHECKING.get()||DOWNLOADING.get())return;
  if(System.currentTimeMillis()-prefs(activity).getLong("last_check_ms",0)<15*60000L)return;
  checkForUpdates(activity,(ok,version,url,available,error)->{if(ok&&available&&!activity.isFinishing()&&!(version.equals(prefs(activity).getString("dismissed_version",""))&&System.currentTimeMillis()-prefs(activity).getLong("dismissed_at",0)<24*3600000L))offer(activity,url,version,null);});
 }
 public static void manualCheckAndPrompt(Activity activity,Runnable refresh){
  if(activity==null||activity.isFinishing())return;
  checkForUpdates(activity,(ok,version,url,available,error)->{if(refresh!=null)refresh.run();if(activity.isFinishing())return;if(!ok){toast(activity,error);return;}if(available)offer(activity,url,version,refresh);else toast(activity,"نسختك محدثة أو أحدث من الإصدار المستقر على GitHub • "+getCurrentVersion(activity));});
  if(refresh!=null)refresh.run();
 }
 private static void show(Activity activity,AlertDialog dialog){dialog.setOnShowListener(d->{if(dialog.getButton(AlertDialog.BUTTON_NEGATIVE)!=null)dialog.getButton(AlertDialog.BUTTON_NEGATIVE).requestFocus();});dialog.show();}
 private static void offer(Activity activity,String url,String version,Runnable refresh){
  if(offering||activity.isFinishing())return;offering=true;
  AlertDialog dialog=new AlertDialog.Builder(activity,android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle("يتوفر تحديث • "+version)
   .setMessage("الإصدار الحالي: "+getCurrentVersion(activity)+"\nالإصدار المتاح: "+version+"\nيبدأ التنزيل فقط عند اختيار تحديث الآن. يُفحص التوقيع ورقم البناء لحماية بياناتك.")
   .setPositiveButton("تحديث الآن",(d,w)->startDownload(activity,url,version,refresh)).setNegativeButton("لاحقًا",(d,w)->prefs(activity).edit().putString("dismissed_version",version).putLong("dismissed_at",System.currentTimeMillis()).apply()).create();dialog.setOnDismissListener(d->offering=false);show(activity,dialog);
 }
 /** Compatibility entry point: even callers requesting silent mode must obtain explicit consent. */
 public static void downloadAndInstall(Activity activity,String url,String version,boolean ignoredSilent,Runnable refresh){offer(activity,url,version,refresh);}
 private static void startDownload(Activity activity,String url,String version,Runnable refresh){
  if(!AppUpdateData.apkUrl(url)){toast(activity,"رابط التحديث لا ينتمي إلى إصدارات المشروع على GitHub");return;}
  if(!DOWNLOADING.compareAndSet(false,true))return;Context app=activity.getApplicationContext();toast(activity,"جارٍ تنزيل التحديث بعد موافقتك…");if(refresh!=null)refresh.run();
  new Thread(()->{File temporary=new File(app.getCacheDir(),"update-download.tmp"),approved=new File(app.getCacheDir(),"approved-update.apk");
   try{HttpURLConnection http=download(url);try(InputStream input=http.getInputStream();FileOutputStream out=new FileOutputStream(temporary)){
     byte[] block=new byte[32768];long total=0;int n;while((n=input.read(block))!=-1){out.write(block,0,n);total+=n;if(total>256L*1024*1024)throw new IOException("ملف التحديث أكبر من الحد المسموح");}out.getFD().sync();
    }finally{http.disconnect();}
    String digest=prefs(app).getString("latest_apk_sha256","");if(url.equals(getCachedDownloadUrl(app))&&!digest.isEmpty()&&!digest.equals(sha256(temporary)))throw new IOException("فشل التحقق من سلامة ملف GitHub");
    validateArchive(app,temporary);if(approved.exists()&&!approved.delete())throw new IOException();if(!temporary.renameTo(approved))throw new IOException();prefs(app).edit().putString("pending_version",version).apply();
    MAIN.post(()->{DOWNLOADING.set(false);if(refresh!=null)refresh.run();if(!activity.isFinishing())installDownloaded(activity);});
   }catch(Exception error){temporary.delete();DOWNLOADING.set(false);String message=error.getMessage();MAIN.post(()->{if(refresh!=null)refresh.run();toast(app,message==null||message.isEmpty()?"تعذّر تنزيل التحديث. لم تتغير بياناتك.":message);});}
  },"github-approved-download").start();
 }
 public static boolean hasDownloadedUpdate(Context c){return new File(c.getCacheDir(),"approved-update.apk").isFile()&&!prefs(c).getString("pending_version","").isEmpty();}
 public static void installDownloaded(Activity activity){
  if(activity.isFinishing()||!hasDownloadedUpdate(activity))return;
  if(Build.VERSION.SDK_INT>=26&&!activity.getPackageManager().canRequestPackageInstalls()){
   AlertDialog dialog=new AlertDialog.Builder(activity,android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle("إذن تثبيت التحديث")
    .setMessage("اسمح بتثبيت التطبيقات لهذا اللانشر، ثم عد إلى الإعدادات واختر تثبيت التحديث المحمّل. لم تُحذف إعداداتك.")
    .setPositiveButton("فتح إذن التثبيت",(d,w)->{try{activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+activity.getPackageName())));}catch(Exception e){toast(activity,"افتح إذن تثبيت التطبيقات من إعدادات Android");}}).setNegativeButton("لاحقًا",null).create();show(activity,dialog);return;
  }
  if(!INSTALLING.compareAndSet(false,true))return;
  Context app=activity.getApplicationContext();new Thread(()->{PackageInstaller installer=app.getPackageManager().getPackageInstaller();int id=-1;try{
   File apk=new File(app.getCacheDir(),"approved-update.apk");validateArchive(app,apk);
   PackageInstaller.SessionParams params=new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);params.setAppPackageName(app.getPackageName());
   if(Build.VERSION.SDK_INT>=31)params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED);
   id=installer.createSession(params);try(PackageInstaller.Session session=installer.openSession(id)){
    try(InputStream in=new FileInputStream(apk);OutputStream out=session.openWrite("update.apk",0,apk.length())){byte[] block=new byte[32768];int n;while((n=in.read(block))!=-1)out.write(block,0,n);session.fsync(out);}
    int flags=PendingIntent.FLAG_UPDATE_CURRENT|(Build.VERSION.SDK_INT>=31?PendingIntent.FLAG_MUTABLE:0);PendingIntent result=PendingIntent.getBroadcast(app,id,new Intent(app,InstallStatusReceiver.class),flags);session.commit(result.getIntentSender());
   }
  }catch(Exception e){INSTALLING.set(false);if(id>=0)try{installer.abandonSession(id);}catch(Exception ignored){}MAIN.post(()->toast(app,e.getMessage()==null?"تعذّر فتح مثبّت التحديث":e.getMessage()));}},"approved-package-installer").start();
 }
 static void validateArchive(Context c,File apk)throws Exception{
  PackageManager pm=c.getPackageManager();int flags=Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES;
  PackageInfo incoming=pm.getPackageArchiveInfo(apk.getAbsolutePath(),flags),current=pm.getPackageInfo(c.getPackageName(),flags);
  if(incoming==null||!c.getPackageName().equals(incoming.packageName))throw new IOException("ملف التحديث لا يخص هذا التطبيق");
  long old=Build.VERSION.SDK_INT>=28?current.getLongVersionCode():current.versionCode,next=Build.VERSION.SDK_INT>=28?incoming.getLongVersionCode():incoming.versionCode;
  if(next<=old)throw new IOException("رقم بناء الملف ليس أحدث من نسختك. لم تتغير بياناتك.");
  if(Build.VERSION.SDK_INT>=28&&(current.signingInfo==null||incoming.signingInfo==null))throw new IOException("لا يمكن التحقق من شهادة ملف التحديث. لم تتغير بياناتك.");
  android.content.pm.Signature[] ours=Build.VERSION.SDK_INT>=28?current.signingInfo.getApkContentsSigners():current.signatures,theirs=Build.VERSION.SDK_INT>=28?incoming.signingInfo.getApkContentsSigners():incoming.signatures;
  Set<String> a=new HashSet<>(),b=new HashSet<>();if(ours!=null)for(android.content.pm.Signature x:ours)a.add(hex(MessageDigest.getInstance("SHA-256").digest(x.toByteArray())));if(theirs!=null)for(android.content.pm.Signature x:theirs)b.add(hex(MessageDigest.getInstance("SHA-256").digest(x.toByteArray())));
  if(a.isEmpty()||!a.equals(b))throw new IOException("توقيع التحديث لا يطابق النسخة المثبتة. أُوقف التثبيت لحماية بياناتك. لا تحذف نسختك الحالية.");
 }
 private static HttpURLConnection download(String source)throws Exception{
  String url=source;for(int i=0;i<8;i++){
   if(!AppUpdateData.downloadHost(url))throw new IOException("وجهة تنزيل غير موثوقة");HttpURLConnection http=(HttpURLConnection)new URL(url).openConnection();http.setInstanceFollowRedirects(false);http.setConnectTimeout(15000);http.setReadTimeout(30000);http.setRequestProperty("User-Agent","Quswis-Updater");int status=http.getResponseCode();
   if(status>=300&&status<400){String next=http.getHeaderField("Location");http.disconnect();if(next==null)throw new IOException();url=new URI(url).resolve(next).toString();continue;}if(status!=200){http.disconnect();throw new IOException("تعذّر تنزيل الملف من GitHub");}return http;
  }throw new IOException("تحويلات تنزيل كثيرة");
 }
 private static byte[] read(InputStream source,int limit)throws Exception{try(InputStream input=source;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=input.read(b))!=-1){out.write(b,0,n);if(out.size()>limit)throw new IOException();}return out.toByteArray();}}
 private static String hex(byte[] bytes){StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format(Locale.US,"%02x",b&255));return out.toString();}
 static String sha256(File f)throws Exception{MessageDigest d=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(f)){byte[] b=new byte[32768];int n;while((n=in.read(b))!=-1)d.update(b,0,n);}return hex(d.digest());}
 private static void toast(Context c,String text){Toast.makeText(c,text,Toast.LENGTH_LONG).show();}
 public static final class InstallStatusReceiver extends BroadcastReceiver{
  @Override public void onReceive(Context c,Intent intent){if(intent==null)return;int status=intent.getIntExtra(PackageInstaller.EXTRA_STATUS,-1);
   if(status==PackageInstaller.STATUS_PENDING_USER_ACTION){Intent confirm=intent.getParcelableExtra(Intent.EXTRA_INTENT);if(confirm!=null){confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);try{c.startActivity(confirm);}catch(Exception e){toast(c,"أعد فتح إعدادات التحديث لإكمال موافقة Android");}}}
   else if(status==PackageInstaller.STATUS_SUCCESS){INSTALLING.set(false);prefs(c).edit().remove("pending_version").apply();new File(c.getCacheDir(),"approved-update.apk").delete();toast(c,"اكتمل التحديث مع الاحتفاظ ببيانات التطبيق");}
   else {INSTALLING.set(false);toast(c,"لم يُثبَّت التحديث. بقيت النسخة الحالية وبياناتها محفوظة.");}
  }
 }
}
