package com.techgenius.tvoslauncher;
import org.json.*;import java.util.*;
public final class FeatureDataTest {
 static int count;static void yes(boolean value){count++;if(!value)throw new AssertionError("Feature policy check "+count);}
 public static void main(String[] args)throws Exception{
  yes(AppUpdateData.isNewer("1.0.206","v2.0.6"));yes(!AppUpdateData.isNewer("2.0.6","v2.0.5"));yes(!AppUpdateData.isNewer("2.0.6","v2.0.6"));yes(AppUpdateData.isNewer("2.0.6","2.0.7"));yes(!AppUpdateData.isNewer("2.0.6","audio-performance-206-99"));
  String url="https://github.com/vipkash13-prog/Quswis-tvos-launcher/releases/download/v2.0.7/Quswis-tvOS-Launcher.apk";yes(AppUpdateData.apkUrl(url));yes(!AppUpdateData.apkUrl(url.replace("https:","http:")));yes(!AppUpdateData.apkUrl(url.replace("vipkash13-prog","other-owner")));yes(!AppUpdateData.downloadHost("https://github.com.evil.test/a.apk"));
  JSONObject release=new JSONObject().put("tag_name","v2.0.7").put("assets",new JSONArray().put(new JSONObject().put("name","Quswis-tvOS-Launcher.apk").put("browser_download_url",url)));yes(AppUpdateData.release(release).version.equals("2.0.7"));
  release.put("prerelease",true);boolean rejected=false;try{AppUpdateData.release(release);}catch(Exception expected){rejected=true;}yes(rejected);
  String api=XtreamData.api("http://provider.test","u /&","p+?&","get_live_streams");yes(api.contains("username=u%20%2F%26"));yes(api.contains("password=p%2B%3F%26"));
  for(String forbidden:new String[]{"get_vod_streams","get_series","get_vod_categories"}){rejected=false;try{XtreamData.api("http://provider.test","u","p",forbidden);}catch(IllegalArgumentException e){rejected=true;}yes(rejected);}
  JSONObject ports=new JSONObject().put("server_info",new JSONObject().put("url","provider.test").put("port",8080));yes(XtreamData.streamBase("http://provider.test",ports).equals("http://provider.test:8080"));yes(XtreamData.streamBase("http://provider.test:80",ports).equals("http://provider.test:80"));ports.getJSONObject("server_info").put("url","other.test");yes(XtreamData.streamBase("http://provider.test",ports).equals("http://provider.test"));
  JSONObject auth=new JSONObject().put("user_info",new JSONObject().put("auth",1).put("status","Active").put("allowed_output_formats",new JSONArray().put("ts")));yes(XtreamData.formats(auth).equals(Collections.singletonList("ts")));
  JSONArray streams=new JSONArray().put(new JSONObject().put("stream_id",1).put("name","قناة اختبار التصفية").put("stream_type","live").put("category_id","7").put("stream_icon","https://logo.test/icon.png"));streams.put(streams.getJSONObject(0));streams.put(new JSONObject().put("stream_id",2).put("name","Not live").put("stream_type","movie"));
  String account=XtreamData.account("http://provider.test","u /&");JSONObject catalog=XtreamData.catalogue(account,new JSONArray(),streams,Collections.singletonList("ts"));List<LiveTvData.Channel> channels=XtreamData.channels(catalog);yes(channels.size()==1);yes(channels.get(0).streams.get(0).url.endsWith("/1.ts"));yes(!channels.get(0).json().toString().contains("p+?&"));yes(channels.get(0).logo.startsWith("xtream-logo://"));yes(XtreamData.subscription(channels.get(0)));yes(LiveTvData.search(channels,"قناة").size()==1);
  System.out.println("Live subscription/update policies: "+count+" checks passed");
 }
}
