package com.techgenius.tvoslauncher;
import java.time.Instant;
public class PrayerPanelClockTest {
 static int checks;
 static void check(boolean b){checks++;if(!b)throw new AssertionError("check "+checks);}
 static long at(String s){return Instant.parse(s).toEpochMilli();}
 public static void main(String[] args){
  String[] t={"05:00","12:00","15:00","18:00","19:30"};
  PrayerPanelClock.Result r=PrayerPanelClock.next(t,"Asia/Riyadh",at("2026-11-02T08:00:00Z"));
  check(r.index==1&&!r.tomorrow);check(r.at==at("2026-11-02T09:00:00Z"));
  check(PrayerPanelClock.countdown(r.at-at("2026-11-02T08:00:00Z")).equals("01:00:00"));
  r=PrayerPanelClock.next(t,"Asia/Riyadh",at("2026-11-02T16:31:00Z"));
  check(r.index==0&&r.tomorrow);check(r.at==at("2026-11-03T02:00:00Z"));
  r=PrayerPanelClock.next(t,"Asia/Riyadh",at("2026-11-02T09:00:00Z"));check(r.index==1);
  r=PrayerPanelClock.next(t,"Asia/Riyadh",at("2026-11-02T09:00:01Z"));check(r.index==2);
  check(PrayerPanelClock.clean("18:00 (AST)").equals("18:00"));
  check(PrayerPanelClock.clean("25:00").equals("--:--"));
  check(PrayerPanelClock.clean(null).equals("--:--"));
  check(PrayerPanelClock.next(new String[]{"--:--","","","",""},"UTC",0)==null);
  check(PrayerPanelClock.countdown(-5000).equals("00:00:00"));
  check(PrayerPanelClock.countdown(1).equals("00:00:01"));
  r=PrayerPanelClock.next(t,"America/New_York",at("2026-10-31T23:31:00Z"));
  check(r.tomorrow&&r.at==at("2026-11-01T10:00:00Z"));
  System.out.println("Prayer detail countdown: "+checks+" checks passed");
 }
}
