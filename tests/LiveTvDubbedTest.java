package com.techgenius.tvoslauncher;
import java.nio.file.*;import java.util.*;
public final class LiveTvDubbedTest {
 static int count;static void check(boolean ok,String why){count++;if(!ok)throw new AssertionError(why);}
 public static void main(String[] args)throws Exception{
  check(LiveTvData.SECTIONS.length==8,"Eight sections");check(LiveTvData.SECTIONS[6].equals("🧸 قنوات الكرتون المدبلج بالعربية"),"Previous order preserved");check(LiveTvData.SECTIONS[7].equals("🎬 الأفلام والمسلسلات الآسيوية المدبلجة"),"Exact eighth section name");
  check(Arrays.equals(Arrays.copyOf(LiveTvData.LISTS,7),new String[]{"countries/sa","regions/arab","index","categories/news","categories/sports","categories/animation","categories/kids"}),"Original source indices unchanged");
  LiveTvData.Channel old=LiveTvDubbed.spacetoon(),fresh=LiveTvData.Channel.from(old.json());fresh.streams.get(0).url="https://new-source.invalid/failing.m3u8";LiveTvData.Channel merged=LiveTvDubbed.merge(old,fresh);check(merged.streams.get(0).url.equals(LiveTvDubbed.SPACETOON_URL),"Never replace working Spacetoon URL");check(merged.streams.size()==2,"New source remains only an alternative");check(LiveTvDubbed.merge(merged,old).streams.size()==2,"No duplicate stream");check(old.streams.size()==1&&old.streams.get(0).url.equals(LiveTvDubbed.SPACETOON_URL),"Do not mutate existing channel");
  check(LiveTvDubbed.merge(old,null).key().equals(old.key()),"Retain channel when provider removes it");check(LiveTvDubbed.asian(channel("MBCBollywood.ae")),"Documented Asian channel");check(!LiveTvDubbed.asian(channel("MBCDrama.kr")),"Korean original language is not Arabic dubbing");check(!LiveTvDubbed.asian(channel("MBCDrama.ae")),"Do not infer Asian content from Arabic drama");
  String raw=new String(Files.readAllBytes(Paths.get(args[0])),java.nio.charset.StandardCharsets.UTF_8);List<LiveTvDubbed.Audit> audits=LiveTvDubbed.parseAudits(raw);check(audits.size()==3,"Only actually decoded audited additions");Set<String> ids=new HashSet<>();for(LiveTvDubbed.Audit a:audits){check(ids.add(a.channel.key()),"No duplicate audited channel");check(a.checked>0&&!a.documentation.isEmpty(),"Evidence includes date and source");check(!a.channel.id.equals("CartoonNetworkArabic.ae"),"Do not add unauthorized IP restream");}
  check(LiveTvDubbed.parseAudits(raw.replace("decoded_stream_language:ar","name_only").replace("official_arabic_channel","name_only")).isEmpty(),"Channel name alone never proves audio");check(LiveTvDubbed.parseAudits(raw.replace("\"video_decoded\": true","\"video_decoded\": false")).isEmpty(),"HTTP 200 alone never proves playback");
  check(LiveTvDubbed.parseAudits(raw.replace("decoded_stream_language:ar","decoded_stream_language:arg")).size()==1,"Aragonese is not Arabic; only documented SAT-7 remains");
  System.out.println("LIVE_TV_DUBBED_DATA_OK: "+count+" checks");
 }
 static LiveTvData.Channel channel(String id){LiveTvData.Channel c=new LiveTvData.Channel();c.id=id;return c;}
}
