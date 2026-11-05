package com.techgenius.tvoslauncher;

import java.net.*;import java.nio.charset.StandardCharsets;import java.security.MessageDigest;import java.util.*;import org.json.*;

/** Live-only Xtream protocol. Subscription streams never enter the public-playlist filter. */
final class XtreamData {
    static final String SERVER="http://cli2345.live";
    static String segment(String value){return URLEncoder.encode(value,StandardCharsets.UTF_8).replace("+","%20");}
    static String account(String server,String username)throws Exception{
        byte[] bytes=MessageDigest.getInstance("SHA-256").digest((server+"\n"+username).getBytes(StandardCharsets.UTF_8));StringBuilder out=new StringBuilder();for(int i=0;i<12;i++)out.append(String.format(Locale.US,"%02x",bytes[i]&255));return out.toString();
    }
    static String api(String server,String user,String pass,String action){
        if(!action.isEmpty()&&!action.equals("get_live_categories")&&!action.equals("get_live_streams"))throw new IllegalArgumentException("Only live API actions are allowed");
        return server+"/player_api.php?username="+segment(user)+"&password="+segment(pass)+(action.isEmpty()?"":"&action="+action);
    }
    static List<String> formats(JSONObject auth)throws Exception{
        JSONObject user=auth.optJSONObject("user_info");if(user==null||user.optInt("auth")!=1)throw new Exception("بيانات الاشتراك غير صحيحة أو غير مصرح بها");
        String state=user.optString("status");if(!state.isEmpty()&&!state.equalsIgnoreCase("Active"))throw new Exception("الاشتراك غير نشط أو منتهي");
        JSONArray raw=user.optJSONArray("allowed_output_formats");List<String> out=new ArrayList<>();
        for(String format:new String[]{"m3u8","ts"})if(raw==null||raw.length()==0||raw.toString().contains("\""+format+"\""))out.add(format);
        if(out.isEmpty())throw new Exception("السيرفر لا يعلن صيغة HLS أو MPEG-TS مدعومة");return out;
    }
    static JSONObject catalogue(String account,JSONArray categories,JSONArray streams,List<String> formats)throws Exception{
        JSONObject result=new JSONObject().put("account",account).put("updated",System.currentTimeMillis()).put("categories",categories).put("formats",new JSONArray(formats));JSONArray channels=new JSONArray();Set<String> seen=new HashSet<>();
        for(int i=0;i<streams.length();i++){
            JSONObject raw=streams.optJSONObject(i);if(raw==null)continue;String id=raw.optString("stream_id"),name=raw.optString("name").trim(),type=raw.optString("stream_type","live");
            if(!id.matches("[0-9]+")||name.isEmpty()||(!type.equals("live")&&!type.equals("created_live"))||!seen.add(id))continue;
            channels.put(new JSONObject().put("id",id).put("name",name).put("category",raw.optString("category_id")).put("logo",LiveTvData.web(raw.optString("stream_icon"))));
        }
        result.put("channels",channels);return result;
    }
    static List<LiveTvData.Channel> channels(JSONObject catalog)throws Exception{
        List<LiveTvData.Channel> out=new ArrayList<>();String account=catalog.getString("account");JSONArray raw=catalog.getJSONArray("channels"),formats=catalog.getJSONArray("formats");
        for(int i=0;i<raw.length();i++){
            JSONObject row=raw.getJSONObject(i);LiveTvData.Channel c=new LiveTvData.Channel();c.id="xtream:"+account+":"+row.getString("id");c.name=c.sourceName=row.getString("name");c.groups=row.optString("category");c.logo="xtream-logo://"+account+"/"+row.getString("id");
            for(int j=0;j<formats.length();j++){LiveTvData.Endpoint e=new LiveTvData.Endpoint();e.url="xtream://"+account+"/"+row.getString("id")+"."+formats.getString(j);c.streams.add(e);}out.add(c);
        }
        return out;
    }
    static boolean subscription(LiveTvData.Channel c){return c!=null&&c.id.startsWith("xtream:");}
}
