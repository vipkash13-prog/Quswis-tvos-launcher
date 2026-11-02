package com.techgenius.tvoslauncher;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;
import android.widget.LinearLayout;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Prayer times from AlAdhan for a selected country and city. */
final class PrayerTimes {
 private static final String PREFS="prayer_times", K_DATA="data", K_AT="at", K_KEY="cache_key";
 static final String K_ENABLED="home_enabled", K_COUNTRY="country", K_CITY="city";
 private static volatile boolean busy;
 private PrayerTimes(){}
 static SharedPreferences prefs(Context c){return c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);}
 static boolean enabled(Context c){return prefs(c).getBoolean(K_ENABLED,false);}
 static void setEnabled(Context c,boolean v){prefs(c).edit().putBoolean(K_ENABLED,v).apply();}
 static String country(Context c){return prefs(c).getString(K_COUNTRY,"السعودية");}
 static String countryApi(Context c){return prefs(c).getString("country_api","Saudi Arabia");}
 static String city(Context c){return prefs(c).getString(K_CITY,"جدة");}
 private static String cityApi(Context c) {
  String value=city(c);
  String[][] map={{"جدة","Jeddah"},{"الرياض","Riyadh"},{"مكة","Mecca"},{"مكة المكرمة","Mecca"},
   {"المدينة","Medina"},{"المدينة المنورة","Medina"},{"الدمام","Dammam"},{"الخبر","Khobar"},
   {"الطائف","Taif"},{"أبها","Abha"},{"تبوك","Tabuk"},{"بريدة","Buraidah"},{"حائل","Hail"},
   {"دبي","Dubai"},{"أبوظبي","Abu Dhabi"},{"الشارقة","Sharjah"},{"الكويت","Kuwait City"},
   {"الدوحة","Doha"},{"المنامة","Manama"},{"مسقط","Muscat"},{"القاهرة","Cairo"},{"عمّان","Amman"}};
  for(String[] item:map)if(item[0].equals(value))return item[1];
  return value;
 }
 static void setLocation(Context c,String city,String country,String countryApi){
  prefs(c).edit().putString(K_CITY,city).putString(K_COUNTRY,country).putString("country_api",countryApi).remove(K_AT).apply();
 }
 /** Detail content only; the host retains the release-182 fixed window bounds. */
 static View createPanel(Context c){return new DetailPanel(c);}

 private static final class DetailPanel extends LinearLayout implements SharedPreferences.OnSharedPreferenceChangeListener {
  private final Handler tick=new Handler(Looper.getMainLooper());
  private final TextView heading,location,date,next,countdown,summary,footer;
  private final TextView[] labels=new TextView[5],clocks=new TextView[5],details=new TextView[5];
  private final LinearLayout[] rows=new LinearLayout[5];
  private final String[] names={"الفجر","الظهر","العصر","المغرب","العشاء"};
  private final String[] keys={"Fajr","Dhuhr","Asr","Maghrib","Isha"};
  private final Runnable pulse=new Runnable(){public void run(){render();tick.postDelayed(this,1000L);}};
  DetailPanel(Context c){
   super(c);setOrientation(VERTICAL);setFocusable(true);setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
   setPadding(Ui.px(c,20),Ui.px(c,16),Ui.px(c,20),Ui.px(c,16));
   setBackground(surface(0xE51C3045,24,0xCCFFFFFF));
   heading=text(21,true,0xFFFFD995);heading.setText("مواقيت الصلاة");addView(heading,lp(26));
   location=text(13,false,Color.WHITE);addView(location,lp(20));
   date=text(11,false,0xFFCFD8E3);addView(date,lp(20));
   LinearLayout hero=new LinearLayout(c);hero.setOrientation(VERTICAL);hero.setGravity(Gravity.CENTER);
   hero.setBackground(surface(0x663F6A7A,16,0x668CD7DE));
   next=text(17,true,0xFFFFE8A3);hero.addView(next,lp(26));
   countdown=text(29,true,Color.WHITE);countdown.setTextDirection(View.TEXT_DIRECTION_LTR);
   countdown.setTypeface(Typeface.MONOSPACE,Typeface.BOLD);hero.addView(countdown,lp(36));
   summary=text(10,false,0xFFDAE8EF);hero.addView(summary,lp(19));
   LinearLayout.LayoutParams hp=lp(86);hp.topMargin=Ui.px(c,6);hp.bottomMargin=Ui.px(c,10);addView(hero,hp);
   for(int i=0;i<5;i++){
    LinearLayout row=new LinearLayout(c);row.setOrientation(HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);
    row.setPadding(Ui.px(c,12),0,Ui.px(c,12),0);rows[i]=row;
    labels[i]=text(15,true,Color.WHITE);labels[i].setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
    row.addView(labels[i],new LinearLayout.LayoutParams(0,-1,1));
    LinearLayout timeBox=new LinearLayout(c);timeBox.setOrientation(VERTICAL);timeBox.setGravity(Gravity.CENTER);
    clocks[i]=text(17,true,Color.WHITE);clocks[i].setTextDirection(View.TEXT_DIRECTION_LTR);
    details[i]=text(9,false,0xFFCFD8E3);timeBox.addView(clocks[i],lp(22));timeBox.addView(details[i],lp(13));
    row.addView(timeBox,new LinearLayout.LayoutParams(Ui.px(c,126),-1));
    LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,0,1);rp.bottomMargin=Ui.px(c,5);addView(row,rp);
   }
   footer=text(10,false,0xFFCFD8E3);footer.setMaxLines(2);footer.setSingleLine(false);addView(footer,lp(36));
   render();
  }
  private TextView text(float size,boolean bold,int color){
   TextView t=new TextView(getContext());Ui.textSize(t,size);t.setTextColor(color);t.setGravity(Gravity.CENTER);
   t.setSingleLine(true);t.setIncludeFontPadding(false);t.setEllipsize(android.text.TextUtils.TruncateAt.END);
   if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;
  }
  private LinearLayout.LayoutParams lp(int h){return new LinearLayout.LayoutParams(-1,Ui.px(getContext(),h));}
  private GradientDrawable surface(int color,int radius,int stroke){
   GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(Ui.px(getContext(),radius));g.setStroke(Ui.px(getContext(),1),stroke);return g;
  }
  @Override protected void onAttachedToWindow(){
   super.onAttachedToWindow();prefs(getContext()).registerOnSharedPreferenceChangeListener(this);
   tick.removeCallbacks(pulse);pulse.run();refresh(getContext(),null,false);
  }
  @Override protected void onDetachedFromWindow(){
   tick.removeCallbacksAndMessages(null);prefs(getContext()).unregisterOnSharedPreferenceChangeListener(this);super.onDetachedFromWindow();
  }
  @Override public void onSharedPreferenceChanged(SharedPreferences p,String key){render();}
  private void render(){
   SharedPreferences p=prefs(getContext());JSONObject t=new JSONObject(),meta=new JSONObject();
   try{t=new JSONObject(p.getString(K_DATA,"{}"));meta=new JSONObject(p.getString("detail_metadata","{}"));}catch(Exception ignored){}
   String zone=meta.optString("timezone",java.util.TimeZone.getDefault().getID());
   long now=System.currentTimeMillis();java.util.TimeZone tz=java.util.TimeZone.getTimeZone(zone);
   SimpleDateFormat day=new SimpleDateFormat("yyyy-MM-dd",Locale.US);day.setTimeZone(tz);
   String storedDay=meta.optString("day",p.getString("cache_date",""));
   String expectedKey=city(getContext())+"|"+countryApi(getContext())+"|"+p.getString("latitude","")+"|"+p.getString("longitude","");
   boolean fresh=day.format(new Date(now)).equals(storedDay)&&expectedKey.equals(p.getString(K_KEY,""));
   String[] times=new String[5];for(int i=0;i<5;i++)times[i]=t.optString(keys[i],"--:--");
   PrayerPanelClock.Result upcoming=fresh?PrayerPanelClock.next(times,zone,now):null;
   location.setText(city(getContext())+" • "+country(getContext()));
   SimpleDateFormat display=new SimpleDateFormat("EEEE، d MMMM yyyy",new Locale("ar"));display.setTimeZone(tz);
   String hijri=meta.optString("hijri","");date.setText(display.format(new Date(now))+(hijri.isEmpty()?"":" • "+hijri+" هـ"));
   if(upcoming==null){next.setText("الصلاة القادمة");countdown.setText("--:--:--");summary.setText("بانتظار تحديث مواقيت اليوم");}
   else{
    next.setText("الصلاة القادمة • "+names[upcoming.index]+(upcoming.tomorrow?" غداً":""));
    countdown.setText(PrayerPanelClock.countdown(upcoming.at-now));
    summary.setText("المتبقي للأذان • "+PrayerPanelClock.clean(times[upcoming.index])+(upcoming.tomorrow?" • موعد الغد تقديري":""));
   }
   for(int i=0;i<5;i++){
    boolean selected=upcoming!=null&&upcoming.index==i;
    rows[i].setBackground(surface(selected?0x665A6F65:0x353B5A70,10,selected?0xCCFFE8A3:0x223B5A70));
    labels[i].setText(names[i]+(selected?"  • القادمة":""));labels[i].setTextColor(selected?0xFFFFE8A3:Color.WHITE);
    clocks[i].setText(fresh?PrayerPanelClock.clean(times[i]):"--:--");clocks[i].setTextColor(selected?0xFFFFE8A3:Color.WHITE);
    int delay=p.getInt("iqama_"+keys[i],15);
    details[i].setText(p.getBoolean("iqama_enabled",false)&&delay>0?"الإقامة بعد "+delay+" دقيقة":"وقت الأذان");
   }
   footer.setText("الشروق "+(fresh?PrayerPanelClock.clean(t.optString("Sunrise","--:--")):"--:--")+"  •  الأذان "+(p.getBoolean("adhan_enabled",false)?"مفعّل":"متوقف")+
    "\n"+(fresh?"حسب توقيت "+zone:"الاتصال بالإنترنت مطلوب لتحديث المواقيت"));
   // Update the date and fetch the new day without polling the network every second.
   if(!fresh&&now-lastRefresh>60000L){lastRefresh=now;refresh(getContext(),null,false);}
  }
  private long lastRefresh;
 }
 static LinearLayout createView(Context c){
  LinearLayout bar=new LinearLayout(c);bar.setOrientation(LinearLayout.HORIZONTAL);
  bar.setGravity(Gravity.CENTER);bar.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
  bar.setPadding(Ui.px(c,8),Ui.px(c,7),Ui.px(c,8),Ui.px(c,7));
  GradientDrawable bg=new GradientDrawable();
  bg.setColor(0x663F6A7A);bg.setCornerRadius(Ui.px(c,18));
  bg.setStroke(Ui.px(c,1),0xAAE4FAFF);bar.setBackground(bg);
  String[] names={"الفجر","الظهر","العصر","المغرب","العشاء"};
  String[] icons={"☀","☼","☀","☀","☾"};
  for(int i=0;i<5;i++){
   LinearLayout tile=new LinearLayout(c);tile.setOrientation(LinearLayout.VERTICAL);
   tile.setGravity(Gravity.CENTER);
   tile.setPadding(Ui.px(c,3),Ui.px(c,3),Ui.px(c,3),Ui.px(c,3));
   GradientDrawable tileBg=new GradientDrawable();tileBg.setColor(0x66B5D6DF);
   tileBg.setCornerRadius(Ui.px(c,12));tileBg.setStroke(Ui.px(c,1),0x88FFFFFF);
   tile.setBackground(tileBg);
   TextView label=new TextView(c);label.setText(icons[i]+"  "+names[i]);label.setTextColor(Color.WHITE);
   label.setGravity(Gravity.CENTER);label.setSingleLine(true);label.setTextSize(11);label.setEllipsize(android.text.TextUtils.TruncateAt.END);
   TextView time=new TextView(c);time.setText("--:--");time.setTextColor(Color.WHITE);
   time.setTypeface(Typeface.DEFAULT_BOLD);time.setTextSize(14);time.setGravity(Gravity.CENTER);time.setSingleLine(true);
   tile.addView(label,new LinearLayout.LayoutParams(-1,0,1));
   tile.addView(time,new LinearLayout.LayoutParams(-1,0,1));
   LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-1,1);
   lp.setMargins(Ui.px(c,3),0,Ui.px(c,3),0);bar.addView(tile,lp);
  }
  return bar;
 }
 private static void show(LinearLayout view,String raw){
  String[] keys={"Fajr","Dhuhr","Asr","Maghrib","Isha"};
  JSONObject t=null;try{t=new JSONObject(raw);}catch(Exception ignored){}
  for(int i=0;i<5;i++){
   LinearLayout tile=(LinearLayout)view.getChildAt(i);
   TextView time=(TextView)tile.getChildAt(1);
   String value=t==null?"--:--":t.optString(keys[i],"--:--");
   int cut=value.indexOf(" ");if(cut>0)value=value.substring(0,cut);
   time.setText(value);
  }
 }
 static void refresh(Context context,LinearLayout view,boolean force){
  if(view!=null){if(!enabled(context)){view.setVisibility(View.GONE);}else view.setVisibility(View.VISIBLE);}
  SharedPreferences p=prefs(context);String key=city(context)+"|"+countryApi(context)+"|"+prefs(context).getString("latitude","")+"|"+prefs(context).getString("longitude","");
  String cached=p.getString(K_DATA,null);long at=p.getLong(K_AT,0);
  String today=new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(new Date());
  boolean sameDay=today.equals(p.getString("cache_date",""));
  if(view!=null&&cached!=null&&key.equals(p.getString(K_KEY,"")))show(view,cached);
  else if(view!=null)show(view,"{}");
  if(!force&&sameDay&&key.equals(p.getString(K_KEY,""))&&cached!=null) PrayerScheduler.schedule(context);
  if(busy||(!force&&sameDay&&key.equals(p.getString(K_KEY,""))&&System.currentTimeMillis()-at<3600000L))return;
  busy=true;Context app=context.getApplicationContext();
  new Thread(()->{
   String data=null;HttpURLConnection conn=null;
   try{
    SharedPreferences current=prefs(app);
    String latitude=current.getString("latitude","");
    String longitude=current.getString("longitude","");
    String url;
    if(!latitude.isEmpty()&&!longitude.isEmpty()){
     url="https://api.aladhan.com/v1/timings?latitude="+URLEncoder.encode(latitude,"UTF-8")+
       "&longitude="+URLEncoder.encode(longitude,"UTF-8")+"&method=4";
    }else{
     url="https://api.aladhan.com/v1/timingsByCity?city="+URLEncoder.encode(cityApi(app),"UTF-8")+
       "&country="+URLEncoder.encode(countryApi(app),"UTF-8")+"&method=4";
    }
    conn=(HttpURLConnection)new URL(url).openConnection();conn.setConnectTimeout(10000);conn.setReadTimeout(10000);
    conn.setRequestProperty("User-Agent",Weather.UA);
    if(conn.getResponseCode()==200){
     BufferedReader r=new BufferedReader(new InputStreamReader(conn.getInputStream(),"UTF-8"));
     StringBuilder b=new StringBuilder();String line;while((line=r.readLine())!=null)b.append(line);r.close();
     JSONObject root=new JSONObject(b.toString());
     if(root.optInt("code")==200){data=root.getJSONObject("data").getJSONObject("timings").toString();
      JSONObject result=root.getJSONObject("data"),details=new JSONObject();
      JSONObject meta=result.optJSONObject("meta"),date=result.optJSONObject("date");
      if(meta!=null)details.put("timezone",meta.optString("timezone",java.util.TimeZone.getDefault().getID()));
      if(date!=null){
       JSONObject gregorian=date.optJSONObject("gregorian"),hijri=date.optJSONObject("hijri");
       if(gregorian!=null){String d=gregorian.optString("date","");if(d.matches("\\d{2}-\\d{2}-\\d{4}"))details.put("day",d.substring(6)+"-"+d.substring(3,5)+"-"+d.substring(0,2));}
       if(hijri!=null)details.put("hijri",hijri.optString("date",""));
      }
      p.edit().putString("detail_metadata",details.toString()).apply();
      p.edit().putString(K_DATA,data).putLong(K_AT,System.currentTimeMillis()).putString(K_KEY,key).putString("cache_date",new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(new Date())).apply();
      PrayerScheduler.schedule(app);}
    }
   }catch(Exception ignored){}finally{if(conn!=null)conn.disconnect();}
   final String shown=data!=null?data:(key.equals(p.getString(K_KEY,""))?p.getString(K_DATA,null):null);
   new Handler(Looper.getMainLooper()).post(()->{busy=false;if(view!=null&&enabled(view.getContext())){
    view.setVisibility(View.VISIBLE);show(view,shown==null?"{}":shown);
   }});
  },"prayer-times-refresh").start();
 }
 private static String format(Context c,String raw){
  try{
   JSONObject t=new JSONObject(raw);String[] k={"Fajr","Dhuhr","Asr","Maghrib","Isha"};
   String[] n={"الفجر","الظهر","العصر","المغرب","العشاء"};
   StringBuilder s=new StringBuilder("مواقيت الصلاة • ").append(city(c)).append("\\n");
   for(int i=0;i<5;i++){
    if(i>0)s.append("\\n");
    String value=t.optString(k[i],"--").replaceAll("\\\\s*\\\\(.*?\\\\)","");
    s.append(n[i]).append("     ").append(value);
   }
   return s.toString();
  }catch(Exception e){return "مواقيت الصلاة\nتعذر قراءة المواقيت";}
 }
}

