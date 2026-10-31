package com.techgenius.tvoslauncher;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/** Football scores filtered by the selected league and team. */
final class FootballScores {
 private static final String PREFS="football_scores",K_DATA="data",K_AT="at",K_LEAGUE="league",K_TEAM_ID="team_id",K_TEAM="team";
 private static final long CACHE_MS=600000L;private static volatile boolean busy;
 static final String[][] LEAGUES={{"ksa.1","دوري روشن السعودي"},{"eng.1","الدوري الإنجليزي"},{"esp.1","الدوري الإسباني"},{"ita.1","الدوري الإيطالي"},{"fra.1","الدوري الفرنسي"},{"afc.champions","دوري أبطال آسيا للنخبة"}};
 static final class Team{final String id,name;Team(String i,String n){id=i;name=n;}}
 interface TeamsCallback{void onLoaded(List<Team> teams,String error);}
 private static final class Match{String date,home,away,homeId,awayId,hs,as,status;}
 private FootballScores(){}
 static SharedPreferences prefs(Context c){return c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);}
 static String league(Context c){return prefs(c).getString(K_LEAGUE,"ksa.1");}
 static String leagueName(Context c){for(String[] x:LEAGUES)if(x[0].equals(league(c)))return x[1];return LEAGUES[0][1];}
 static String teamId(Context c){return prefs(c).getString(K_TEAM_ID,"");}
 static String teamName(Context c){return prefs(c).getString(K_TEAM,"");}
 static void setLeague(Context c,String id){prefs(c).edit().putString(K_LEAGUE,id).remove(K_TEAM_ID).remove(K_TEAM).remove(K_AT).apply();}
 static void setTeam(Context c,Team t){prefs(c).edit().putString(K_TEAM_ID,t.id).putString(K_TEAM,t.name).remove(K_AT).apply();}
 static void loadTeams(Context c,String league,TeamsCallback cb){
  Context app=c.getApplicationContext();new Thread(()->{
   List<Team> out=new ArrayList<>();String error=null;HttpURLConnection conn=null;
   try{
    conn=(HttpURLConnection)new URL("https://site.api.espn.com/apis/site/v2/sports/soccer/"+league+"/teams?limit=1000").openConnection();
    conn.setConnectTimeout(10000);conn.setReadTimeout(10000);conn.setRequestProperty("User-Agent",Weather.UA);
    if(conn.getResponseCode()!=200)throw new Exception("HTTP");
    BufferedReader r=new BufferedReader(new InputStreamReader(conn.getInputStream(),"UTF-8"));StringBuilder b=new StringBuilder();String line;
    while((line=r.readLine())!=null)b.append(line);r.close();JSONObject root=new JSONObject(b.toString());
    JSONArray sports=root.optJSONArray("sports");
    if(sports!=null)for(int i=0;i<sports.length();i++){JSONArray leagues=sports.optJSONObject(i).optJSONArray("leagues");if(leagues==null)continue;
     for(int j=0;j<leagues.length();j++){JSONArray teams=leagues.optJSONObject(j).optJSONArray("teams");if(teams==null)continue;
      for(int k=0;k<teams.length();k++){JSONObject e=teams.optJSONObject(k);JSONObject t=e==null?null:e.optJSONObject("team");if(t==null)t=e;
       if(t!=null){String id=t.optString("id",""),name=t.optString("displayName","");if(!id.isEmpty()&&!name.isEmpty())out.add(new Team(id,name));}}
     }}
    JSONArray direct=root.optJSONArray("teams");
    if(out.isEmpty()&&direct!=null)for(int i=0;i<direct.length();i++){JSONObject e=direct.optJSONObject(i);JSONObject t=e==null?null:e.optJSONObject("team");if(t==null)t=e;
     if(t!=null){String id=t.optString("id",""),name=t.optString("displayName","");if(!id.isEmpty()&&!name.isEmpty())out.add(new Team(id,name));}}
    if(out.isEmpty())error="لم تصل قائمة الفرق من مزود البيانات";
    Collections.sort(out,Comparator.comparing(t->t.name));
   }catch(Exception e){error="تعذر تحميل الفرق • تحقق من الإنترنت";}finally{if(conn!=null)conn.disconnect();}
   final String err=error;new Handler(Looper.getMainLooper()).post(()->cb.onLoaded(out,err));
  },"football-team-list").start();
 }
 static TextView createView(Context c){
  TextView v=new TextView(c);v.setTextColor(Color.WHITE);v.setTextSize(13);v.setGravity(Gravity.CENTER_VERTICAL);
  v.setPadding(Ui.px(c,16),Ui.px(c,10),Ui.px(c,16),Ui.px(c,10));v.setTypeface(Typeface.create("sans-serif-medium",0));
  v.setMaxLines(6);v.setLineSpacing(Ui.px(c,2),1f);GradientDrawable bg=new GradientDrawable();
  bg.setColor(0xE51B2633);bg.setCornerRadius(Ui.px(c,15));bg.setStroke(Ui.px(c,1),0x667DA7D8);
  v.setBackground(bg);v.setFocusable(false);v.setClickable(false);return v;
 }
 static void refresh(Context c,TextView view,boolean force){
  if(view==null)return;Context app=c.getApplicationContext();SharedPreferences p=prefs(app);
  String lg=league(app),tid=teamId(app),tn=teamName(app);
  if(tid.isEmpty()){view.setText("المباريات\nاختر الدوري ثم الفريق من الإعدادات");return;}
  String key=lg+"|"+tid,cached=p.getString(K_DATA,null);long at=p.getLong(K_AT,0);
  if(cached!=null&&key.equals(p.getString("cache_key","")))view.setText(cached);else view.setText("مباريات "+tn+"\nجارٍ تحميل النتائج والمواعيد…");
  if(busy||(!force&&key.equals(p.getString("cache_key",""))&&System.currentTimeMillis()-at<CACHE_MS))return;
  busy=true;new Thread(()->{
   String result;HttpURLConnection conn=null;
   try{
    SimpleDateFormat day=new SimpleDateFormat("yyyyMMdd",Locale.US);day.setTimeZone(TimeZone.getTimeZone("Asia/Riyadh"));
    String from=day.format(new Date()),until=day.format(new Date(System.currentTimeMillis()+30L*86400000L));
    String url="https://site.api.espn.com/apis/site/v2/sports/soccer/"+lg+"/scoreboard?dates="+from+"-"+until;
    conn=(HttpURLConnection)new URL(url).openConnection();conn.setConnectTimeout(10000);conn.setReadTimeout(10000);
    conn.setRequestProperty("User-Agent",Weather.UA);if(conn.getResponseCode()!=200)throw new Exception("scoreboard");
    BufferedReader r=new BufferedReader(new InputStreamReader(conn.getInputStream(),"UTF-8"));StringBuilder b=new StringBuilder();String line;
    while((line=r.readLine())!=null)b.append(line);r.close();JSONArray events=new JSONObject(b.toString()).optJSONArray("events");
    List<Match> matches=new ArrayList<>();
    if(events!=null)for(int i=0;i<events.length();i++){JSONObject ev=events.optJSONObject(i);JSONArray cs=ev==null?null:ev.optJSONArray("competitions");
     JSONObject cp=cs==null||cs.length()==0?null:cs.optJSONObject(0);JSONArray comps=cp==null?null:cp.optJSONArray("competitors");
     if(comps==null||comps.length()<2)continue;JSONObject a=comps.optJSONObject(0),z=comps.optJSONObject(1);if(a==null||z==null)continue;
     Match m=new Match();m.date=ev.optString("date","");m.home=name(a);m.away=name(z);m.homeId=a.optString("id","");m.awayId=z.optString("id","");
     if(!tid.equals(m.homeId)&&!tid.equals(m.awayId))continue;m.hs=a.optString("score","-");m.as=z.optString("score","-");
     JSONObject st=ev.optJSONObject("status"),ty=st==null?null:st.optJSONObject("type");m.status=ty==null?"":ty.optString("shortDetail","");matches.add(m);}
    Collections.sort(matches,Comparator.comparing(m->m.date==null?"":m.date));
    StringBuilder out=new StringBuilder("مباريات ").append(tn);
    if(matches.isEmpty())out.append("\nلا توجد مباريات مجدولة للفريق خلال الثلاثين يوماً القادمة");
    for(int i=0;i<Math.min(5,matches.size());i++){Match m=matches.get(i);out.append("\n").append(m.home).append("  ").append(m.hs).append(" - ").append(m.as).append("  ").append(m.away);
     if(!m.status.isEmpty())out.append(" • ").append(m.status);else if(m.date.length()>=16)out.append(" • ").append(date(m.date));}
    result=out.toString();p.edit().putString(K_DATA,result).putLong(K_AT,System.currentTimeMillis()).putString("cache_key",key).apply();
   }catch(Exception e){result="مباريات "+tn+"\nتعذر جلب المباريات • تحقق من الإنترنت";}finally{if(conn!=null)conn.disconnect();}
   final String display=result;new Handler(Looper.getMainLooper()).post(()->{busy=false;if(view!=null)view.setText(display);});
  },"football-score-refresh").start();
 }
 private static String name(JSONObject c){JSONObject t=c.optJSONObject("team");return t==null?"فريق":t.optString("displayName","فريق");}
 private static String date(String raw){try{SimpleDateFormat in=new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'",Locale.US);in.setTimeZone(TimeZone.getTimeZone("UTC"));Date d=in.parse(raw.substring(0,16)+"Z");SimpleDateFormat out=new SimpleDateFormat("EEE h:mm a",new Locale("ar"));out.setTimeZone(TimeZone.getTimeZone("Asia/Riyadh"));return out.format(d);}catch(Exception e){return "";}}
}