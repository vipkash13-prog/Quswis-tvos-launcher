package com.techgenius.tvoslauncher;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.util.*;

/** Xtream live channels for section 7. Credentials stay in private app storage. */
final class LiveTvXtream {
 static final String HOST="http://cli2345.live";
 static final String USER="xtream_user", PASS="xtream_pass";
 static boolean configured(SharedPreferences p){return !p.getString(USER,"").isEmpty()&&!p.getString(PASS,"").isEmpty();}
 static String encode(String v)throws Exception{return URLEncoder.encode(v,"UTF-8");}
 static List<LiveTvData.Channel> load(SharedPreferences prefs)throws Exception{
  String user=prefs.getString(USER,""),pass=prefs.getString(PASS,"");
  if(user.isEmpty()||pass.isEmpty())throw new IllegalArgumentException("Credentials required");
  URL url=new URL(HOST+"/player_api.php?username="+encode(user)+"&password="+encode(pass)+"&action=get_live_streams");
  HttpURLConnection conn=(HttpURLConnection)url.openConnection();conn.setConnectTimeout(12000);conn.setReadTimeout(20000);
  try{
   if(conn.getResponseCode()!=200)throw new IOException("HTTP "+conn.getResponseCode());
   ByteArrayOutputStream bytes=new ByteArrayOutputStream();
   try(InputStream in=conn.getInputStream()){byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1){bytes.write(buf,0,n);if(bytes.size()>16000000)throw new IOException("Response too large");}}
   JSONArray arr=new JSONArray(bytes.toString("UTF-8"));List<LiveTvData.Channel> result=new ArrayList<>();
   for(int i=0;i<arr.length();i++){
    JSONObject o=arr.optJSONObject(i);if(o==null)continue;
    String id=o.optString("stream_id","");if(!id.matches("[0-9]+"))continue;
    LiveTvData.Channel c=new LiveTvData.Channel();
    c.id="xtream-"+id;c.name=o.optString("name","قناة "+id);c.sourceName=c.name;
    c.logo=o.optString("stream_icon","");c.groups=o.optString("category_id","");
    LiveTvData.Endpoint e=new LiveTvData.Endpoint();
    e.url=HOST+"/live/"+encode(user)+"/"+encode(pass)+"/"+id+".m3u8";
    c.streams.add(e);result.add(c);
   }
   return result;
  }finally{conn.disconnect();}
 }
}
