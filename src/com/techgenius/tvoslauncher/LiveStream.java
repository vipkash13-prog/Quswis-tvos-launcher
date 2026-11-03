package com.techgenius.tvoslauncher;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;

/** The "live broadcast" shortcut on Home: shown or hidden from Settings, and it opens one chosen app (TiviMate by default). */
final class LiveStream {
    static final String DEFAULT_PACKAGE = "ar.tvplayer.tv";

    private LiveStream() {
    }

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("livestream", Context.MODE_PRIVATE);
    }

    static boolean enabled(Context c) {
        return prefs(c).getBoolean("enabled", false);
    }

    static void setEnabled(Context c, boolean on) {
        prefs(c).edit().putBoolean("enabled", on).apply();
    }

    static String packageName(Context c) {
        return prefs(c).getString("package", DEFAULT_PACKAGE);
    }

    static void setPackage(Context c, String pkg) {
        prefs(c).edit().putString("package", pkg).apply();
    }

    /** The app's name, or null when it is not installed. */
    static String appLabel(Context c, String pkg) {
        try {
            PackageManager pm = c.getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (Exception e) {
            return null;
        }
    }

    /** Starts the chosen app. False when it is not installed or cannot be opened. */
    static boolean launch(Context c) {
        try {
            PackageManager pm = c.getPackageManager();
            Intent i = pm.getLeanbackLaunchIntentForPackage(packageName(c));
            if (i == null) i = pm.getLaunchIntentForPackage(packageName(c));
            if (i == null) return false;
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
