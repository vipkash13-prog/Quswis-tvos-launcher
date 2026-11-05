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
    private final Runnable hide = () -> {
        if (Profiles.homePrefs(activity).getBoolean("ouswis_smart_autohide", true)) {
            island.setVisibility(View.GONE);
            strip.setVisibility(View.GONE);
        }
    };

    OuswisSmartOverlay(Activity activity, FrameLayout root) {
        this.activity = activity;
        this.root = root;
        island = makePill(0xB91B2938, 15);
        strip = makePill(0x9A25384A, 13);
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

    void refresh() {
        handler.removeCallbacks(hide);
        boolean showIsland = Profiles.homePrefs(activity).getBoolean("ouswis_smart_island", true);
        boolean showStrip = Profiles.homePrefs(activity).getBoolean("ouswis_glass_strip", true);
        // A real local date/time, not invented sports scores or prayer times.
        String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
        String date = new SimpleDateFormat("EEEE، d MMMM", new Locale("ar")).format(new Date());
        island.setText("✦  OUSWIS  ·  " + time);
        strip.setText("✦  " + date + "  ✦");
        island.setVisibility(showIsland ? View.VISIBLE : View.GONE);
        strip.setVisibility(showStrip ? View.VISIBLE : View.GONE);
        island.bringToFront();
        strip.bringToFront();
        if (Profiles.homePrefs(activity).getBoolean("ouswis_smart_autohide", true)) {
            handler.postDelayed(hide, 10000L);
        }
    }

    void dispose() {
        handler.removeCallbacks(hide);
    }
}
