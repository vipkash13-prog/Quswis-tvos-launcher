package com.techgenius.tvoslauncher;

import org.json.*;
import java.nio.file.*;
import java.util.*;

/** Test-only state mutations exercise boundaries; no fixture assets are bundled in the APK. */
public final class MatchCenterDataTest {
    private static int checks;
    private static void check(boolean yes){checks++;if(!yes)throw new AssertionError("Match center check "+checks);}
    private static JSONObject copy(JSONObject o)throws Exception{return new JSONObject(o.toString());}
    private static long at(String d){return FootballData.parseDate(d).getTime();}
    public static void main(String[] args)throws Exception{
        JSONObject response=new JSONObject(Files.readString(Path.of("tests/fixtures/espn-center-upcoming.json")));
        List<JSONObject> matches=MatchCenterData.parse(response,"uefa.champions");Set<String> selected=new HashSet<>(Arrays.asList("uefa.champions","eng.1"));
        check(matches.size()>=5);
        check(!matches.get(0).getJSONObject("league").getString("logo").isEmpty());
        long now=at("2026-11-04T00:00Z");
        check(MatchCenterData.select(matches,selected,0,now,5).size()==5);
        check(MatchCenterData.select(matches,selected,0,now,Integer.MAX_VALUE).size()==matches.size());
        JSONObject tomorrow=copy(matches.get(0));tomorrow.getJSONObject("fixture").put("id","tomorrow-only").put("date","2026-11-05T17:45Z");
        check(MatchCenterData.select(Collections.singletonList(tomorrow),selected,0,now,Integer.MAX_VALUE).size()==1);
        List<JSONObject> mixedDays=new ArrayList<>(matches);mixedDays.add(tomorrow);check(MatchCenterData.select(mixedDays,selected,0,now,Integer.MAX_VALUE).size()==matches.size());
        check(MatchCenterData.select(Collections.singletonList(tomorrow),selected,MatchCenterData.LEAGUE_TAB,now,5).size()==1);
        for(int section:new int[]{MatchCenterData.UPCOMING,MatchCenterData.LIVE,MatchCenterData.RESULTS}){
            List<JSONObject> many=new ArrayList<>();
            for(int i=0;i<601;i++){
                JSONObject m=copy(matches.get(0));m.getJSONObject("fixture").put("id","pagination-"+i)
                    .put("state",section==MatchCenterData.LIVE?"in":section==MatchCenterData.RESULTS?"post":"pre")
                    .put("completed",section==MatchCenterData.RESULTS)
                    .put("statusCode",section==MatchCenterData.LIVE?"STATUS_IN_PROGRESS":section==MatchCenterData.RESULTS?"STATUS_FULL_TIME":"STATUS_SCHEDULED");many.add(m);
            }
            check(MatchCenterData.select(many,selected,section,section==MatchCenterData.UPCOMING?now:at("2026-11-04T18:00Z"),Integer.MAX_VALUE).size()==601);
        }
        check(MatchCenterData.select(matches,Collections.emptySet(),0,now,5).isEmpty());
        check(MatchCenterData.select(matches,selected,1,now,5).isEmpty());
        check(MatchCenterData.select(matches,selected,2,now,5).isEmpty());
        for(JSONObject m:matches)check(!m.getJSONObject("fixture").getBoolean("hasScore"));
        JSONObject late=copy(matches.get(0));late.getJSONObject("fixture").put("id","boundary").put("date","2026-11-03T22:00Z").put("state","post").put("completed",true).put("hasScore",true);
        late.put("goals",new JSONObject().put("home","0").put("away","0"));
        check(MatchCenterData.day(at("2026-11-03T22:00Z")).equals("20261104"));
        check(MatchCenterData.clock(at("2026-11-03T22:00Z")).equals("1:00 ص"));
        check(MatchCenterData.clock(at("2026-11-04T09:00Z")).equals("12:00 م"));
        check(MatchCenterData.select(Collections.singletonList(late),selected,2,now,5).size()==1);
        check(MatchCenterData.select(Collections.singletonList(late),selected,0,now,5).isEmpty());
        late.getJSONObject("fixture").put("statusCode","STATUS_CANCELED");
        check(MatchCenterData.select(Collections.singletonList(late),selected,2,now,5).isEmpty());
        late.getJSONObject("fixture").put("statusCode","STATUS_FULL_TIME");
        late.getJSONObject("fixture").put("state","in").put("completed",false);
        check(MatchCenterData.select(Collections.singletonList(late),selected,1,now,5).size()==1);
        check(late.getJSONObject("goals").getString("home").equals("0"));
        List<JSONObject> duplicates=new ArrayList<>();duplicates.add(late);duplicates.add(copy(late));
        check(MatchCenterData.select(duplicates,selected,1,now,500).size()==1);
        JSONObject invalid=copy(late);invalid.getJSONObject("fixture").put("date","not-a-date");
        check(MatchCenterData.select(Collections.singletonList(invalid),selected,1,now,5).isEmpty());
        JSONObject national=new JSONObject(Files.readString(Path.of("tests/fixtures/espn-saudi-schedule.json")));
        List<JSONObject> saudi=MatchCenterData.parse(national,MatchCenterData.NATIONAL);check(!saudi.isEmpty());
        for(JSONObject m:saudi){JSONObject teams=m.getJSONObject("teams");check(teams.getJSONObject("home").getString("id").equals("655")||teams.getJSONObject("away").getString("id").equals("655"));}
        national.getJSONObject("team").put("id","not-saudi");boolean rejected=false;try{MatchCenterData.parse(national,MatchCenterData.NATIONAL);}catch(IllegalArgumentException expected){rejected=true;}check(rejected);
        check(MatchCenterData.select(Collections.emptyList(),selected,0,now,5).isEmpty());
        System.out.println("Match center policy: "+checks+" checks passed");
    }
}