/** Pure time calculation shared by the detail view and host-side timing checks. */
final class PrayerPanelClock {
 static final class Result {
  final int index;final long at;final boolean tomorrow;
  Result(int index,long at,boolean tomorrow){this.index=index;this.at=at;this.tomorrow=tomorrow;}
 }
 static String clean(String value){
  if(value==null||!value.matches("(?:[01][0-9]|2[0-3]):[0-5][0-9](?: .*)?"))return "--:--";
  return value.substring(0,5);
 }
 static Result next(String[] times,String zone,long now){
  if(times==null||times.length!=5)return null;
  java.util.TimeZone tz=java.util.TimeZone.getTimeZone(zone);
  Result result=null;
  for(int i=0;i<5;i++){
   String value=clean(times[i]);if(value.equals("--:--"))continue;
   java.util.Calendar when=java.util.Calendar.getInstance(tz);when.setTimeInMillis(now);
   when.set(java.util.Calendar.HOUR_OF_DAY,Integer.parseInt(value.substring(0,2)));
   when.set(java.util.Calendar.MINUTE,Integer.parseInt(value.substring(3,5)));
   when.set(java.util.Calendar.SECOND,0);when.set(java.util.Calendar.MILLISECOND,0);
   if(when.getTimeInMillis()>=now&&(result==null||when.getTimeInMillis()<result.at))result=new Result(i,when.getTimeInMillis(),false);
  }
  if(result!=null)return result;
  String fajr=clean(times[0]);if(fajr.equals("--:--"))return null;
  java.util.Calendar tomorrow=java.util.Calendar.getInstance(tz);tomorrow.setTimeInMillis(now);
  tomorrow.add(java.util.Calendar.DAY_OF_MONTH,1);
  tomorrow.set(java.util.Calendar.HOUR_OF_DAY,Integer.parseInt(fajr.substring(0,2)));
  tomorrow.set(java.util.Calendar.MINUTE,Integer.parseInt(fajr.substring(3,5)));
  tomorrow.set(java.util.Calendar.SECOND,0);tomorrow.set(java.util.Calendar.MILLISECOND,0);
  return new Result(0,tomorrow.getTimeInMillis(),true);
 }
 static String countdown(long millis){
  long seconds=Math.max(0,(millis+999)/1000);
  return String.format(java.util.Locale.US,"%02d:%02d:%02d",seconds/3600,(seconds/60)%60,seconds%60);
 }
}
