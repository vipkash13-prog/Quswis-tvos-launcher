package com.techgenius.tvoslauncher;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Lightweight, non-focusable home overlays. Never changes dock layout or DPAD navigation. */
final class OuswisSmartOverlay {
    private final Activity activity;
    private final FrameLayout root;
    private final TextView island;
    private final TextView strip;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable hide;
    private final Runnable eventTick;
    private String lastPrayerEvent = "";
    private boolean disposed;

    OuswisSmartOverlay(Activity activity, FrameLayout root) {
        this.activity = activity;
        this.root = root;
        island = makePill(0xB91B2938, 15);
        strip = makePill(0x9A25384A, 13);
        hide = () -> {
            if (Profiles.homePrefs(this.activity).getBoolean("ouswis_smart_autohide", true)) {
                island.setVisibility(View.GONE);
                strip.setVisibility(View.GONE);
            }
        };
        eventTick = new Runnable() {
            @Override public void run() {
                if (disposed) return;
                checkPrayerEvent();
                handler.postDelayed(this, 30000L);
            }
        };
        handler.postDelayed(eventTick, 30000L);
        FrameLayout.LayoutParams top = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(38), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        top.topMargin = dp(12);
        root.addView(island, top);
        FrameLayout.LayoutParams bottom = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(34), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        bottom.bottomMargin = Math.round(root.getResources().getDisplayMetrics().heightPixels * 0.29f);
        root.addView(strip, bottom);
        refresh();
    }

    private int dp(float value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private TextView makePill(int background, int textSize) {
        TextView view = new TextView(activity);
        view.setTextColor(Color.WHITE);
        view.setTextSize(textSize);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(19), 0, dp(19), 0);
        view.setFocusable(false);
        view.setClickable(false);
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(background);
        shape.setCornerRadius(dp(24));
        shape.setStroke(dp(1), 0x55FFFFFF);
        view.setBackground(shape);
        view.setElevation(dp(4));
        return view;
    }

    private void checkPrayerEvent() {
        if (!PrayerTimes.enabled(activity)) return;
        android.content.SharedPreferences prefs = PrayerTimes.prefs(activity);
        try {
            org.json.JSONObject times = new org.json.JSONObject(prefs.getString("data", "{}"));
            org.json.JSONObject meta = new org.json.JSONObject(prefs.getString("detail_metadata", "{}"));
            String zone = meta.optString("timezone", java.util.TimeZone.getDefault().getID());
            java.util.TimeZone tz = java.util.TimeZone.getTimeZone(zone);
            java.text.SimpleDateFormat day = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US);
            day.setTimeZone(tz);
            long now = System.currentTimeMillis();
            if (!day.format(new Date(now)).equals(meta.optString("day", prefs.getString("cache_date", "")))) return;
            String[] names = {"الفجر", "الظهر", "العصر", "المغرب", "العشاء"};
            String[] keys = {"Fajr", "Dhuhr", "Asr", "Maghrib", "Isha"};
            String[] values = new String[5];
            for (int i = 0; i < 5; i++) values[i] = times.optString(keys[i], "--:--");
            PrayerPanelClock.Result next = PrayerPanelClock.next(values, zone, now);
            if (next == null || next.tomorrow) return;
            long remaining = next.at - now;
            if (remaining < 0 || remaining > 5 * 60 * 1000L) return;
            String key = day.format(new Date(now)) + ":" + next.index;
            if (key.equals(lastPrayerEvent)) return;
            lastPrayerEvent = key;
            showEvent("الصلاة القادمة: " + names[next.index],
                    "يتبقى " + Math.max(1, (remaining + 59999L) / 60000L) + " دقائق على الأذان");
        } catch (Exception ignored) {
            // Missing or invalid cached prayer data must not affect the launcher.
        }
    }

    void showEvent(String title, String detail) {
        handler.removeCallbacks(hide);
        boolean showIsland = Profiles.homePrefs(activity).getBoolean("ouswis_smart_island", true);
        boolean showStrip = Profiles.homePrefs(activity).getBoolean("ouswis_glass_strip", true);
        island.setText("✦  " + title);
        strip.setText(detail);
        island.setVisibility(showIsland ? View.VISIBLE : View.GONE);
        strip.setVisibility(showStrip ? View.VISIBLE : View.GONE);
        island.bringToFront();
        strip.bringToFront();
        if (Profiles.homePrefs(activity).getBoolean("ouswis_smart_autohide", true))
            handler.postDelayed(hide, 10000L);
    }

    void refresh() {
        handler.removeCallbacks(hide);
        boolean showIsland = Profiles.homePrefs(activity).getBoolean("ouswis_smart_island", true);
        boolean showStrip = Profiles.homePrefs(activity).getBoolean("ouswis_glass_strip", true);
        island.setVisibility(View.GONE);
        strip.setVisibility(View.GONE);
        checkPrayerEvent();
    }

    void dispose() {
        disposed = true;
        handler.removeCallbacks(eventTick);
        handler.removeCallbacks(hide);
    }
}
