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
 static void setLocation(Context c,String city,String country,String countryApi){
  prefs(c).edit().putString(K_CITY,city).putString(K_COUNTRY,country).putString("country_api",countryApi).remove(K_AT).apply();
 }
 static TextView createView(Context c){
  TextView v=new TextView(c);v.setTextColor(Color.WHITE);v.setTextSize(13);v.setGravity(Gravity.CENTER_VERTICAL);
  v.setPadding(Ui.px(c,15),Ui.px(c,10),Ui.px(c,15),Ui.px(c,10));
  v.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));v.setMaxLines(5);
  v.setLineSpacing(Ui.px(c,3),1f);
  GradientDrawable bg=new GradientDrawable();bg.setColor(0xE51B2633);bg.setCornerRadius(Ui.px(c,17));
  bg.setStroke(Ui.px(c,1),0x668FD6C4);v.setBackground(bg);v.setFocusable(false);v.setClickable(false);return v;
 }
 static void refresh(Context context,TextView view,boolean force){
  if(view==null)return;if(!enabled(context)){view.setVisibility(View.GONE);return;}view.setVisibility(View.VISIBLE);
  SharedPreferences p=prefs(context);String key=city(context)+"|"+countryApi(context);
  String cached=p.getString(K_DATA,null);long at=p.getLong(K_AT,0);
  if(cached!=null&&key.equals(p.getString(K_KEY,"")))view.setText(format(context,cached));
  else view.setText("مواقيت الصلاة\n"+city(context)+" • جارٍ جلب المواقيت…");
  if(busy||(!force&&key.equals(p.getString(K_KEY,""))&&System.currentTimeMillis()-at<3600000L))return;
  busy=true;Context app=context.getApplicationContext();
  new Thread(()->{
   String data=null;HttpURLConnection conn=null;
   try{
    String url="https://api.aladhan.com/v1/timingsByCity?city="+URLEncoder.encode(city(app),"UTF-8")+
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
    view.setVisibility(View.VISIBLE);view.setText(shown==null?"مواقيت الصلاة\nتعذر جلب المواقيت • تحقق من المدينة والإنترنت":format(view.getContext(),shown));
   }});
  },"prayer-times-refresh").start();
 }
 private static String format(Context c,String raw){
  try{
   JSONObject t=new JSONObject(raw);String[] k={"Fajr","Dhuhr","Asr","Maghrib","Isha"};
   String[] n={"الفجر","الظهر","العصر","المغرب","العشاء"};
   StringBuilder s=new StringBuilder("مواقيت الصلاة • ").append(city(c)).append("\n");
   for(int i=0;i<5;i++){if(i==2)s.append("\n");else if(i>0)s.append("    ");
    s.append(n[i]).append(" ").append(t.optString(k[i],"--").replaceAll("\\s*\\(.*?\\)",""));}
   return s.toString();
  }catch(Exception e){return "مواقيت الصلاة\nتعذر قراءة المواقيت";}
 }
}