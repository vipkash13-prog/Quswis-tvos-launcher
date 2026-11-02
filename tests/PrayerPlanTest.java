package com.techgenius.tvoslauncher;
import org.json.*;
import java.util.*;
import java.nio.file.*;
import java.time.Instant;

/** Offline regression of dated delivery, using a real AlAdhan monthly calendar. */
public class PrayerPlanTest {
 static int count;
 static void check(boolean yes){count++;if(!yes)throw new AssertionError("check "+count);}
 static long utc(String s){return Instant.parse(s).toEpochMilli();}
 static JSONObject day(String fajr)throws Exception{return new JSONObject().put("timezone","Asia/Riyadh").put("timings",new JSONObject().put("Fajr",fajr).put("Dhuhr","12:00").put("Asr","15:00").put("Maghrib","18:00").put("Isha","19:30"));}
 public static void main(String[] args)throws Exception{
  JSONObject real=new JSONObject();PrayerPlan.merge(new JSONObject(new String(Files.readAllBytes(Paths.get("tests/fixtures/prayer-jeddah-2026-11.json")),"UTF-8")),real);
  check(real.length()==30);check(real.getJSONObject("2026-11-03").getString("timezone").equals("Asia/Riyadh"));
  int[] delays={15,15,15,15,15};long night=utc("2026-11-02T20:00:00Z");
  List<PrayerPlan.Event> plan=PrayerPlan.events(real,night,true,false,delays);
  check(plan.size()==35);PrayerPlan.Event first=plan.get(0);check(first.index==0&&first.day.equals("2026-11-03"));
  check(first.at==PrayerPlan.instant("2026-11-03",real.getJSONObject("2026-11-03").getJSONObject("timings").getString("Fajr"),"Asia/Riyadh"));
  check(first.at%60000==0);check(first.key.equals("2026-11-03|Fajr|adhan"));
  TimeZone saved=TimeZone.getDefault();TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Honolulu"));
  try{check(PrayerPlan.events(real,night,true,false,delays).get(0).at==first.at);}finally{TimeZone.setDefault(saved);}
  check(PrayerPlan.events(real,night,false,false,delays).isEmpty());
  JSONObject edge=new JSONObject().put("2026-11-30",day("05:00")).put("2026-12-01",day("05:02")).put("2026-12-02",day("05:04"));
  plan=PrayerPlan.events(edge,utc("2026-11-30T20:00:00Z"),true,false,delays);
  check(plan.get(0).at==utc("2026-12-01T02:02:00Z"));check(plan.get(0).day.equals("2026-12-01"));
  check(plan.get(5).at==utc("2026-12-02T02:04:00Z")); // not a guessed daily repeating Fajr
  JSONObject today=new JSONObject().put("2026-11-02",day("05:00"));
  plan=PrayerPlan.events(today,utc("2026-11-02T02:05:00Z"),false,true,delays);
  check(plan.get(0).iqama&&plan.get(0).at==utc("2026-11-02T02:15:00Z"));
  plan=PrayerPlan.events(today,utc("2026-11-02T02:00:00Z"),true,true,delays);
  check(!plan.get(0).iqama&&plan.get(1).iqama&&plan.get(0).request!=plan.get(1).request);
  check(PrayerPlan.deliver("Jeddah","Jeddah",first.at,first.at,false));
  check(PrayerPlan.deliver("Jeddah","Jeddah",first.at+120000,first.at,false));
  check(!PrayerPlan.deliver("Jeddah","Jeddah",first.at+120001,first.at,false));
  check(!PrayerPlan.deliver("Jeddah","Jeddah",first.at,first.at,true));
  check(!PrayerPlan.deliver("Riyadh","Jeddah",first.at,first.at,false));
  check(!PrayerPlan.deliver("Jeddah","Jeddah",first.at-2000,first.at,false));
  check(PrayerPlan.instant("2026-02-30","05:00","Asia/Riyadh")==-1);
  check(PrayerPlan.instant("2026-11-02","25:00","Asia/Riyadh")==-1);
  check(PrayerPlan.instant("2026-11-02","05:00","Invalid/Zone")==-1);
  check(PrayerPlan.instant("2026-11-02","05:00 (AST)","Asia/Riyadh")==utc("2026-11-02T02:00:00Z"));
  check(PrayerPlan.day(utc("2026-11-01T22:00:00Z"),"Asia/Riyadh").equals("2026-11-02"));
  check(PrayerPlan.events(real,night,true,false,delays).stream().allMatch(e->e.at>=night));
  System.out.println("Prayer calendar/delivery: "+count+" checks passed");
 }
}
