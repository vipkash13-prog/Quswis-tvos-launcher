package com.techgenius.tvoslauncher;
import org.json.*;
import java.nio.file.*;
import java.util.*;

public class FootballDataTest {
 static int checks;
 static void check(boolean value){checks++;if(!value)throw new AssertionError("case "+checks);}
 static final TimeZone RIYADH=TimeZone.getTimeZone("Asia/Riyadh");
 static JSONObject fixture(String id,String league,String date,String state)throws Exception{
  return new JSONObject().put("fixture",new JSONObject().put("id",id).put("leagueId",league).put("date",date).put("state",state));
 }
 static JSONObject copy(JSONObject value)throws Exception{return new JSONObject(value.toString());}
 public static void main(String[] args)throws Exception{
  JSONObject source=new JSONObject(Files.readString(Path.of("tests/fixtures/espn-completed.json")));
  List<JSONObject> data=FootballData.parse(source,"eng.1","الدوري الإنجليزي");
  check(data.size()==1);
  JSONObject match=data.get(0),f=match.getJSONObject("fixture"),g=match.getJSONObject("goals");
  check(f.getString("state").equals("post")&&f.getBoolean("hasScore"));
  JSONArray sides=source.getJSONArray("events").getJSONObject(0).getJSONArray("competitions").getJSONObject(0).getJSONArray("competitors");
  JSONObject home=sides.getJSONObject(0).getString("homeAway").equals("home")?sides.getJSONObject(0):sides.getJSONObject(1);
  check(g.getString("home").equals(home.getString("score")));
  check(match.getJSONArray("events").length()>0);
  check(match.getJSONArray("events").toString().contains("بطاقة صفراء"));
  check(match.getJSONArray("events").toString().contains("هدف"));
  JSONObject reversed=copy(source);JSONObject competition=reversed.getJSONArray("events").getJSONObject(0).getJSONArray("competitions").getJSONObject(0);
  competition.put("competitors",new JSONArray().put(copy(sides.getJSONObject(1))).put(copy(sides.getJSONObject(0))));
  check(FootballData.parse(reversed,"eng.1","").get(0).getJSONObject("teams").getJSONObject("home").getString("id").equals(match.getJSONObject("teams").getJSONObject("home").getString("id")));
  JSONObject type=competition.getJSONObject("status").getJSONObject("type");
  type.put("state","pre").put("completed",false).put("name","STATUS_SCHEDULED");
  check(!FootballData.parse(reversed,"eng.1","").get(0).getJSONObject("fixture").getBoolean("hasScore"));
  type.put("state","in").put("name","STATUS_IN_PROGRESS");
  competition.getJSONArray("competitors").getJSONObject(0).put("score","0");competition.getJSONArray("competitors").getJSONObject(1).put("score","0");
  JSONObject zero=FootballData.parse(reversed,"eng.1","").get(0);check(zero.getJSONObject("goals").getString("home").equals("0"));
  competition.getJSONArray("competitors").getJSONObject(0).remove("score");
  JSONObject missing=FootballData.parse(reversed,"eng.1","").get(0);check(missing.getJSONObject("goals").isNull("away"));
  type.put("state","post").put("completed",false).put("name","STATUS_CANCELED");
  check(!FootballData.parse(reversed,"eng.1","").get(0).getJSONObject("fixture").getBoolean("hasScore"));
  check(FootballData.statusLabel("STATUS_POSTPONED","pre",false).equals("مؤجلة"));
  check(FootballData.statusLabel("STATUS_HALFTIME","in",false).equals("استراحة"));
  check(FootballData.parseDate("2026-11-02T20:00Z").equals(FootballData.parseDate("2026-11-02T23:00:00+03:00")));
  check(FootballData.parseDate("not-a-date")==null);
  check(FootballData.day(FootballData.parseDate("2026-11-02T22:00Z").getTime(),RIYADH).equals("20261103"));
  long now=FootballData.parseDate("2026-11-02T10:00Z").getTime();List<JSONObject> games=new ArrayList<>();
  games.add(fixture("live","ksa.1","2026-11-02T09:00Z","in"));
  for(int i=0;i<12;i++)games.add(fixture("eng"+i,"eng.1","2026-11-02T20:00Z","pre"));
  games.add(fixture("ksa","ksa.1","2026-11-02T20:30Z","pre"));
  games.add(copy(games.get(0)));games.add(fixture("expired","ksa.1","2026-10-01T10:00Z","post"));
  games.add(fixture("far","ksa.1","2026-12-01T10:00Z","pre"));
  List<JSONObject> top=FootballData.select(games,Arrays.asList("ksa.1","eng.1"),now,RIYADH);
  check(top.size()==7);check(top.get(0).getJSONObject("fixture").getString("id").equals("live"));
  Set<String> ids=new HashSet<>();for(JSONObject m:top)ids.add(m.getJSONObject("fixture").getString("id"));
  check(ids.size()==7&&ids.contains("ksa")&&!ids.contains("far")&&!ids.contains("expired"));
  check(FootballData.select(games,Collections.singletonList("ksa.1"),now,RIYADH).size()==2);
  check(FootballData.select(Collections.emptyList(),Arrays.asList("ksa.1"),now,RIYADH).isEmpty());
  boolean rejected=false;try{FootballData.parse(new JSONObject(),"eng.1","");}catch(IllegalArgumentException e){rejected=true;}check(rejected);
  check(FootballData.parse(new JSONObject().put("events",new JSONArray()),"ksa.1","").isEmpty());
  System.out.println("Football parsing and selection: "+checks+" checks passed");
 }
}
