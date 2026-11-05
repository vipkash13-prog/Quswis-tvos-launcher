package com.techgenius.tvoslauncher;

import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.*;

/** Match-center policy only. The existing ESPN parser and Home data remain unchanged. */
final class MatchCenterData {
    static final int UPCOMING=0, LIVE=1, RESULTS=2, LEAGUE_TAB=3;
    static final TimeZone RIYADH=TimeZone.getTimeZone("Asia/Riyadh");
    static final String NATIONAL="saudi-national";
    static final String[][] LEAGUES={
        {"ksa.1","دوري روشن السعودي"},{"afc.champions","دوري أبطال آسيا للنخبة"},
        {"uefa.champions","دوري أبطال أوروبا"},{"eng.1","الدوري الإنجليزي الممتاز"},
        {"esp.1","الدوري الإسباني"},{"ita.1","الدوري الإيطالي"},
        {"fra.1","الدوري الفرنسي"},{NATIONAL,"مباريات المنتخب السعودي"}};
    static String label(String id){for(String[] l:LEAGUES)if(l[0].equals(id))return l[1];return id;}
    static String month(long at){return day(at).substring(0,6);}
    static String day(long at){return FootballData.day(at,RIYADH);}
    static String format(long at,String pattern){SimpleDateFormat f=new SimpleDateFormat(pattern,new Locale("ar"));f.setTimeZone(RIYADH);return f.format(new Date(at));}
    static String clock(long at){SimpleDateFormat f=new SimpleDateFormat("h:mm",Locale.US);f.setTimeZone(RIYADH);Calendar c=Calendar.getInstance(RIYADH);c.setTimeInMillis(at);return f.format(new Date(at))+(c.get(Calendar.HOUR_OF_DAY)<12?" ص":" م");}
    static List<JSONObject> parse(JSONObject root,String group)throws Exception{
        List<JSONObject> rows=FootballData.parse(root,group,label(group));
        String logo="";JSONArray leagues=root.optJSONArray("leagues");
        JSONObject league=leagues==null?null:leagues.optJSONObject(0);
        JSONArray logos=league==null?null:league.optJSONArray("logos");
        if(logos!=null&&logos.optJSONObject(0)!=null)logo=logos.optJSONObject(0).optString("href");
        if(NATIONAL.equals(group)){
            JSONObject team=root.optJSONObject("team");if(team==null||!"655".equals(team.optString("id")))throw new IllegalArgumentException("Saudi national team response does not match");
            logo=team.optString("logo");
        }
        for(JSONObject m:rows){m.getJSONObject("league").put("logo",logo);}
        if(NATIONAL.equals(group))rows.removeIf(m->{JSONObject teams=m.optJSONObject("teams");return teams==null||(!"655".equals(teams.optJSONObject("home").optString("id"))&&!"655".equals(teams.optJSONObject("away").optString("id")));});
        return rows;
    }
    static List<JSONObject> select(List<JSONObject> input,Set<String> chosen,int tab,long now,int limit){
        List<JSONObject> result=new ArrayList<>();Set<String> seen=new HashSet<>();String today=day(now);
        for(JSONObject m:input){
            JSONObject f=m.optJSONObject("fixture");if(f==null||!chosen.contains(f.optString("leagueId")))continue;
            Date when=FootballData.parseDate(f.optString("date"));if(when==null)continue;
            String state=f.optString("state"),code=f.optString("statusCode");
            boolean include=tab==LIVE?"in".equals(state)&&when.getTime()<=now+3600000L&&when.getTime()>now-36L*3600000L&&!code.contains("POSTPON")&&!code.contains("CANCEL"):tab==RESULTS?
                    "post".equals(state)&&f.optBoolean("completed")&&!code.contains("CANCEL")&&!code.contains("POSTPON")&&today.equals(day(when.getTime())):
                    "pre".equals(state)&&when.getTime()>=now&&!code.contains("CANCEL")&&!code.contains("POSTPON");
            if(!include)continue;
            String id=f.optString("id");String key=id.isEmpty()?f.optString("leagueId")+"|"+when.getTime()+"|"+m.optJSONObject("teams"):id;
            if(seen.add(key))result.add(m);
        }
        // Preserve all of today's upcoming matches; when none remain, show the nearest future schedule.
        if(tab==UPCOMING){boolean hasToday=false;for(JSONObject m:result)if(today.equals(day(FootballData.parseDate(m.optJSONObject("fixture").optString("date")).getTime())))hasToday=true;
            if(hasToday)result.removeIf(m->!today.equals(day(FootballData.parseDate(m.optJSONObject("fixture").optString("date")).getTime())));}
        result.sort((a,b)->{long x=FootballData.parseDate(a.optJSONObject("fixture").optString("date")).getTime(),y=FootballData.parseDate(b.optJSONObject("fixture").optString("date")).getTime();return tab==RESULTS?Long.compare(y,x):Long.compare(x,y);});
        if(result.size()>limit)return new ArrayList<>(result.subList(0,limit));return result;
    }
    static String teamName(String raw){
        String name=FootballScores.arabicTeam(raw);if(!name.equals(raw))return name;
        String[][] countries={{"Gamba Osaka","غامبا أوساكا"},{"Pohang Steelers","بوهانغ ستيلرز"},{"Kashima Antlers","كاشيما أنتلرز"},{"Buriram United","بوريرام يونايتد"},{"Fenerbahce","فنربخشة"},{"Fenerbahçe","فنربخشة"},{"Ulsan Hyundai","أولسان هيونداي"},{"Ulsan HD","أولسان"},{"Jeonbuk Hyundai Motors","جيونبوك هيونداي"},{"Kawasaki Frontale","كاواساكي فرونتال"},{"Vissel Kobe","فيسيل كوبي"},{"Yokohama F. Marinos","يوكوهاما مارينوس"},{"Johor Darul Ta'zim","جوهور دار التعظيم"},{"Persepolis","برسبوليس"},{"Esteghlal","استقلال"},{"Al Sadd","السد"},{"Al Duhail","الدحيل"},{"Al Ain","العين"},{"Al Wahda","الوحدة"},{"Saudi Arabia","السعودية"},{"United Arab Emirates","الإمارات"},{"Qatar","قطر"},{"Bahrain","البحرين"},{"Kuwait","الكويت"},{"Oman","عُمان"},{"Iraq","العراق"},{"Jordan","الأردن"},{"Japan","اليابان"},{"Australia","أستراليا"},{"Indonesia","إندونيسيا"},{"China","الصين"},{"South Korea","كوريا الجنوبية"},{"Egypt","مصر"},{"Morocco","المغرب"},{"Tunisia","تونس"},{"AEK Athens","أيك أثينا"},{"Benfica","بنفيكا"},{"Sporting CP","سبورتينغ لشبونة"},{"FC Porto","بورتو"}};
        for(String[] p:countries)if(p[0].equalsIgnoreCase(raw))return p[1];return name;
    }
}
