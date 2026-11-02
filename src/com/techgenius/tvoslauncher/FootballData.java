package com.techgenius.tvoslauncher;

import org.json.JSONArray;
import org.json.JSONObject;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.*;

/** ESPN normalization and selection without Android UI dependencies. */
final class FootballData {
 static final int LIMIT=7;
 private FootballData(){}
 static List<JSONObject> parse(JSONObject root,String league,String label) throws Exception {
  JSONArray events=root.optJSONArray("events");
  if(events==null)throw new IllegalArgumentException("Missing ESPN events array");
  List<JSONObject> out=new ArrayList<>();
  for(int i=0;i<events.length();i++){
   JSONObject event=events.optJSONObject(i);if(event==null)continue;
   JSONArray competitions=event.optJSONArray("competitions");
   JSONObject competition=competitions==null?null:competitions.optJSONObject(0);if(competition==null)continue;
   JSONArray sides=competition.optJSONArray("competitors");if(sides==null)continue;
   JSONObject home=null,away=null;
   for(int j=0;j<sides.length();j++){
    JSONObject side=sides.optJSONObject(j);if(side==null)continue;
    if("home".equals(side.optString("homeAway")))home=side;
    if("away".equals(side.optString("homeAway")))away=side;
   }
   if(home==null||away==null)continue;
   String date=event.optString("date",competition.optString("date",""));if(parseDate(date)==null)continue;
   JSONObject status=competition.optJSONObject("status");if(status==null)status=event.optJSONObject("status");
   JSONObject type=status==null?null:status.optJSONObject("type");
   String state=type==null?"pre":type.optString("state","pre");
   String code=type==null?"":type.optString("name","");
   boolean completed=type!=null&&type.optBoolean("completed",false);
   boolean hasScore="in".equals(state)||(completed&&!code.contains("CANCEL")&&!code.contains("POSTPON"));
   JSONObject fixture=new JSONObject().put("id",event.optString("id",competition.optString("id","")))
    .put("date",date).put("leagueId",league).put("state",state).put("statusCode",code)
    .put("completed",completed).put("hasScore",hasScore)
    .put("clock",status==null?"":status.optString("displayClock",""))
    .put("status",statusLabel(code,state,completed));
   if(competition.has("timeValid")&&!competition.optBoolean("timeValid",true))fixture.put("timeTbd",true);
   JSONObject goals=new JSONObject();
   if(hasScore){goals.put("home",score(home));goals.put("away",score(away));}
   JSONArray timeline=new JSONArray();JSONArray details=competition.optJSONArray("details");
   if(details!=null)for(int j=details.length()-1;j>=0;j--){
    JSONObject detail=details.optJSONObject(j);if(detail==null)continue;
    String text=eventText(detail);if(!text.isEmpty())timeline.put(text);
   }
   JSONObject venue=competition.optJSONObject("venue");
   out.add(new JSONObject().put("fixture",fixture).put("goals",goals)
    .put("teams",new JSONObject().put("home",team(home)).put("away",team(away)))
    .put("league",new JSONObject().put("id",league).put("name",label))
    .put("events",timeline).put("venue",venue==null?"":venue.optString("fullName","")));
  }
  return out;
 }
 private static Object score(JSONObject side){
  Object s=side.opt("score");if(s==null||s==JSONObject.NULL)return JSONObject.NULL;
  if(s instanceof JSONObject)return ((JSONObject)s).optString("displayValue",((JSONObject)s).optString("value",""));
  return String.valueOf(s);
 }
 private static JSONObject team(JSONObject side) throws Exception {
  JSONObject team=side.optJSONObject("team");if(team==null)team=new JSONObject();
  String logo=team.optString("logo","");JSONArray logos=team.optJSONArray("logos");
  if(logo.isEmpty()&&logos!=null&&logos.optJSONObject(0)!=null)logo=logos.optJSONObject(0).optString("href","");
  return new JSONObject().put("id",team.optString("id",side.optString("id","")))
   .put("name",team.optString("displayName",team.optString("name","فريق"))).put("logo",logo);
 }
 static String eventText(JSONObject d){
  JSONObject type=d.optJSONObject("type"),clock=d.optJSONObject("clock");
  String raw=type==null?"":type.optString("text","");String lower=raw.toLowerCase(Locale.US);
  String label;
  if(d.optBoolean("ownGoal",false))label="هدف عكسي";
  else if(d.optBoolean("redCard",false)||lower.contains("red card"))label="بطاقة حمراء";
  else if(d.optBoolean("yellowCard",false)||lower.contains("yellow card"))label="بطاقة صفراء";
  else if(d.optBoolean("scoringPlay",false)||lower.equals("goal"))label=d.optBoolean("penaltyKick",false)?"هدف من ركلة جزاء":"هدف";
  else if(lower.contains("substitution"))label="تبديل";
  else if(lower.contains("penalty"))label="ركلة جزاء";
  else label=raw;
  if(label.isEmpty())return "";
  JSONArray athletes=d.optJSONArray("athletesInvolved");JSONObject a=athletes==null?null:athletes.optJSONObject(0);
  String player=a==null?"":a.optString("shortName",a.optString("displayName",""));
  return (clock==null?"":clock.optString("displayValue","")+" ")+label+(player.isEmpty()?"":" • "+player);
 }
 static String statusLabel(String code,String state,boolean complete){
  if(code.contains("POSTPON"))return "مؤجلة";
  if(code.contains("CANCEL"))return "ملغاة";
  if(code.contains("SUSPEND"))return "متوقفة";
  if(code.contains("DELAY"))return "متأخرة";
  if(code.contains("HALFTIME"))return "استراحة";
  if(code.contains("SHOOTOUT"))return "ركلات ترجيح";
  if(code.contains("EXTRA"))return "وقت إضافي";
  if("in".equals(state))return "مباشر";
  if(complete)return "انتهت";
  return "لم تبدأ";
 }
 static Date parseDate(String raw){
  if(raw==null)return null;
  for(String pattern:new String[]{"yyyy-MM-dd'T'HH:mm:ss.SSSXXX","yyyy-MM-dd'T'HH:mm:ssXXX","yyyy-MM-dd'T'HH:mmXXX"}){
   SimpleDateFormat f=new SimpleDateFormat(pattern,Locale.US);f.setLenient(false);
   ParsePosition pos=new ParsePosition(0);Date result=f.parse(raw,pos);
   if(result!=null&&pos.getIndex()==raw.length())return result;
  }
  return null;
 }
 static String day(long at,TimeZone zone){SimpleDateFormat f=new SimpleDateFormat("yyyyMMdd",Locale.US);f.setTimeZone(zone);return f.format(new Date(at));}
 static List<JSONObject> select(List<JSONObject> input,List<String> leagues,long now,TimeZone zone){
  List<JSONObject> filtered=new ArrayList<>();Set<String> seen=new HashSet<>();String today=day(now,zone);
  for(JSONObject m:input){
   JSONObject f=m.optJSONObject("fixture");if(f==null)continue;
   String league=f.optString("leagueId");if(!leagues.contains(league))continue;
   Date date=parseDate(f.optString("date"));if(date==null)continue;
   long at=date.getTime();boolean live="in".equals(f.optString("state"));
   if(!live&&(at<now-86400000L||at>now+8L*86400000L))continue;
   String id=f.optString("id");String unique=league+"|"+(id.isEmpty()?m.optJSONObject("teams")+"|"+at:id);
   if(seen.add(unique))filtered.add(m);
  }
  filtered.sort((a,b)->{
   JSONObject af=a.optJSONObject("fixture"),bf=b.optJSONObject("fixture");
   int ar=rank(af,today,zone),br=rank(bf,today,zone);if(ar!=br)return Integer.compare(ar,br);
   long ad=parseDate(af.optString("date")).getTime(),bd=parseDate(bf.optString("date")).getTime();
   return ar==2||ar==4?Long.compare(bd,ad):Long.compare(ad,bd);
  });
  List<JSONObject> result=new ArrayList<>();
  for(int rank=0;rank<=4&&result.size()<LIMIT;rank++){
   Map<String,ArrayDeque<JSONObject>> groups=new LinkedHashMap<>();for(String l:leagues)groups.put(l,new ArrayDeque<>());
   for(JSONObject m:filtered){JSONObject f=m.optJSONObject("fixture");if(rank(f,today,zone)==rank)groups.get(f.optString("leagueId")).add(m);}
   boolean added=true;
   while(added&&result.size()<LIMIT){added=false;for(ArrayDeque<JSONObject> group:groups.values()){
    if(!group.isEmpty()&&result.size()<LIMIT){result.add(group.removeFirst());added=true;}
   }}
  }
  return result;
 }
 private static int rank(JSONObject f,String today,TimeZone zone){
  if("in".equals(f.optString("state")))return 0;
  boolean same=today.equals(day(parseDate(f.optString("date")).getTime(),zone));
  boolean done="post".equals(f.optString("state"));
  return same?(done?2:1):(done?4:3);
 }
}
