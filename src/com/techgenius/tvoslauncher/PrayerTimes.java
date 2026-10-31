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
  bar.setGravity(Gravity.CENTER);bar.setPadding(Ui.px(c,5),Ui.px(c,5),Ui.px(c,5),Ui.px(c,5));
  bar.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
  GradientDrawable bg=new GradientDrawable();bg.setColor(0xE51B2633);
  bg.setCornerRadius(Ui.px(c,14));bg.setStroke(Ui.px(c,1),0x668FD6C4);bar.setBackground(bg);
  String[] names={"الفجر","الظهر","العصر","المغرب","العشاء"};
  for(String name:names){
   TextView cell=new TextView(c);cell.setText(name+"\\n--:--");cell.setTextColor(Color.WHITE);
   cell.setTextSize(12);cell.setGravity(Gravity.CENTER);cell.setMaxLines(2);
   cell.setPadding(Ui.px(c,4),Ui.px(c,7),Ui.px(c,4),Ui.px(c,7));
   GradientDrawable tile=new GradientDrawable();tile.setColor(0xB211213A);
   tile.setCornerRadius(Ui.px(c,9));tile.setStroke(Ui.px(c,1),0x55487FBC);
   cell.setBackground(tile);
   LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-1,1f);
   lp.setMargins(Ui.px(c,2),0,Ui.px(c,2),0);bar.addView(cell,lp);
  }
  return bar;
 }
 private static void show(LinearLayout view,String raw){
  String[] names={"الفجر","الظهر","العصر","المغرب","العشاء"};
  String[] keys={"Fajr","Dhuhr","Asr","Maghrib","Isha"};
  JSONObject t=null;try{t=new JSONObject(raw);}catch(Exception ignored){}
  for(int i=0;i<5;i++){
   TextView cell=(TextView)view.getChildAt(i);
   String time=t==null?"--:--":t.optString(keys[i],"--:--").replaceAll("\\\\s*\\\\(.*?\\\\)","");
   cell.setText(names[i]+"\\n"+time);
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