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
 static final String[][] LEAGUES={{"ksa.1","دوري روشن السعودي"},{"eng.1","الدوري الإنجليزي"},{"esp.1","الدوري الإسباني"},{"ita.1","الدوري الإيطالي"},{"fra.1","الدوري الفرنسي"},{"ger.1","الدوري الألماني"},{"afc.champions","دوري أبطال آسيا للنخبة"},{"uefa.champions","دوري أبطال أوروبا"}};
 static final class Team{final String id,name;Team(String i,String n){id=i;name=n;}}
 interface TeamsCallback{void onLoaded(List<Team> teams,String error);}
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
  // Arabic names for clubs frequently returned by ESPN and 365Scores.
  String[][] more={{"Sevilla","إشبيلية"},{"Getafe","خيتافي"},{"Como","كومو"},{"Venezia","فينيزيا"},{"Strasbourg","ستراسبورغ"},{"AJ Auxerre","أوكسير"},{"Auxerre","أوكسير"},{"1. FC Union Berlin","يونيون برلين"},{"Union Berlin","يونيون برلين"},{"FC Cologne","كولن"},{"FC Köln","كولن"},{"Liverpool FC","ليفربول"},{"Arsenal FC","أرسنال"},{"Sevilla FC","إشبيلية"},{"Getafe CF","خيتافي"},{"Como 1907","كومو"},{"Venezia FC","فينيزيا"},{"RC Strasbourg","ستراسبورغ"},{"Racing Strasbourg","ستراسبورغ"},{"FC Koln","كولن"},{"SSC Napoli","نابولي"},{"Napoli","نابولي"},{"AS Roma","روما"},{"Roma","روما"},{"Lazio","لاتسيو"},{"Fiorentina","فيورنتينا"},{"Atalanta","أتالانتا"},{"Torino","تورينو"},{"Udinese","أودينيزي"},{"Genoa","جنوى"},{"Cagliari","كالياري"},{"Parma","بارما"},{"Pisa","بيزا"},{"Lecce","ليتشي"},{"Sassuolo","ساسولو"},{"Verona","فيرونا"},{"Hellas Verona","فيرونا"},{"Real Betis","ريال بيتيس"},{"Villarreal","فياريال"},{"Valencia","فالنسيا"},{"Real Sociedad","ريال سوسيداد"},{"Athletic Club","أتلتيك بلباو"},{"Osasuna","أوساسونا"},{"Celta Vigo","سيلتا فيغو"},{"Rayo Vallecano","رايو فاييكانو"},{"Espanyol","إسبانيول"},{"Girona","جيرونا"},{"Mallorca","مايوركا"},{"Levante","ليفانتي"},{"Elche","إلتشي"},{"Alaves","ألافيس"},{"Real Oviedo","ريال أوفييدو"},{"Brest","بريست"},{"Lille","ليل"},{"Lyon","ليون"},{"Marseille","مارسيليا"},{"Monaco","موناكو"},{"Nice","نيس"},{"Lens","لانس"},{"Rennes","رين"},{"Nantes","نانت"},{"Toulouse","تولوز"},{"Metz","ميتز"},{"Lorient","لوريان"},{"Le Havre","لوهافر"},{"Angers","أنجيه"},{"Paris FC","باريس إف سي"},{"Hamburger SV","هامبورغ"},{"Werder Bremen","فيردر بريمن"},{"Eintracht Frankfurt","آينتراخت فرانكفورت"},{"RB Leipzig","لايبزيغ"},{"VfL Wolfsburg","فولفسبورغ"},{"Mainz","ماينز"},{"Hoffenheim","هوفنهايم"},{"Heidenheim","هايدنهايم"},{"St. Pauli","سانت باولي"},{"Hertha Berlin","هيرتا برلين"},{"Bayer 04 Leverkusen","باير ليفركوزن"}};
  for(String[] x:more)if(n.equals(x[0].toLowerCase(Locale.US).replaceAll("[^a-z0-9]","")))return x[1];
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
 static final long REFRESH_MS=30000L;
 private static final Handler MAIN=new Handler(Looper.getMainLooper());
 private static final java.util.concurrent.ExecutorService LOGOS=java.util.concurrent.Executors.newFixedThreadPool(2);
 private static final android.util.LruCache<String,android.graphics.Bitmap> LOGO_CACHE=new android.util.LruCache<String,android.graphics.Bitmap>(2*1024*1024){
  @Override protected int sizeOf(String key,android.graphics.Bitmap image){return image.getByteCount();}
 };
 private static final class MatchRow {
  android.widget.LinearLayout root;
  android.widget.ImageView homeLogo,awayLogo;
  TextView home,away,score,meta,event;
  JSONObject match;
 }
 private static final class MatchPanel extends android.widget.LinearLayout {
  final MatchRow[] rows=new MatchRow[FootballData.LIMIT];
  final TextView title,footer;
  final Handler ticker=new Handler(Looper.getMainLooper());
  volatile boolean loading;
  final Runnable rotate=new Runnable(){public void run(){
   for(MatchRow row:rows)if(row.match!=null){
    JSONArray events=row.match.optJSONArray("events");
    if(events!=null&&events.length()>0){int index=(int)((android.os.SystemClock.elapsedRealtime()/5000L)%events.length());row.event.setText(events.optString(index));}
   }
   ticker.postDelayed(this,5000L);
  }};
  MatchPanel(Context c){
   super(c);setOrientation(VERTICAL);setPadding(Ui.px(c,14),Ui.px(c,10),Ui.px(c,14),Ui.px(c,12));
   GradientDrawable glass=new GradientDrawable();glass.setColor(0x554C7B91);glass.setCornerRadius(Ui.px(c,22));glass.setStroke(Ui.px(c,1),0xAADDFAFF);setBackground(glass);
   title=text(c,18,true);title.setText("المباريات القادمة");addView(title,new LayoutParams(-1,Ui.px(c,34)));
   for(int i=0;i<rows.length;i++){
    MatchRow row=new MatchRow();rows[i]=row;
    row.root=new android.widget.LinearLayout(c);row.root.setOrientation(VERTICAL);row.root.setGravity(Gravity.CENTER_VERTICAL);
    row.root.setPadding(Ui.px(c,8),Ui.px(c,2),Ui.px(c,8),Ui.px(c,2));
    GradientDrawable bg=new GradientDrawable();bg.setColor(0x66557E94);bg.setCornerRadius(Ui.px(c,12));bg.setStroke(Ui.px(c,1),0x77FFFFFF);row.root.setBackground(bg);
    android.widget.LinearLayout main=new android.widget.LinearLayout(c);main.setOrientation(HORIZONTAL);main.setGravity(Gravity.CENTER_VERTICAL);
    main.setLayoutDirection(android.view.View.LAYOUT_DIRECTION_RTL);
    row.homeLogo=new android.widget.ImageView(c);row.awayLogo=new android.widget.ImageView(c);
    row.homeLogo.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);row.awayLogo.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
    row.home=text(c,11,false);row.away=text(c,11,false);row.score=text(c,13,true);row.score.setTextDirection(android.view.View.TEXT_DIRECTION_LTR);
    main.addView(row.homeLogo,new LayoutParams(Ui.px(c,20),Ui.px(c,20)));
    main.addView(row.home,new LayoutParams(0,-1,1));main.addView(row.score,new LayoutParams(Ui.px(c,62),-1));
    main.addView(row.away,new LayoutParams(0,-1,1));main.addView(row.awayLogo,new LayoutParams(Ui.px(c,20),Ui.px(c,20)));
    row.root.addView(main,new LayoutParams(-1,Ui.px(c,22)));
    row.meta=text(c,9,false);row.meta.setTextColor(0xFFFFE8A3);row.root.addView(row.meta,new LayoutParams(-1,Ui.px(c,11)));
    row.event=text(c,9,false);row.event.setTextColor(0xFFDDEAF2);row.root.addView(row.event,new LayoutParams(-1,Ui.px(c,11)));
    LayoutParams lp=new LayoutParams(-1,0,1);lp.bottomMargin=Ui.px(c,5);addView(row.root,lp);
   }
   footer=text(c,9,false);footer.setTextColor(0xFFCFD8E3);addView(footer,new LayoutParams(-1,Ui.px(c,16)));
   setFocusable(true);setOnLongClickListener(v->{refresh(c,this,true);return true;});
   showMessage(this,"جارٍ جلب المباريات...");
  }
  @Override protected void onAttachedToWindow(){super.onAttachedToWindow();ticker.removeCallbacks(rotate);ticker.post(rotate);}
  @Override protected void onDetachedFromWindow(){ticker.removeCallbacksAndMessages(null);super.onDetachedFromWindow();}
 }
 private static TextView text(Context c,float size,boolean bold){
  TextView t=new TextView(c);Ui.textSize(t,size);t.setTextColor(Color.WHITE);t.setSingleLine(true);t.setIncludeFontPadding(false);
  t.setEllipsize(android.text.TextUtils.TruncateAt.END);t.setGravity(Gravity.CENTER);if(bold)t.setTypeface(Typeface.DEFAULT_BOLD);return t;
 }
 static android.widget.LinearLayout createView(Context c){return new MatchPanel(c);}
 private static void showMessage(MatchPanel panel,String msg){
  panel.title.setText("المباريات القادمة");
  for(int i=0;i<panel.rows.length;i++){
   MatchRow r=panel.rows[i];r.match=null;r.root.setVisibility(i==0?android.view.View.VISIBLE:android.view.View.INVISIBLE);
   r.home.setText("");r.away.setText("");r.score.setText("—");r.meta.setText(i==0?msg:"");r.event.setText("");loadLogo(r.homeLogo,"");loadLogo(r.awayLogo,"");
  }
 }
 private static void showFixtures(MatchPanel panel,List<JSONObject> matches){
  panel.title.setText("المباريات القادمة • "+matches.size());
  for(int i=0;i<panel.rows.length;i++){
   MatchRow row=panel.rows[i];row.match=i<matches.size()?matches.get(i):null;
   row.root.setVisibility(row.match==null?android.view.View.INVISIBLE:android.view.View.VISIBLE);if(row.match==null)continue;
   JSONObject m=row.match,f=m.optJSONObject("fixture"),teams=m.optJSONObject("teams"),goals=m.optJSONObject("goals"),league=m.optJSONObject("league");
   JSONObject home=teams.optJSONObject("home"),away=teams.optJSONObject("away");
   row.home.setText(arabicTeam(home.optString("name")));row.away.setText(arabicTeam(away.optString("name")));
   Date date=FootballData.parseDate(f.optString("date"));
   boolean hasScore=f.optBoolean("hasScore",false)&&goals!=null&&!goals.isNull("home")&&!goals.isNull("away");
   // The home team is physically on the right in this RTL row.
   row.score.setText(hasScore?goals.optString("away")+" - "+goals.optString("home"):
     (f.optBoolean("hasScore",false)?"—":(f.optBoolean("timeTbd",false)?"يحدد لاحقاً":format(date,"HH:mm"))));
   boolean stale=f.optBoolean("stale",false),live="in".equals(f.optString("state"));
   row.score.setTextColor(live&&!stale?0xFFFFE8A3:Color.WHITE);
   String state=f.optString("status","لم تبدأ")+(live?" "+f.optString("clock",""):"");
   String when=format(date,"dd/MM HH:mm");
   row.meta.setText((stale?"محفوظ • ":"")+league.optString("name")+" • "+state+" • "+when);
   JSONArray events=m.optJSONArray("events");
   row.event.setText(events!=null&&events.length()>0?events.optString(0):("pre".equals(f.optString("state"))?"الموعد حسب توقيت الجهاز":"تفاصيل الأحداث غير متاحة من المصدر"));
   row.root.setContentDescription(row.home.getText()+" "+(hasScore?goals.optString("home"):"")+"، "+row.away.getText()+" "+(hasScore?goals.optString("away"):"")+"، "+row.meta.getText());
   loadLogo(row.homeLogo,home.optString("logo"));loadLogo(row.awayLogo,away.optString("logo"));
  }
 }
 private static String format(Date date,String pattern){if(date==null)return "--:--";SimpleDateFormat f=new SimpleDateFormat(pattern,Locale.US);return f.format(date);}
 private static void loadLogo(android.widget.ImageView target,String url){
  if(url.equals(target.getTag()))return;target.setTag(url);target.setImageDrawable(null);if(!url.startsWith("https://"))return;
  android.graphics.Bitmap cached=LOGO_CACHE.get(url);if(cached!=null){target.setImageBitmap(cached);return;}
  LOGOS.execute(()->{
   HttpURLConnection conn=null;
   try{
    conn=(HttpURLConnection)new URL(url).openConnection();conn.setConnectTimeout(4000);conn.setReadTimeout(4000);
    if(conn.getResponseCode()!=200)return;
    android.graphics.BitmapFactory.Options options=new android.graphics.BitmapFactory.Options();options.inSampleSize=4;
    android.graphics.Bitmap image=android.graphics.BitmapFactory.decodeStream(conn.getInputStream(),null,options);
    if(image!=null){LOGO_CACHE.put(url,image);MAIN.post(()->{if(url.equals(target.getTag()))target.setImageBitmap(image);});}
   }catch(Exception ignored){}finally{if(conn!=null)conn.disconnect();}
  });
 }
 private static final class LeagueResult {
  final String id;final List<JSONObject> matches=new ArrayList<>();boolean received,complete=true;
  LeagueResult(String id){this.id=id;}
 }
 private static JSONObject getJson(String endpoint) throws Exception {
  HttpURLConnection conn=(HttpURLConnection)new URL(endpoint).openConnection();
  try{
   conn.setConnectTimeout(4500);conn.setReadTimeout(5000);conn.setRequestProperty("User-Agent",Weather.UA);conn.setRequestProperty("Accept","application/json");
   if(conn.getResponseCode()!=200)throw new java.io.IOException("HTTP "+conn.getResponseCode());
   try(BufferedReader r=new BufferedReader(new InputStreamReader(conn.getInputStream(),"UTF-8"))){
    StringBuilder body=new StringBuilder();String line;
    while((line=r.readLine())!=null){body.append(line);if(body.length()>4_000_000)throw new java.io.IOException("Response too large");}
    return new JSONObject(body.toString());
   }
  }finally{conn.disconnect();}
 }
 private static LeagueResult fetchLeague(String id,String name,long now,TimeZone zone,long deadline){
  LeagueResult result=new LeagueResult(id);int eligible=0;
  int[] days={0,-1,1,2,3,4,5,6,7};
  for(int offset:days){
   if(Thread.currentThread().isInterrupted()||System.currentTimeMillis()>=deadline){result.complete=false;break;}
   java.util.Calendar day=java.util.Calendar.getInstance(zone);day.setTimeInMillis(now);day.add(java.util.Calendar.DAY_OF_MONTH,offset);
   try{
    JSONObject response=getJson("https://site.api.espn.com/apis/site/v2/sports/soccer/"+id+"/scoreboard?dates="+FootballData.day(day.getTimeInMillis(),zone)+"&limit=100");
    List<JSONObject> matches=FootballData.parse(response,id,name);result.received=true;result.matches.addAll(matches);
    for(JSONObject m:matches)if(!"post".equals(m.getJSONObject("fixture").optString("state"))&&offset>=0)eligible++;
   }catch(Exception e){result.complete=false;}
   if(offset>=0&&offset!=0&&eligible>=FootballData.LIMIT)break;
  }
  return result;
 }
 private static String selectionKey(Context c){return "espn-seven-v1|"+android.text.TextUtils.join("|",new java.util.TreeSet<>(selectedLeagues(c)))+"|"+TimeZone.getDefault().getID()+"|"+FootballData.day(System.currentTimeMillis(),TimeZone.getDefault());}
 private static List<JSONObject> cachedMatches(SharedPreferences p,List<String> leagues,boolean forceStale){
  List<JSONObject> all=new ArrayList<>();
  for(String id:leagues){
   try{
    JSONArray data=new JSONArray(p.getString("espn_league_"+id,"[]"));
    boolean stale=forceStale||p.getBoolean("espn_failed_"+id,false)||System.currentTimeMillis()-p.getLong("espn_at_"+id,0)>REFRESH_MS*2;
    for(int i=0;i<data.length();i++){JSONObject m=data.getJSONObject(i);m.getJSONObject("fixture").put("stale",stale);all.add(m);}
   }catch(Exception ignored){}
  }
  return all;
 }
 static void refresh(Context c,android.widget.LinearLayout view,boolean force){
  if(!(view instanceof MatchPanel))return;MatchPanel panel=(MatchPanel)view;Context app=c.getApplicationContext();
  if(!enabled(app)){panel.setVisibility(android.view.View.GONE);return;}panel.setVisibility(android.view.View.VISIBLE);
  List<String> selected=new ArrayList<>();for(String[] league:LEAGUES)if(isLeagueSelected(app,league[0]))selected.add(league[0]);
  if(selected.isEmpty()){showMessage(panel,"اختر الدوريات من الإعدادات");panel.footer.setText("");return;}
  SharedPreferences p=prefs(app);String key=selectionKey(app);long now=System.currentTimeMillis();TimeZone zone=TimeZone.getDefault();
  List<JSONObject> cache=FootballData.select(cachedMatches(p,selected,false),selected,now,zone);
  if(!cache.isEmpty()){showFixtures(panel,cache);panel.footer.setText("ESPN • بيانات محفوظة • جارٍ التحقق من التحديث");}
  else showMessage(panel,"جارٍ تحديث المباريات...");
  if(panel.loading)return;
  if(!force&&key.equals(p.getString("espn_refresh_key",""))&&now-p.getLong(K_AT,0)<REFRESH_MS){
   panel.footer.setText(p.getString("espn_footer","ESPN"));return;
  }
  panel.loading=true;
  new Thread(()->{
   java.util.concurrent.ExecutorService pool=java.util.concurrent.Executors.newFixedThreadPool(4);
   List<java.util.concurrent.Callable<LeagueResult>> jobs=new ArrayList<>();long deadline=System.currentTimeMillis()+40000L;
   for(String[] league:LEAGUES)if(selected.contains(league[0]))jobs.add(()->fetchLeague(league[0],league[1],now,zone,deadline));
   List<JSONObject> all=new ArrayList<>();List<String> failed=new ArrayList<>();int ok=0;
   try{
    List<java.util.concurrent.Future<LeagueResult>> results=pool.invokeAll(jobs,45,java.util.concurrent.TimeUnit.SECONDS);
    for(int i=0;i<results.size();i++){
     LeagueResult result=null;try{result=results.get(i).get();}catch(Exception ignored){}
     String id=selected.get(i);
     if(result!=null&&result.received){
      p.edit().putBoolean("espn_failed_"+id,!result.complete).apply();
      // Merge partial responses with marked cached entries instead of erasing a league.
      if(!result.complete){failed.add(id);all.addAll(cachedMatches(p,Collections.singletonList(id),true));}
      all.addAll(0,result.matches);ok++;
      if(result.complete){JSONArray data=new JSONArray();for(JSONObject m:result.matches)data.put(m);p.edit().putString("espn_league_"+id,data.toString()).putLong("espn_at_"+id,System.currentTimeMillis()).apply();}
     }else{p.edit().putBoolean("espn_failed_"+id,true).apply();failed.add(id);all.addAll(cachedMatches(p,Collections.singletonList(id),true));}
    }
   }catch(InterruptedException e){Thread.currentThread().interrupt();failed.addAll(selected);all.addAll(cachedMatches(p,selected,true));}
   finally{pool.shutdownNow();}
   List<JSONObject> top=FootballData.select(all,selected,System.currentTimeMillis(),zone);
   String status=failed.isEmpty()?"تحديث "+format(new Date(),"HH:mm:ss"):
    (ok==0?"تعذر التحديث"+(top.isEmpty()?"":" • بيانات محفوظة"):"تحديث جزئي "+ok+"/"+selected.size()+" • قد تظهر بيانات محفوظة");
   final String footer="ESPN • "+status+" • "+zone.getID();
   boolean changed=!key.equals(selectionKey(app));
   if(!changed)p.edit().putString("espn_refresh_key",key).putLong(K_AT,System.currentTimeMillis()).putString("espn_footer",footer).apply();
   final boolean any=ok>0;
   MAIN.post(()->{
    panel.loading=false;if(!panel.isAttachedToWindow())return;
    if(!key.equals(selectionKey(app))){refresh(app,panel,true);return;}
    if(!top.isEmpty())showFixtures(panel,top);else showMessage(panel,any?"لا توجد مباريات ضمن الأيام الثمانية القادمة":"تعذر التحديث • تحقق من الإنترنت");
    panel.footer.setText(footer);panel.footer.setContentDescription(footer+(failed.isEmpty()?"":" • تعذر تحديث "+android.text.TextUtils.join("، ",failed)));
   });
  },"football-espn-refresh").start();
 }
}
