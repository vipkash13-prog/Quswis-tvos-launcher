package com.techgenius.tvoslauncher;

import java.util.ArrayList;
import java.util.List;

/** One checked shell stream per operation; unsupported optional TV features cannot stop setup. */
final class AdbGrantPlan {
    static final String EXIT = "__TVOS_EXIT__:";
    static final class Step {
        final String label, command;
        final boolean required;
        Step(String label, String command, boolean required) {
            this.label = label;
            this.command = command + "; tvos_rc=$?; echo '" + EXIT + "'$tvos_rc";
            this.required = required;
        }
    }
    static String quote(String value) { return "'" + value.replace("'", "'\\''") + "'"; }
    static List<Step> create(String pkg, String services, int sdk, boolean listener, boolean admin) {
        List<Step> steps = new ArrayList<>();
        String app = quote(pkg);
        // Android 12+ requires coarse location before fine location.
        steps.add(new Step("Coarse location", "pm grant " + app + " android.permission.ACCESS_COARSE_LOCATION", true));
        steps.add(new Step("Fine location", "pm grant " + app + " android.permission.ACCESS_FINE_LOCATION", true));
        if (sdk <= 32) {
            steps.add(new Step("Read storage", "pm grant " + app + " android.permission.READ_EXTERNAL_STORAGE", false));
            steps.add(new Step("Write storage", "pm grant " + app + " android.permission.WRITE_EXTERNAL_STORAGE", false));
        } else {
            steps.add(new Step("Media images", "pm grant " + app + " android.permission.READ_MEDIA_IMAGES", false));
        }
        if (sdk >= 30) steps.add(new Step("Media storage", "appops set " + app + " MANAGE_EXTERNAL_STORAGE allow", false));
        if (sdk >= 26) steps.add(new Step("Install updates", "appops set " + app + " REQUEST_INSTALL_PACKAGES allow", false));
        steps.add(new Step("Usage access", "appops set " + app + " GET_USAGE_STATS allow", true));
        if (listener) steps.add(new Step("Continue Watching", "cmd notification allow_listener " + quote(pkg + "/" + pkg + ".MediaWatchService"), false));
        if (admin) steps.add(new Step("Device admin", "dpm set-active-admin " + quote(pkg + "/.SleepReceiver$Admin"), false));
        steps.add(new Step("Accessibility service", "settings put secure enabled_accessibility_services " + quote(services), true));
        steps.add(new Step("Enable accessibility", "settings put secure accessibility_enabled 1", true));
        steps.add(new Step("Default Home", "cmd package set-home-activity " + quote(pkg + "/.MainActivity"), true));
        return steps;
    }
    static boolean succeeded(String output) {
        int marker = output == null ? -1 : output.lastIndexOf(EXIT);
        return marker >= 0 && "0".equals(output.substring(marker + EXIT.length()).trim());
    }
}
