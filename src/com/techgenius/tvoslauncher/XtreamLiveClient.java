package com.techgenius.tvoslauncher;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/** Live-channel-only Xtream Codes API client. Never requests VOD or series endpoints. */
final class XtreamLiveClient {
    static final String SERVER = "http://cli2345.live";
    private static final String PREFS = "xtream_live";
    static final class Channel {
        final String id, name, logo, category;
        Channel(String id, String name, String logo, String category) {
            this.id=id; this.name=name; this.logo=logo; this.category=category;
        }
    }
    static final class Category {
        final String id, name;
        Category(String id, String name) { this.id=id; this.name=name; }
    }
    private XtreamLiveClient() {}
    private static String enc(String s) throws Exception { return URLEncoder.encode(s, "UTF-8"); }
    private static JSONArray request(String user, String pass, String action) throws Exception {
        if (user == null || user.trim().isEmpty() || pass == null || pass.isEmpty())
            throw new IllegalArgumentException("Username and password required");
        String uri = SERVER + "/player_api.php?username=" + enc(user)
                + "&password=" + enc(pass) + "&action=" + action;
        HttpURLConnection connection = (HttpURLConnection) new URL(uri).openConnection();
        try {
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(15000);
            connection.setRequestProperty("Accept", "application/json");
            if (connection.getResponseCode() != 200)
                throw new Exception("Server returned HTTP " + connection.getResponseCode());
            StringBuilder data = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"))) {
                String line;
                while ((line=reader.readLine()) != null) {
                    data.append(line);
                    if (data.length() > 12000000) throw new Exception("Channel response too large");
                }
            }
            return new JSONArray(data.toString());
        } finally { connection.disconnect(); }
    }
    static List<Category> categories(String user, String pass) throws Exception {
        JSONArray arr=request(user,pass,"get_live_categories");
        List<Category> out=new ArrayList<>();
        for(int i=0;i<arr.length();i++) {
            org.json.JSONObject o=arr.optJSONObject(i);
            if(o!=null)out.add(new Category(o.optString("category_id"),o.optString("category_name")));
        }
        return out;
    }
    static List<Channel> channels(String user, String pass) throws Exception {
        JSONArray arr=request(user,pass,"get_live_streams");
        List<Channel> out=new ArrayList<>();
        for(int i=0;i<arr.length();i++) {
            org.json.JSONObject o=arr.optJSONObject(i);
            if(o!=null && !o.optString("stream_id").isEmpty())
                out.add(new Channel(o.optString("stream_id"),o.optString("name"),o.optString("stream_icon"),o.optString("category_id")));
        }
        return out;
    }
    static String playbackUrl(String user,String pass,String streamId,boolean hls) throws Exception {
        if(!streamId.matches("[0-9]+"))throw new IllegalArgumentException("Invalid stream id");
        return SERVER+"/live/"+enc(user)+"/"+enc(pass)+"/"+streamId+(hls?".m3u8":".ts");
    }
}
