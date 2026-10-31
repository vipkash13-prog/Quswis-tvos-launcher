package com.techgenius.tvoslauncher;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Prayer times from AlAdhan, cached locally and refreshed in the background. */
final class PrayerTimes {
    private static final String PREFS = "prayer_times";
    static final String K_ENABLED = "home_enabled";
    private static final String K_DATA = "data", K_AT = "at";
    private static volatile boolean busy;
    private PrayerTimes() {}

    static boolean enabled(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(K_ENABLED, false);
    }
    static void setEnabled(Context c, boolean value) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(K_ENABLED, value).apply();
    }
    static TextView createView(Context c) {
        TextView v = new TextView(c);
        v.setTextColor(Color.WHITE);
        v.setTextSize(13);
        v.setGravity(Gravity.CENTER_VERTICAL);
        v.setPadding(Ui.px(c, 16), Ui.px(c, 9), Ui.px(c, 16), Ui.px(c, 9));
        v.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xD91B2633);
        bg.setCornerRadius(Ui.px(c, 15));
        bg.setStroke(Ui.px(c, 1), 0x557FC8B4);
        v.setBackground(bg);
        v.setFocusable(false);
        return v;
    }
    static void refresh(Context context, TextView view, boolean force) {
        if (!enabled(context)) { view.setVisibility(android.view.View.GONE); return; }
        view.setVisibility(android.view.View.VISIBLE);
        String cached = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(K_DATA, null);
        long at = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(K_AT, 0);
        if (cached != null) view.setText(format(cached));
        else view.setText("مواقيت الصلاة  •  جارٍ التحديث…");
        if (busy || (!force && System.currentTimeMillis() - at < 60 * 60 * 1000L)) return;
        busy = true;
        Context app = context.getApplicationContext();
        new Thread(() -> {
            String result = null;
            HttpURLConnection conn = null;
            try {
                String city = Weather.place(app);
                if (city == null || city.trim().isEmpty()) city = "Jeddah";
                String url = "https://api.aladhan.com/v1/timingsByCity?city="
                        + URLEncoder.encode(city, "UTF-8") + "&country=Saudi%20Arabia&method=4";
                conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setConnectTimeout(9000); conn.setReadTimeout(9000);
                conn.setRequestProperty("User-Agent", Weather.UA);
                BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                StringBuilder b = new StringBuilder(); String line;
                while ((line = r.readLine()) != null) b.append(line);
                r.close();
                JSONObject timings = new JSONObject(b.toString()).getJSONObject("data").getJSONObject("timings");
                result = timings.toString();
                app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                        .putString(K_DATA, result).putLong(K_AT, System.currentTimeMillis()).apply();
            } catch (Exception ignored) {
            } finally { if (conn != null) conn.disconnect(); }
            final String data = result != null ? result : app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(K_DATA, null);
            new Handler(Looper.getMainLooper()).post(() -> {
                busy = false;
                if (view != null && enabled(view.getContext())) {
                    view.setVisibility(android.view.View.VISIBLE);
                    view.setText(data == null ? "مواقيت الصلاة غير متاحة حالياً" : format(data));
                }
            });
        }).start();
    }
    private static String format(String raw) {
        try {
            JSONObject t = new JSONObject(raw);
            String[] keys = {"Fajr","Dhuhr","Asr","Maghrib","Isha"};
            String[] names = {"الفجر","الظهر","العصر","المغرب","العشاء"};
            StringBuilder s = new StringBuilder("مواقيت الصلاة");
            for (int i=0;i<keys.length;i++) {
                String value = t.optString(keys[i], "--").replaceAll("\\s*\\(.*?\\)", "");
                s.append(i == 0 ? "   " : "   •   ").append(names[i]).append(" ").append(value);
            }
            return s.toString();
        } catch (Exception e) { return "مواقيت الصلاة غير متاحة حالياً"; }
    }
}
