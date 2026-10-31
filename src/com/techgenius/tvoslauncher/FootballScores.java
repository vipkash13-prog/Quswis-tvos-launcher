package com.techgenius.tvoslauncher;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** AFC Champions League Elite scoreboard using ESPN's public scoreboard feed. */
final class FootballScores {
    private static final String PREFS = "football_scores";
    private static final String K_DATA = "data", K_AT = "at";
    private static volatile boolean busy;
    private FootballScores() {}

    static TextView createView(Context c) {
        TextView v = new TextView(c);
        v.setTextColor(Color.WHITE); v.setTextSize(13);
        v.setGravity(Gravity.CENTER_VERTICAL); v.setPadding(Ui.px(c,16), Ui.px(c,10), Ui.px(c,16), Ui.px(c,10));
        v.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        GradientDrawable bg = new GradientDrawable(); bg.setColor(0xD91B2633);
        bg.setCornerRadius(Ui.px(c,15)); bg.setStroke(Ui.px(c,1), 0x557DA7D8); v.setBackground(bg);
        v.setFocusable(false); return v;
    }
    static void refresh(Context context, TextView view, boolean force) {
        String cached = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(K_DATA, null);
        long at = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(K_AT, 0);
        if (cached != null) view.setText(cached);
        else view.setText("مباريات دوري أبطال آسيا للنخبة  •  جارٍ التحديث…");
        if (busy || (!force && System.currentTimeMillis()-at < 30*60*1000L)) return;
        busy = true; Context app = context.getApplicationContext();
        new Thread(() -> {
            String result = null; HttpURLConnection conn = null;
            try {
                SimpleDateFormat f = new SimpleDateFormat("yyyyMMdd", Locale.US);
                f.setTimeZone(TimeZone.getTimeZone("Asia/Riyadh"));
                String today = f.format(new Date());
                long end = System.currentTimeMillis() + 6L*24*60*60*1000;
                String until = f.format(new Date(end));
                URL url = new URL("https://site.api.espn.com/apis/site/v2/sports/soccer/afc.champions/scoreboard?dates="+today+"-"+until);
                conn = (HttpURLConnection)url.openConnection(); conn.setConnectTimeout(10000); conn.setReadTimeout(10000);
                conn.setRequestProperty("User-Agent", Weather.UA);
                BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                StringBuilder b = new StringBuilder(); String line; while((line=r.readLine())!=null)b.append(line); r.close();
                JSONObject root = new JSONObject(b.toString()); JSONArray events = root.optJSONArray("events");
                StringBuilder out = new StringBuilder("دوري أبطال آسيا للنخبة");
                int shown = 0;
                if (events != null) for (int i=0;i<events.length() && shown<3;i++) {
                    JSONObject event = events.getJSONObject(i);
                    JSONArray comps = event.optJSONArray("competitions");
                    if (comps == null || comps.length()==0) continue;
                    JSONArray competitors = comps.getJSONObject(0).optJSONArray("competitors");
                    if (competitors == null || competitors.length()<2) continue;
                    JSONObject home = competitors.getJSONObject(0), away = competitors.getJSONObject(1);
                    String h = home.optJSONObject("team") == null ? "الفريق الأول" : home.getJSONObject("team").optString("displayName","الفريق الأول");
                    String a = away.optJSONObject("team") == null ? "الفريق الثاني" : away.getJSONObject("team").optString("displayName","الفريق الثاني");
                    String hs = home.optString("score","-"), as = away.optString("score","-");
                    JSONObject status = event.optJSONObject("status");
                    String detail = status == null ? "" : status.optJSONObject("type") == null ? "" : status.getJSONObject("type").optString("shortDetail","");
                    out.append("\n").append(h).append("  ").append(hs).append(" - ").append(as).append("  ").append(a);
                    if (!detail.isEmpty()) out.append("   ").append(detail);
                    shown++;
                }
                if (shown==0) out.append("\nلا توجد مباريات مجدولة خلال الأيام القادمة");
                result = out.toString();
                app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(K_DATA,result).putLong(K_AT,System.currentTimeMillis()).apply();
            } catch (Exception ignored) {
            } finally { if(conn!=null)conn.disconnect(); }
            final String data = result != null ? result : app.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString(K_DATA,null);
            new Handler(Looper.getMainLooper()).post(() -> {
                busy=false;
                view.setText(data == null ? "مباريات دوري أبطال آسيا للنخبة غير متاحة حالياً" : data);
            });
        }).start();
    }
}
