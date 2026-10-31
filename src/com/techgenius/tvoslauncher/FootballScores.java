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
 static boolean enabled(Context c){return prefs(c).getBoolean("enabled",false);}
 static void setEnabled(Context c,boolean value){prefs(c).edit().putBoolean("enabled",value).apply();}
 static java.util.Set<String> selectedLeagues(Context c){
  return new java.util.HashSet<>(prefs(c).getStringSet("selected_leagues",java.util.Collections.singleton("ksa.1")));
 }
 static boolean isLeagueSelected(Context c,String id){return selectedLeagues(c).contains(id);}
 static void setLeagues(Context c,java.util.Set<String> ids){
  prefs(c).edit().putStringSet("selected_leagues",new java.util.HashSet<>(ids)).remove(K_AT).apply();
 }
 static String selectedLeagueNames(Context c){
  java.util.List<String> names=new java.util.ArrayList<>();
  for(String[] l:LEAGUES)if(isLeagueSelected(c,l[0]))names.add(l[1]);
  return names.isEmpty()?"لم يتم اختيار دوريات":android.text.TextUtils.join("، ",names);
 }
 static String apiKey(Context c){return prefs(c).getString("api_football_key","");}
 static void setApiKey(Context c,String key){prefs(c).edit().putString("api_football_key",key).remove(K_AT).apply();}
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
  if(!enabled(app)){view.setVisibility(android.view.View.GONE);return;}
  view.setVisibility(android.view.View.VISIBLE);
  java.util.Set<String> selected=selectedLeagues(app);
  if(selected.isEmpty()){view.setText("المباريات القادمة\\nاختر الدوريات من الإعدادات");return;}
  String key=android.text.TextUtils.join("|",new java.util.TreeSet<>(selected));
  String cached=p.getString(K_DATA,null);long at=p.getLong(K_AT,0);
  view.setText(cached!=null&&key.equals(p.getString("cache_key",""))?cached:"المباريات القادمة\\nجارٍ تحميل المباريات…");
  if(busy||(!force&&key.equals(p.getString("cache_key",""))&&System.currentTimeMillis()-at<CACHE_MS))return;
  busy=true;new Thread(()->{
   String result;
   String token=apiKey(app);
   if(token.isEmpty()){
    result="المباريات القادمة\\nأضف مفتاح API-FOOTBALL من الإعدادات";
   }else{
    java.util.List<String> lines=new java.util.ArrayList<>();
    boolean success=false;
    for(String[] league:LEAGUES){
     if(!selected.contains(league[0]))continue;
     int leagueId=apiLeague(league[0]);
     if(leagueId==0)continue;
     HttpURLConnection conn=null;
     try{
      java.text.SimpleDateFormat day=new java.text.SimpleDateFormat("yyyy-MM-dd",Locale.US);
      day.setTimeZone(TimeZone.getTimeZone("Asia/Riyadh"));
      String from=day.format(new Date());
      String until=day.format(new Date(System.currentTimeMillis()+7L*86400000L));
      String endpoint="https://v3.football.api-sports.io/fixtures?league="+leagueId+"&season="+season(league[0])+"&from="+from+"&to="+until+"&timezone=Asia%2FRiyadh";
      conn=(HttpURLConnection)new URL(endpoint).openConnection();
      conn.setConnectTimeout(10000);conn.setReadTimeout(10000);
      conn.setRequestProperty("x-apisports-key",token);
      if(conn.getResponseCode()!=200)continue;
      BufferedReader reader=new BufferedReader(new InputStreamReader(conn.getInputStream(),"UTF-8"));
      StringBuilder body=new StringBuilder();String line;
      while((line=reader.readLine())!=null)body.append(line);reader.close();
      JSONObject root=new JSONObject(body.toString());
      JSONObject errors=root.optJSONObject("errors");
      if(errors!=null&&errors.length()>0)continue;
      success=true;
      JSONArray events=root.optJSONArray("response");
      if(events==null)continue;
      for(int i=0;i<events.length();i++){
       JSONObject ev=events.optJSONObject(i);if(ev==null)continue;
       JSONObject fixture=ev.optJSONObject("fixture"),teams=ev.optJSONObject("teams"),goals=ev.optJSONObject("goals");
       if(fixture==null||teams==null)continue;
       JSONObject home=teams.optJSONObject("home"),away=teams.optJSONObject("away");
       if(home==null||away==null)continue;
       String when=date(fixture.optString("date",""));
       String score=goals!=null&&!goals.isNull("home")?goals.optString("home")+" - "+goals.optString("away"):when;
       lines.add(fixture.optString("date","")+"|"+league[1]+" • "+score+"\\n"+home.optString("name")+"  ×  "+away.optString("name"));
      }
     }catch(Exception ignored){}finally{if(conn!=null)conn.disconnect();}
    }
    java.util.Collections.sort(lines);
    StringBuilder out=new StringBuilder("المباريات القادمة");
    for(int i=0;i<Math.min(5,lines.size());i++){
     String entry=lines.get(i);out.append("\\n──────────────\\n").append(entry.substring(entry.indexOf('|')+1));
    }
    if(lines.isEmpty())out.append(success?"\\nلا توجد مباريات في الأيام السبعة القادمة":"\\nتعذر جلب البيانات؛ تحقق من المفتاح والاشتراك والدوريات");
    result=out.toString();
    if(success)p.edit().putString(K_DATA,result).putLong(K_AT,System.currentTimeMillis()).putString("cache_key",key).apply();
   }
   final String display=result;new Handler(Looper.getMainLooper()).post(()->{busy=false;if(view!=null)view.setText(display);});
  },"football-score-refresh").start();
 }
 private static int apiLeague(String id){
  switch(id){case "ksa.1":return 307;case "eng.1":return 39;case "esp.1":return 140;
   case "ita.1":return 135;case "fra.1":return 61;case "afc.champions":return 17;default:return 0;}
 }
 private static int season(String id){
  java.util.Calendar c=java.util.Calendar.getInstance(TimeZone.getTimeZone("Asia/Riyadh"));
  int year=c.get(java.util.Calendar.YEAR),month=c.get(java.util.Calendar.MONTH);
  return month>=java.util.Calendar.JULY?year:year-1;
 }
 private static String name(JSONObject c){JSONObject t=c.optJSONObject("team");return t==null?"فريق":t.optString("displayName","فريق");}
 private static String date(String raw){try{SimpleDateFormat in=new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'",Locale.US);in.setTimeZone(TimeZone.getTimeZone("UTC"));Date d=in.parse(raw.substring(0,16)+"Z");SimpleDateFormat out=new SimpleDateFormat("EEE HH:mm",new Locale("ar"));out.setTimeZone(TimeZone.getTimeZone("Asia/Riyadh"));return out.format(d);}catch(Exception e){return "";}}
}