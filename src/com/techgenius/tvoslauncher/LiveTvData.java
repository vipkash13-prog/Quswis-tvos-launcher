package com.techgenius.tvoslauncher;

import org.json.*;
import java.io.*;import java.net.*;import java.text.*;import java.util.*;import java.util.regex.*;

/** Catalogue facts, never promises that a URL is playable or that a programme is on air. */
final class LiveTvData {
 static final String[] SECTIONS={"🇸🇦 القنوات السعودية","🌙 القنوات العربية","🌍 قنوات العالم","📰 قنوات الأخبار","⚽ القنوات الرياضية","🎌 قنوات الأنمي","🧸 قنوات الكرتون المدبلج بالعربية","📺 قنوات اشتراكي IPTV"};
 static final String[] LISTS={"countries/sa","regions/arab","index","categories/news","categories/sports","categories/animation","categories/kids","categories/movies","categories/series","countries/ae"};
 static final long DAY=86400000L;
 static final class Meta {String id="",name="",arabic="",country="",website="",categories="",network="";boolean blocked;}
 static final class Endpoint {
  String url="",feed="",referrer="",agent="",languages="";boolean geo;
  JSONObject json()throws Exception{return new JSONObject().put("url",url).put("feed",feed).put("ref",referrer).put("ua",agent).put("lang",languages).put("geo",geo);}
  static Endpoint from(JSONObject o){Endpoint e=new Endpoint();e.url=o.optString("url");e.feed=o.optString("feed");e.referrer=o.optString("ref");e.agent=o.optString("ua");e.languages=o.optString("lang");e.geo=o.optBoolean("geo");return e;}
 }
 static final class Channel {
  String id="",name="",sourceName="",logo="",groups="",country="",website="";List<Endpoint> streams=new ArrayList<>();
  String key(){return id.isEmpty()?normalize(sourceName):id;}
  boolean arabicDeclared(){for(Endpoint e:streams)if(hasArabic(e.languages))return true;return false;}
  boolean animation(){String g=groups.toLowerCase(Locale.ROOT);return g.contains("animation")||g.contains("kids");}
  JSONObject json()throws Exception{JSONArray a=new JSONArray();for(Endpoint e:streams)a.put(e.json());return new JSONObject().put("id",id).put("name",name).put("source",sourceName).put("logo",logo).put("groups",groups).put("country",country).put("website",website).put("streams",a);}
  static Channel from(JSONObject o){Channel c=new Channel();c.id=o.optString("id");c.name=o.optString("name");c.sourceName=o.optString("source");c.logo=o.optString("logo");c.groups=o.optString("groups");c.country=o.optString("country");c.website=o.optString("website");JSONArray a=o.optJSONArray("streams");if(a!=null)for(int i=0;i<a.length();i++)if(a.optJSONObject(i)!=null)c.streams.add(Endpoint.from(a.optJSONObject(i)));return c;}
 }
 static final class Playlist {final List<Channel> channels=new ArrayList<>();String epg="";}
 static String web(String raw){if(raw==null)return "";try{URI u=new URI(raw.trim());if((!"https".equalsIgnoreCase(u.getScheme())&&!"http".equalsIgnoreCase(u.getScheme()))||u.getHost()==null||u.getUserInfo()!=null)return "";return u.toASCIIString();}catch(Exception e){return "";}}
 static boolean allowed(String name,String raw){String n=normalize(name);if(n.matches(".*(?:bein|بيانسبورت|بينسبورت|osn|اواس ان|sscpremium|sscsports|ssc sports|premium|اشتراك|مشفر|skysport|bt sport|tntsports|dazn|adult|xxx|18\\+).*"))return false;
  String url=web(raw);if(url.isEmpty())return false;try{URI u=new URI(url);String h=u.getHost().toLowerCase(Locale.ROOT);if(h.matches("[0-9.]+")||h.contains(":")||h.equals("localhost")||h.endsWith(".local")||h.equals("freem3u.xyz"))return false;String q=u.getRawQuery();return q==null||!q.toLowerCase(Locale.ROOT).matches(".*(?:username|password|drm|license_key)=.*");}catch(Exception e){return false;}}
 static boolean hasArabic(String languages){return Arrays.asList(languages.toLowerCase(Locale.ROOT).split("[,;\\s]+")).stream().anyMatch(x->x.equals("ar")||x.equals("ara")||x.startsWith("ar-"));}
 static String normalize(String s){return s==null?"":s.toLowerCase(Locale.ROOT).replaceAll("[\\u064B-\\u065F\\u0670ـ]","").replaceAll("[أإآ]","ا").replace('ى','ي').replaceAll("\\s+"," ").trim();}
 static Map<String,String> attributes(String line){Map<String,String> a=new HashMap<>();Matcher m=Pattern.compile("([\\w-]+)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s,]+))").matcher(line);while(m.find())a.put(m.group(1).toLowerCase(Locale.US),m.group(2)!=null?m.group(2):m.group(3)!=null?m.group(3):m.group(4));return a;}
 static Playlist parse(String text)throws Exception{
  if(text==null||text.length()>12*1024*1024||!text.replace("\uFEFF","").trim().startsWith("#EXTM3U"))throw new IOException("Not an M3U catalogue");
  Playlist out=new Playlist();Map<String,Channel> unique=new LinkedHashMap<>();Map<String,String> attrs=null;String title="",ref="",agent="";boolean drm=false;Set<String> urls=new HashSet<>();
  for(String raw:text.replace("\uFEFF","").split("\\r?\\n")){String line=raw.trim();if(line.startsWith("#EXTM3U")){Map<String,String>a=attributes(line);out.epg=web(a.getOrDefault("x-tvg-url",a.getOrDefault("url-tvg",a.getOrDefault("tvg-url",""))).split(",",2)[0]);}
   else if(line.startsWith("#EXTINF:")){attrs=attributes(line);boolean quote=false;int comma=-1;for(int i=0;i<line.length();i++){if(line.charAt(i)=='"')quote=!quote;else if(line.charAt(i)==','&&!quote){comma=i;break;}}title=comma<0?attrs.getOrDefault("tvg-name",""):line.substring(comma+1).trim();ref=attrs.getOrDefault("http-referrer","");agent=attrs.getOrDefault("http-user-agent","");drm=false;}
   else if(line.startsWith("#EXTVLCOPT:http-referrer="))ref=line.substring(line.indexOf('=')+1).trim();
   else if(line.startsWith("#EXTVLCOPT:http-user-agent="))agent=line.substring(line.indexOf('=')+1).trim();
   else if(line.startsWith("#KODIPROP:")||line.startsWith("#EXT-X-KEY:"))drm=true;
   else if(!line.isEmpty()&&!line.startsWith("#")&&attrs!=null){String url=web(line);String tvg=attrs.getOrDefault("tvg-id","");String id=tvg.split("@",2)[0];String key=id.isEmpty()?normalize(title):id;
    if(!drm&&!title.isEmpty()&&allowed(title,url)&&urls.add(url)){Channel c=unique.get(key);if(c==null){c=new Channel();c.id=id;c.name=title.replaceAll("\\s*\\[(?:Geo-blocked|Not 24/7)\\]","").trim();c.sourceName=c.name;c.logo=web(attrs.getOrDefault("tvg-logo",""));c.groups=attrs.getOrDefault("group-title","");unique.put(key,c);}Endpoint e=new Endpoint();e.url=url;e.feed=tvg;e.referrer=web(ref);e.agent=agent.replaceAll("[\\r\\n]","");e.languages=attrs.getOrDefault("tvg-language","");e.geo=title.toLowerCase(Locale.ROOT).contains("geo-blocked");c.streams.add(e);}
    attrs=null;
   }
  }
  out.channels.addAll(unique.values());return out;
 }
 static List<Channel> enrich(List<Channel> list,Map<String,Meta> meta,Map<String,String> languages,int section){List<Channel> out=new ArrayList<>();for(Channel c:list){Meta m=meta.get(c.id);if(m!=null){if(m.blocked)continue;c.name=m.arabic.isEmpty()?m.name:m.arabic;c.country=m.country;c.website=m.website;c.groups+=";"+m.categories;if(!allowed(m.name+" "+m.network,c.streams.get(0).url))continue;}
   for(Endpoint e:c.streams){String lang=languages.get(e.feed);if(lang==null)lang=languages.get(c.id);if(lang!=null)e.languages=lang;}
   if(section==5&&!c.arabicDeclared()&&!c.country.equals("JP")&&!normalize(c.sourceName).matches(".*(?:anime|naruto|one piece|onepiece|conan|hunter|dragon ball|gundam|pokemon).*"))continue;out.add(c);}
  if(section==5)out.sort((a,b)->Boolean.compare(b.arabicDeclared(),a.arabicDeclared()));return out;}
 static List<Channel> search(List<Channel> all,String query){String q=normalize(query);List<Channel> out=new ArrayList<>();for(Channel c:all)if(q.isEmpty()||(normalize(c.name+" "+c.sourceName+" "+c.id).contains(q)||normalize(c.name+" "+c.sourceName+" "+c.id).replace(" ","").contains(q.replace(" ",""))))out.add(c);return out;}
 static String time(long value){SimpleDateFormat f=new SimpleDateFormat("d/MM/yyyy h:mm a",new Locale("ar"));f.setTimeZone(TimeZone.getTimeZone("Asia/Riyadh"));return f.format(new Date(value));}
}
