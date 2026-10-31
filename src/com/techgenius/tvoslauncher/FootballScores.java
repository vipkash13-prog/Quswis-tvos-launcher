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
 private static final long CACHE_MS=120000L;private static volatile boolean busy;
 static final String[][] LEAGUES={{"ksa.1","دوري روشن السعودي"},{"eng.1","الدوري الإنجليزي"},{"esp.1","الدوري الإسباني"},{"ita.1","الدوري الإيطالي"},{"fra.1","الدوري الفرنسي"},{"ger.1","الدوري الألماني"},{"afc.champions","دوري أبطال آسيا للنخبة"},{"uefa.champions","دوري أبطال أوروبا"}};
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
  prefs(c).edit().putStringSet("selected_leagues",new java.util.HashSet<>(ids)).remove("fixtures_json").remove("cache_key").remove(K_AT).apply();
 }
 static String selectedLeagueNames(Context c){
  java.util.List<String> names=new java.util.ArrayList<>();
  for(String[] l:LEAGUES)if(isLeagueSelected(c,l[0]))names.add(l[1]);
  return names.isEmpty()?"لم يتم اختيار دوريات":android.text.TextUtils.join("، ",names);
 }
 static java.util.Set<String> selectedTeams(Context c,String league){return new java.util.HashSet<>(prefs(c).getStringSet("teams_"+league,java.util.Collections.emptySet()));}
 static void setSelectedTeams(Context c,String league,java.util.Set<String> ids){prefs(c).edit().putStringSet("teams_"+league,new java.util.HashSet<>(ids)).remove("fixtures_json").remove(K_AT).apply();}
 static String arabicTeam(String name){
  if(name==null)return "";
  String n=name.toLowerCase(Locale.US).replaceAll("[^a-z0-9]","");
  String[][] pairs={{"Al Hilal","الهلال"},{"Al Nassr","النصر"},{"Al Ahli","الأهلي"},{"Al Ittihad","الاتحاد"},{"Al Fateh","الفتح"},{"Al Taawoun","التعاون"},{"Al Ettifaq","الاتفاق"},{"Al Fayha","الفيحاء"},{"Al Khaleej","الخليج"},{"Al Kholood","الخلود"},{"Al Shabab","الشباب"},{"Al Qadsiah","القادسية"},{"Al Riyadh","الرياض"},{"Al Hazem","الحزم"},{"Al Okhdood","الأخدود"},{"Damac","ضمك"},{"Abha","أبها"},{"Manchester United","مانشستر يونايتد"},{"Manchester City","مانشستر سيتي"},{"Liverpool","ليفربول"},{"Arsenal","أرسنال"},{"Chelsea","تشيلسي"},{"Tottenham","توتنهام"},{"Newcastle","نيوكاسل"},{"Everton","إيفرتون"},{"Aston Villa","أستون فيلا"},{"West Ham","وست هام"},{"Brighton","برايتون"},{"Brentford","برينتفورد"},{"Fulham","فولهام"},{"Wolverhampton","وولفرهامبتون"},{"Crystal Palace","كريستال بالاس"},{"Nottingham Forest","نوتنغهام فورست"},{"Bournemouth","بورنموث"},{"Leeds United","ليدز يونايتد"},{"Sunderland","سندرلاند"},{"Burnley","بيرنلي"},{"Real Madrid","ريال مدريد"},{"Barcelona","برشلونة"},{"Atletico Madrid","أتلتيكو مدريد"},{"Bayern Munich","بايرن ميونخ"},{"Borussia Dortmund","بوروسيا دورتموند"},{"Inter Milan","إنتر ميلان"},{"AC Milan","ميلان"},{"Juventus","يوفنتوس"},{"Paris Saint-Germain","باريس سان جيرمان"}};
  for(String[] x:pairs)if(n.equals(x[0].toLowerCase(Locale.US).replaceAll("[^a-z0-9]","")))return x[1];
  String[][] extra={{"Bologna","بولونيا"},{"Monza","مونزا"},{"VfB Stuttgart","شتوتغارت"},{"Bayer Leverkusen","باير ليفركوزن"},{"SC Paderborn 07","بادربورن"},{"Borussia Monchengladbach","بوروسيا مونشنغلادباخ"},{"Borussia Mönchengladbach","بوروسيا مونشنغلادباخ"},{"SC Freiburg","فرايبورغ"},{"FC Augsburg","أوغسبورغ"},{"Al Taawoun","التعاون"},{"Al Ettifaq","الاتفاق"},{"Al Feiha","الفيحاء"},{"Al Fayha","الفيحاء"},{"Al Fateh","الفتح"},{"Al Fath","الفتح"},{"Al Khaleej","الخليج"},{"Al Kholood","الخلود"},{"Al Shabab","الشباب"},{"Al Faisaly","الفيصلي"},{"Al Hilal","الهلال"},{"Al Nassr","النصر"},{"Al Ahly","الأهلي"},{"Al Ahli","الأهلي"},{"Al Ittihad","الاتحاد"},{"Al Qadsiah","القادسية"},{"Al Akhdoud","الأخدود"},{"Al Okhdood","الأخدود"},{"Al Raed","الرائد"},{"Al Wehda","الوحدة"},{"Al Riyadh","الرياض"},{"Al Hazm","الحزم"},{"Al Hazem","الحزم"},{"Damac FC","ضمك"},{"Ipswich Town","إيبسويتش تاون"},{"Hull City","هال سيتي"},{"Coventry City","كوفنتري سيتي"}};
  for(String[] x:extra)if(n.equals(x[0].toLowerCase(Locale.US).replaceAll("[^a-z0-9]","")))return x[1];
  return name;
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
       if(t!=null){String id=t.optString("id",""),name=t.optString("displayName","");if(!id.isEmpty()&&!name.isEmpty())out.add(new Team(id,arabicTeam(name)));}}
     }}
    JSONArray direct=root.optJSONArray("teams");
    if(out.isEmpty()&&direct!=null)for(int i=0;i<direct.length();i++){JSONObject e=direct.optJSONObject(i);JSONObject t=e==null?null:e.optJSONObject("team");if(t==null)t=e;
     if(t!=null){String id=t.optString("id",""),name=t.optString("displayName","");if(!id.isEmpty()&&!name.isEmpty())out.add(new Team(id,arabicTeam(name)));}}
    if(out.isEmpty())error="لم تصل قائمة الفرق من مزود البيانات";
    Collections.sort(out,Comparator.comparing(t->t.name));
   }catch(Exception e){error="تعذر تحميل الفرق • تحقق من الإنترنت";}finally{if(conn!=null)conn.disconnect();}
   final String err=error;new Handler(Looper.getMainLooper()).post(()->cb.onLoaded(out,err));
  },"football-team-list").start();
 }
 static android.widget.LinearLayout createView(Context c){
  android.widget.LinearLayout panel=new android.widget.LinearLayout(c);
  panel.setOrientation(android.widget.LinearLayout.VERTICAL);
  panel.setPadding(Ui.px(c,14),Ui.px(c,10),Ui.px(c,14),Ui.px(c,12));
  GradientDrawable glass=new GradientDrawable();glass.setColor(0x554C7B91);
  glass.setCornerRadius(Ui.px(c,22));glass.setStroke(Ui.px(c,1),0xAADDFAFF);
  panel.setBackground(glass);
  TextView title=new TextView(c);title.setText("المباريات القادمة");title.setTextSize(18);
  title.setTypeface(Typeface.DEFAULT_BOLD);title.setTextColor(Color.WHITE);
  title.setGravity(Gravity.CENTER);panel.addView(title,new android.widget.LinearLayout.LayoutParams(-1,Ui.px(c,40)));
  for(int i=0;i<5;i++){
   android.widget.LinearLayout line=new android.widget.LinearLayout(c);
   line.setOrientation(android.widget.LinearLayout.HORIZONTAL);
   line.setLayoutDirection(android.view.View.LAYOUT_DIRECTION_RTL);
   line.setGravity(Gravity.CENTER_VERTICAL);
   line.setPadding(Ui.px(c,8),Ui.px(c,4),Ui.px(c,8),Ui.px(c,4));
   GradientDrawable bg=new GradientDrawable();bg.setColor(0x66557E94);
   bg.setCornerRadius(Ui.px(c,12));bg.setStroke(Ui.px(c,1),0x77FFFFFF);
   line.setBackground(bg);
   android.widget.ImageView home=new android.widget.ImageView(c),away=new android.widget.ImageView(c);
   home.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
   away.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
   TextView homeName=new TextView(c),awayName=new TextView(c),time=new TextView(c);
   for(TextView t:new TextView[]{homeName,awayName,time}){
    t.setTextColor(Color.WHITE);t.setTextSize(12);t.setSingleLine(true);
    t.setEllipsize(android.text.TextUtils.TruncateAt.END);t.setGravity(Gravity.CENTER);
   }
   time.setTypeface(Typeface.DEFAULT_BOLD);time.setTextSize(11);time.setSingleLine(false);time.setMaxLines(2);
   line.addView(home,new android.widget.LinearLayout.LayoutParams(Ui.px(c,38),Ui.px(c,38)));
   line.addView(homeName,new android.widget.LinearLayout.LayoutParams(0,-2,1));
   line.addView(time,new android.widget.LinearLayout.LayoutParams(Ui.px(c,165),-2));
   line.addView(awayName,new android.widget.LinearLayout.LayoutParams(0,-2,1));
   line.addView(away,new android.widget.LinearLayout.LayoutParams(Ui.px(c,38),Ui.px(c,38)));
   android.widget.LinearLayout.LayoutParams lp=new android.widget.LinearLayout.LayoutParams(-1,0,1);
   lp.bottomMargin=Ui.px(c,5);panel.addView(line,lp);
  }
  showMessage(panel,"جارٍ جلب المباريات...");
  return panel;
 }
 private static void showMessage(android.widget.LinearLayout panel,String msg){
  for(int i=0;i<5;i++){
   android.widget.LinearLayout line=(android.widget.LinearLayout)panel.getChildAt(i+1);
   ((android.widget.ImageView)line.getChildAt(0)).setImageDrawable(null);
   ((android.widget.ImageView)line.getChildAt(4)).setImageDrawable(null);
   ((TextView)line.getChildAt(1)).setText("");
   ((TextView)line.getChildAt(2)).setText(i==0?msg:"");
   ((TextView)line.getChildAt(3)).setText("");
   line.setVisibility(i==0?android.view.View.VISIBLE:android.view.View.INVISIBLE);
  }
 }
 private static void showFixtures(android.widget.LinearLayout panel,java.util.List<JSONObject> matches){
  for(int i=0;i<5;i++){
   android.widget.LinearLayout line=(android.widget.LinearLayout)panel.getChildAt(i+1);
   if(i>=matches.size()){line.setVisibility(android.view.View.INVISIBLE);continue;}
   line.setVisibility(android.view.View.VISIBLE);
   JSONObject item=matches.get(i);
   JSONObject teams=item.optJSONObject("teams"),fixture=item.optJSONObject("fixture"),goals=item.optJSONObject("goals");
   JSONObject home=teams==null?null:teams.optJSONObject("home"),away=teams==null?null:teams.optJSONObject("away");
   if(home==null||away==null)continue;
   String when=fixture==null?"--:--":fixtureTime(fixture.optString("date",""));
   String score=goals!=null&&!goals.isNull("home")?goals.optString("home")+" - "+goals.optString("away"):when;
   JSONObject lg=item.optJSONObject("league");
   String leagueLabel=lg==null?"":lg.optString("name","");
   if(!leagueLabel.isEmpty())score=leagueLabel+"\n"+score;
   ((TextView)line.getChildAt(1)).setText(arabicTeam(home.optString("name","")));
   ((TextView)line.getChildAt(2)).setText(score);
   ((TextView)line.getChildAt(3)).setText(arabicTeam(away.optString("name","")));
   loadLogo((android.widget.ImageView)line.getChildAt(0),home.optString("logo",""));
   loadLogo((android.widget.ImageView)line.getChildAt(4),away.optString("logo",""));
  }
 }
 private static void loadLogo(android.widget.ImageView target,String url){
  target.setImageDrawable(null);
  if(!url.startsWith("https://"))return;
  new Thread(()->{
   HttpURLConnection conn=null;
   try{
    conn=(HttpURLConnection)new URL(url).openConnection();
    conn.setConnectTimeout(5000);conn.setReadTimeout(5000);
    if(conn.getResponseCode()!=200)return;
    final android.graphics.Bitmap bitmap=android.graphics.BitmapFactory.decodeStream(conn.getInputStream());
    new Handler(Looper.getMainLooper()).post(()->target.setImageBitmap(bitmap));
   }catch(Exception ignored){}finally{if(conn!=null)conn.disconnect();}
  },"football-logo").start();
 }
 static void refresh(Context c,android.widget.LinearLayout view,boolean force){
  if(view==null)return;
  Context app=c.getApplicationContext();
  if(!enabled(app)){view.setVisibility(android.view.View.GONE);return;}
  view.setVisibility(android.view.View.VISIBLE);
  java.util.Set<String> selected=selectedLeagues(app);
  if(selected.isEmpty()){showMessage(view,"اختر الدوريات من الإعدادات");return;}
  String token="";
  SharedPreferences p=prefs(app);
  String key=android.text.TextUtils.join("|",new java.util.TreeSet<>(selected))+"|"+apiDay(0)+"|"+selectedTeamCacheKey(app,selected);
  String cached=p.getString("fixtures_json",null);
  long at=p.getLong(K_AT,0);
  if(cached!=null&&key.equals(p.getString("cache_key",""))){
   try{JSONArray arr=new JSONArray(cached);java.util.List<JSONObject> list=new java.util.ArrayList<>();
    for(int i=0;i<arr.length();i++)list.add(arr.getJSONObject(i));showFixtures(view,list);
   }catch(Exception ignored){}
  }else showMessage(view,"جارٍ جلب المباريات...");
  if(busy||(!force&&cached!=null&&key.equals(p.getString("cache_key",""))&&System.currentTimeMillis()-at<CACHE_MS))return;
  busy=true;
  new Thread(()->{
   java.util.List<JSONObject> matches=new java.util.ArrayList<>();
   String error=null;boolean success=false;
   // Public ESPN scoreboard fallback, without API key. It includes team logos.
   {
    for(String[] league:LEAGUES){
     if(!selected.contains(league[0]))continue;
     int beforeLeague=matches.size();
     for(int offset=0;offset<=7;offset++){
     HttpURLConnection conn=null;
     try{
      String endpoint="https://site.api.espn.com/apis/site/v2/sports/soccer/"+league[0]+"/scoreboard?dates="+espnDay(offset)+"&limit=100";
      conn=(HttpURLConnection)new URL(endpoint).openConnection();
      conn.setConnectTimeout(8000);conn.setReadTimeout(8000);
      conn.setRequestProperty("User-Agent",Weather.UA);
      if(conn.getResponseCode()!=200)continue;
      BufferedReader reader=new BufferedReader(new InputStreamReader(conn.getInputStream(),"UTF-8"));
      StringBuilder body=new StringBuilder();String line;
      while((line=reader.readLine())!=null)body.append(line);reader.close();
      JSONArray events=new JSONObject(body.toString()).optJSONArray("events");
      if(events==null)continue;
      success=true;
      for(int i=0;i<events.length();i++){
       JSONObject event=events.optJSONObject(i);if(event==null)continue;
       String date=event.optString("date","");
       if(date.isEmpty())continue;
       JSONArray competitions=event.optJSONArray("competitions");
       JSONObject competition=competitions==null?null:competitions.optJSONObject(0);
       JSONArray competitors=competition==null?null:competition.optJSONArray("competitors");
       if(competitors==null||competitors.length()<2)continue;
       JSONObject home=null,away=null;
       for(int j=0;j<competitors.length();j++){
        JSONObject t=competitors.optJSONObject(j);if(t==null)continue;
        if("home".equals(t.optString("homeAway")))home=t;else away=t;
       }
       if(home==null||away==null)continue;
       JSONObject status=event.optJSONObject("status");
       JSONObject type=status==null?null:status.optJSONObject("type");
       if(type!=null&&"post".equals(type.optString("state")))continue;
       java.util.Set<String> wanted=selectedTeams(app,league[0]);
       JSONObject ht=home.optJSONObject("team"),awayTeam=away.optJSONObject("team");
       if(!wanted.isEmpty()&&(ht==null||!wanted.contains(ht.optString("id")))&&(awayTeam==null||!wanted.contains(awayTeam.optString("id"))))continue;
       JSONObject item=new JSONObject(),fixture=new JSONObject(),teams=new JSONObject(),leagueObj=new JSONObject();
       fixture.put("date",date);fixture.put("leagueId",league[0]);
       teams.put("home",espnTeam(home));teams.put("away",espnTeam(away));
       leagueObj.put("name",league[1]);
       item.put("fixture",fixture);item.put("teams",teams);item.put("league",leagueObj);
       matches.add(item);
      }
     }catch(Exception ignored){}finally{if(conn!=null)conn.disconnect();}
     if(matches.size()-beforeLeague>=12)break;
     }
    }
   }
   java.util.Collections.sort(matches,(a,b)->{
    JSONObject af=a.optJSONObject("fixture"),bf=b.optJSONObject("fixture");
    return (af==null?"":af.optString("date","")).compareTo(bf==null?"":bf.optString("date",""));
   });
   // Prioritize today's fixtures from all selected leagues, then upcoming games.
   java.util.List<JSONObject> todays=new java.util.ArrayList<>(),upcoming=new java.util.ArrayList<>();
   String today=apiDay(0);
   java.util.Set<String> seen=new java.util.HashSet<>();
   for(JSONObject m:matches){
    JSONObject f=m.optJSONObject("fixture");
    String d=f==null?"":f.optString("date","");
    String leagueId=f==null?"":f.optString("leagueId","");
    if(!selected.contains(leagueId))continue;
    JSONObject ts=m.optJSONObject("teams");
    JSONObject h=ts==null?null:ts.optJSONObject("home"),a=ts==null?null:ts.optJSONObject("away");
    String unique=leagueId+"|"+d+"|"+(h==null?"":h.optString("name"))+"|"+(a==null?"":a.optString("name"));
    if(!seen.add(unique))continue;
    String localDay=riyadhDay(d);
    if(today.equals(localDay))todays.add(m);else upcoming.add(m);
   }
   // Fair rotation: when multiple leagues are selected, show matches from each.
   java.util.List<JSONObject> ordered=new java.util.ArrayList<>();
   java.util.Map<String,java.util.List<JSONObject>> byLeague=new java.util.LinkedHashMap<>();
   for(String[] league:LEAGUES)if(selected.contains(league[0]))byLeague.put(league[0],new java.util.ArrayList<>());
   for(JSONObject m:todays){JSONObject f=m.optJSONObject("fixture");String id=f==null?"":f.optString("leagueId","");if(byLeague.containsKey(id))byLeague.get(id).add(m);}
   boolean added=true;
   while(added){added=false;for(java.util.List<JSONObject> group:byLeague.values())if(!group.isEmpty()){ordered.add(group.remove(0));added=true;}}
   byLeague.clear();
   for(String[] league:LEAGUES)if(selected.contains(league[0]))byLeague.put(league[0],new java.util.ArrayList<>());
   for(JSONObject m:upcoming){JSONObject f=m.optJSONObject("fixture");String id=f==null?"":f.optString("leagueId","");if(byLeague.containsKey(id))byLeague.get(id).add(m);}
   added=true;
   while(added){added=false;for(java.util.List<JSONObject> group:byLeague.values())if(!group.isEmpty()){ordered.add(group.remove(0));added=true;}}
   final java.util.List<JSONObject> top=new java.util.ArrayList<>(ordered.subList(0,Math.min(5,ordered.size())));
   if(success&&!top.isEmpty()){
    JSONArray saved=new JSONArray();for(JSONObject m:top)saved.put(m);
    p.edit().putString("fixtures_json",saved.toString()).putString("cache_key",key).putLong(K_AT,System.currentTimeMillis()).apply();
   }
   final String message=success?"لا توجد مباريات قادمة للدوريات المختارة":(error==null?"تعذر جلب المباريات":error);
   new Handler(Looper.getMainLooper()).post(()->{
    busy=false;if(view==null)return;
    if(!top.isEmpty())showFixtures(view,top);
    else showMessage(view,message);
   });
  },"football-api-refresh").start();
 }
 private static String selectedTeamCacheKey(Context c,java.util.Set<String> leagues){
  StringBuilder b=new StringBuilder();
  for(String l:new java.util.TreeSet<>(leagues))b.append(l).append(":").append(new java.util.TreeSet<>(selectedTeams(c,l))).append(";");
  return b.toString();
 }
 private static JSONObject espnTeam(JSONObject competitor) throws Exception{
  JSONObject t=competitor.optJSONObject("team");
  JSONObject out=new JSONObject();
  out.put("name",t==null?"فريق":t.optString("displayName","فريق"));
  String logo=t==null?"":t.optString("logo","");
  if(logo.isEmpty()&&t!=null){
   JSONArray logos=t.optJSONArray("logos");
   if(logos!=null&&logos.length()>0){JSONObject first=logos.optJSONObject(0);if(first!=null)logo=first.optString("href","");}
  }
  out.put("logo",logo);return out;
 }
 private static String espnDay(int offset){
  java.text.SimpleDateFormat f=new java.text.SimpleDateFormat("yyyyMMdd",Locale.US);
  f.setTimeZone(TimeZone.getTimeZone("Asia/Riyadh"));
  return f.format(new Date(System.currentTimeMillis()+offset*86400000L));
 }
 private static String apiDay(int offset){
  java.text.SimpleDateFormat f=new java.text.SimpleDateFormat("yyyy-MM-dd",Locale.US);
  f.setTimeZone(TimeZone.getTimeZone("Asia/Riyadh"));
  return f.format(new Date(System.currentTimeMillis()+offset*86400000L));
 }
 private static Date parseMatchDate(String raw){
  if(raw==null||raw.trim().isEmpty())return null;
  try{return Date.from(java.time.Instant.parse(raw.trim()));}catch(Exception ignored){}
  try{return Date.from(java.time.OffsetDateTime.parse(raw.trim()).toInstant());}catch(Exception ignored){}
  try{return Date.from(java.time.LocalDateTime.parse(raw.trim()).toInstant(java.time.ZoneOffset.UTC));}catch(Exception ignored){}
  try{
   String v=raw.trim().replace(" ","T");
   if(v.endsWith("Z")&&v.length()==17)v=v.substring(0,v.length()-1)+":00Z";
   return Date.from(java.time.Instant.parse(v));
  }catch(Exception ignored){}
  return null;
 }
 private static String riyadhDay(String raw){
  Date d=parseMatchDate(raw);if(d==null)return "";
  SimpleDateFormat f=new SimpleDateFormat("yyyy-MM-dd",Locale.US);f.setTimeZone(TimeZone.getTimeZone("Asia/Riyadh"));return f.format(d);
 }
 private static String fixtureTime(String raw){
  Date d=parseMatchDate(raw);if(d==null)return "--:--";
  SimpleDateFormat f=new SimpleDateFormat("HH:mm",Locale.US);f.setTimeZone(TimeZone.getTimeZone("Asia/Riyadh"));return f.format(d);
 }
 private static int apiLeague(String id){
  switch(id){case "ksa.1":return 307;case "eng.1":return 39;case "esp.1":return 140;
   case "ita.1":return 135;case "fra.1":return 61;case "afc.champions":return 17;case "uefa.champions":return 2;default:return 0;}
 }
 private static int season(String id){
  java.util.Calendar c=java.util.Calendar.getInstance(TimeZone.getTimeZone("Asia/Riyadh"));
  int year=c.get(java.util.Calendar.YEAR),month=c.get(java.util.Calendar.MONTH);
  return month>=java.util.Calendar.JULY?year:year-1;
 }
 private static String name(JSONObject c){JSONObject t=c.optJSONObject("team");return t==null?"فريق":t.optString("displayName","فريق");}
 private static String date(String raw){try{SimpleDateFormat in=new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'",Locale.US);in.setTimeZone(TimeZone.getTimeZone("UTC"));Date d=in.parse(raw.substring(0,16)+"Z");SimpleDateFormat out=new SimpleDateFormat("EEE HH:mm",new Locale("ar"));out.setTimeZone(TimeZone.getTimeZone("Asia/Riyadh"));return out.format(d);}catch(Exception e){return "";}}
}