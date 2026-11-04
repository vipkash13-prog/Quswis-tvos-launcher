package com.techgenius.tvoslauncher;

import org.json.*;import java.util.*;

/** Exact channel/feed evidence, not guesses from a channel name or its country. */
final class LiveTvDubbed {
 static final String SPACETOON_ID="SpacetoonArabic.ae";
 static final String SPACETOON_URL="https://live-uae-next.spacetoongo.com/ST_MENA_NEXT/hls/r9p2hjipmw2kl.m3u8";
 static final Set<String> ASIAN=new HashSet<>(Arrays.asList("ZeeAlwan.ae","ZeeAflam.ae","MBCBollywood.ae","B4UAflam.ae"));
 static boolean asian(LiveTvData.Channel c){return ASIAN.contains(c.id);}
 static void localize(LiveTvData.Channel c){if(c.id.equals("MBCBollywood.ae"))c.name="MBC بوليوود";else if(c.id.equals("MBC3USA.us"))c.name="إم بي سي 3 USA";}
 static boolean eligible(LiveTvData.Channel c){return c.animation()||asian(c);}
 static LiveTvData.Channel spacetoon(){LiveTvData.Channel c=new LiveTvData.Channel();c.id=SPACETOON_ID;c.name="سبيستون";c.sourceName="Spacetoon Arabic";c.country="AE";c.groups="Animation;Kids";c.website="https://spacetoon.com";c.logo="https://upload.wikimedia.org/wikipedia/en/2/2b/Spacetoon_logo.png";LiveTvData.Endpoint e=new LiveTvData.Endpoint();e.url=SPACETOON_URL;e.feed=SPACETOON_ID+"@SD";e.referrer="https://spacetoon.com";e.languages="ara";c.streams.add(e);return c;}
 /** Keep old working URLs in their original order. Add new alternatives without replacing any URL. */
 static LiveTvData.Channel merge(LiveTvData.Channel old,LiveTvData.Channel fresh){if(old==null)return fresh;if(fresh==null)return old;if(!old.key().equals(fresh.key()))throw new IllegalArgumentException("Different channel identities");try{LiveTvData.Channel result=LiveTvData.Channel.from(fresh.json());result.streams.clear();Set<String> seen=new HashSet<>();for(LiveTvData.Channel c:new LiveTvData.Channel[]{old,fresh})for(LiveTvData.Endpoint e:c.streams)if(seen.add(e.url))result.streams.add(LiveTvData.Endpoint.from(e.json()));return result;}catch(Exception e){return old;}}
 static final class Audit {LiveTvData.Channel channel;String kind,evidence,documentation;long checked;}
 static List<Audit> parseAudits(String text)throws Exception{List<Audit> out=new ArrayList<>();JSONArray entries=new JSONObject(text).getJSONArray("entries");for(int i=0;i<entries.length();i++){JSONObject o=entries.getJSONObject(i);if(!o.optBoolean("video_decoded"))continue;LiveTvData.Channel c=LiveTvData.Channel.from(o.getJSONObject("channel"));String proof=o.optString("audio_evidence"),doc=o.optString("documentation");boolean audio=proof.startsWith("decoded_stream_language:ar")||proof.equals("official_arabic_channel")&&c.id.equals("Sat7Kids.cy")&&doc.equals("https://sat7.org/our-channels/channel-overview/sat-7-kids/");String kind=o.optString("kind");if(!audio||c.streams.isEmpty()||!LiveTvData.allowed(c.name,c.streams.get(0).url)||!(kind.equals("cartoon")&&c.animation()||kind.equals("asian")&&asian(c)))continue;Audit a=new Audit();a.channel=c;a.kind=kind;a.evidence=proof;a.documentation=doc;a.checked=o.optLong("checked_at");if(a.checked>0)out.add(a);}return out;}
}
