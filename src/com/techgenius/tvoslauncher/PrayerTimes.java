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
   TextView label=new TextView(c);label.setText(names[i]);label.setTextColor(Color.WHITE);
   label.setGravity(Gravity.CENTER);label.setSingleLine(true);label.setTextDirection(View.TEXT_DIRECTION_RTL);label.setTextSize(12);label.setEllipsize(android.text.TextUtils.TruncateAt.END);
   TextView time=new TextView(c);time.setText("--:--");time.setTextColor(Color.WHITE);
   time.setTypeface(Typeface.DEFAULT_BOLD);time.setTextSize(14);time.setTextDirection(View.TEXT_DIRECTION_LTR);time.setGravity(Gravity.CENTER);time.setSingleLine(true);
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
   // Keep the existing card size and highlight the next upcoming prayer.
   try {
    java.text.SimpleDateFormat clock=new java.text.SimpleDateFormat("HH:mm",java.util.Locale.US);
    java.util.Date prayer=clock.parse(value);
    java.util.Date now=clock.parse(clock.format(new java.util.Date()));
    boolean upcoming=prayer!=null&&now!=null&&prayer.after(now);
    boolean firstUpcoming=upcoming;
    for(int j=0;j<i;j++){
     String earlier=t.optString(keys[j],"--:--");
     if(earlier.length()>=5){java.util.Date e=clock.parse(earlier.substring(0,5));if(e!=null&&e.after(now)){firstUpcoming=false;break;}}
    }
    time.setTextColor(firstUpcoming?0xFFFFE8A3:Color.WHITE);
   }catch(Exception ignored){time.setTextColor(Color.WHITE);}
  }
 }
 static void refresh(Context context,LinearLayout view,boolean force){
  if(view==null)return;if(!enabled(context)){view.setVisibility(View.GONE);return;}view.setVisibility(View.VISIBLE);
  SharedPreferences p=prefs(context);String key=city(context)+"|"+countryApi(context);
  String cached=p.getString(K_DATA,null);long at=p.getLong(K_AT,0);
  if(cached!=null&&key.equals(p.getString(K_KEY,"")))show(view,cached);
  else show(view,"{}");
  if(busy||(!force&&key.equals(p.getString(K_KEY,""))&&System.currentTimeMillis()-at<3600000L))return;
  busy=true;Context app=context.getApplicationContext();
  new Thread(()->{
   String data=null;HttpURLConnection conn=null;
   try{
    String url="https://api.aladhan.com/v1/timingsByCity?city="+URLEncoder.encode(cityApi(app),"UTF-8")+
      "&country="+URLEncoder.encode(countryApi(app),"UTF-8")+"&method=4";
    conn=(HttpURLConnection)new URL(url).openConnection();conn.setConnectTimeout(10000);conn.setReadTimeout(10000);
    conn.setRequestProperty("User-Agent",Weather.UA);
    if(conn.getResponseCode()==200){
     BufferedReader r=new BufferedReader(new InputStreamReader(conn.getInputStream(),"UTF-8"));
     StringBuilder b=new StringBuilder();String line;while((line=r.readLine())!=null)b.append(line);r.close();
     JSONObject root=new JSONObject(b.toString());
     if(root.optInt("code")==200){data=root.getJSONObject("data").getJSONObject("timings").toString();
      p.edit().putString(K_DATA,data).putLong(K_AT,System.currentTimeMillis()).putString(K_KEY,key).apply();}
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