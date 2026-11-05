package com.techgenius.tvoslauncher;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Xtream integration for LIVE streams only. No VOD or series API is ever requested. */
final class XtreamLiveSource {
    private XtreamLiveSource() {}
    private static String enc(String value)throws Exception{return URLEncoder.encode(value,"UTF-8").replace("+","%20");}

    static List<LiveTvData.Channel> channels(Context context)throws Exception {
        XtreamAccount.Credentials account=XtreamAccount.load(context);
        if(account==null)return new ArrayList<>();
        String endpoint=account.server+"/player_api.php?username="+enc(account.username)
                +"&password="+enc(account.password)+"&action=get_live_streams";
        HttpURLConnection connection=(HttpURLConnection)new URL(endpoint).openConnection();
        try {
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(20000);
            connection.setRequestProperty("Accept","application/json");
            connection.setRequestProperty("User-Agent","Quswis-LiveTV/1.0");
            int code=connection.getResponseCode();
            if(code!=200)throw new IOException("Xtream HTTP "+code);
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            try(InputStream in=connection.getInputStream()){
                byte[] buffer=new byte[8192];int count;
                while((count=in.read(buffer))!=-1){
                    bytes.write(buffer,0,count);
                    if(bytes.size()>20*1024*1024)throw new IOException("Xtream live list is too large");
                }
            }
            JSONArray array=new JSONArray(bytes.toString("UTF-8"));
            List<LiveTvData.Channel> result=new ArrayList<>();
            Set<String> seen=new HashSet<>();
            for(int i=0;i<array.length();i++){
                JSONObject item=array.optJSONObject(i);
                if(item==null)continue;
                String id=item.optString("stream_id","");
                String name=item.optString("name","").trim();
                if(!id.matches("[0-9]+")||name.isEmpty()||!seen.add(id))continue;
                LiveTvData.Channel channel=new LiveTvData.Channel();
                channel.id="xtream:"+id;
                channel.name=name;
                channel.sourceName=name;
                channel.logo=LiveTvData.web(item.optString("stream_icon",""));
                channel.groups="Xtream Live";
                channel.website=account.server;
                String extension=item.optString("container_extension","m3u8").trim().toLowerCase(java.util.Locale.ROOT);
                if(!extension.matches("[a-z0-9]{1,8}"))extension="m3u8";
                LiveTvData.Endpoint primary=new LiveTvData.Endpoint();
                primary.url=account.server+"/live/"+enc(account.username)+"/"+enc(account.password)+"/"+id+"."+extension;
                primary.feed="Xtream Live";
                channel.streams.add(primary);
                if(!extension.equals("ts")){
                    LiveTvData.Endpoint fallback=new LiveTvData.Endpoint();
                    fallback.url=account.server+"/live/"+enc(account.username)+"/"+enc(account.password)+"/"+id+".ts";
                    fallback.feed="Xtream Live fallback";
                    channel.streams.add(fallback);
                }
                result.add(channel);
            }
            return result;
        } finally {
            connection.disconnect();
        }
    }
}
