package com.techgenius.tvoslauncher;

import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Drives the real launcher on Android 14 against a local Xtream-style server (127.0.0.1:8099):
 * account form + remote navigation + typing the user name, live list, and real HLS and MPEG-TS playback.
 * Package-private classes are reached by reflection because this APK uses a separate class loader.
 */
public final class LiveFixInstrumentation extends Instrumentation {
    private Object dialog;
    private void check(boolean ok, String msg) { if (!ok) throw new AssertionError(msg); }
    private void progress(String s) { Bundle b = new Bundle(); b.putString("stream", s + "\n"); sendStatus(1, b); }
    private static Object field(Object o, String name) throws Exception {
        Class<?> c = o.getClass();
        while (c != null) { try { Field f = c.getDeclaredField(name); f.setAccessible(true); return f.get(o); } catch (NoSuchFieldException e) { c = c.getSuperclass(); } }
        throw new NoSuchFieldException(name);
    }
    private static Object call(Object o, String name, Object... args) throws Exception {
        for (Method m : o.getClass().getDeclaredMethods())
            if (m.getName().equals(name) && m.getParameterTypes().length == args.length) { m.setAccessible(true); return m.invoke(o, args); }
        throw new NoSuchMethodException(name);
    }
    @Override public void onCreate(Bundle a) { super.onCreate(a); start(); }
    private View focus() throws Exception { View[] v = new View[1]; runOnMainSync(() -> { try { v[0] = ((android.app.Dialog) dialog).getCurrentFocus(); } catch (Throwable ignored) {} }); return v[0]; }
    private int count() throws Exception { int[] n = {0}; runOnMainSync(() -> { try { n[0] = ((List<?>) field(field(dialog, "adapter"), "items")).size(); } catch (Throwable t) { throw new RuntimeException(t); } }); return n[0]; }
    private boolean waitFor(java.util.concurrent.Callable<Boolean> c, int seconds) throws Exception {
        long end = SystemClock.uptimeMillis() + seconds * 1000L;
        while (SystemClock.uptimeMillis() < end) { if (c.call()) return true; SystemClock.sleep(300); }
        return false;
    }
    private boolean rendered() throws Exception { boolean[] r = {false}; runOnMainSync(() -> { try { r[0] = (Boolean) field(field(dialog, "engine"), "rendered"); } catch (Throwable t) { throw new RuntimeException(t); } }); return r[0]; }
    private String status() throws Exception { String[] r = {""}; runOnMainSync(() -> { try { r[0] = String.valueOf(((android.widget.TextView) field(dialog, "status")).getText()); } catch (Throwable t) { throw new RuntimeException(t); } }); return r[0]; }

    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            android.content.Context ctx = getTargetContext();
            ctx.getSharedPreferences("setup", 0).edit().putBoolean("setup_done", true).commit();
            Profiles.homePrefs(ctx).edit().putBoolean("asked_default_home", true).commit();
            ctx.getSharedPreferences("live_tv_center", 0).edit().putInt("section", 7).commit();
            Intent launch = new Intent(ctx, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            MainActivity host = (MainActivity) startActivitySync(launch);
            waitForIdleSync();
            progress("LIVEFIX_HOME_OPEN");
            Method open = Class.forName("com.techgenius.tvoslauncher.LiveTvDialog").getDeclaredMethod("open", MainActivity.class, View.class);
            open.setAccessible(true);
            runOnMainSync(() -> { try { open.invoke(null, host, host.getWindow().getDecorView()); } catch (Throwable t) { throw new RuntimeException(t); } });
            SystemClock.sleep(800);
            check(waitFor(() -> host.liveTvDialog != null, 10), "Live dialog did not open");
            dialog = host.liveTvDialog;
            progress("LIVEFIX_DIALOG_OPEN");

            // 1) Account form: remote navigation must reach the user-name field and accept typing.
            runOnMainSync(() -> { try { call(dialog, "editXtreamAccount"); } catch (Throwable t) { throw new RuntimeException(t); } });
            SystemClock.sleep(500); waitForIdleSync();
            View f0 = focus();
            check(f0 instanceof EditText && "رابط السيرفر (Host)".contentEquals(((EditText) f0).getHint()), "Host field must be focused first: " + f0);
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN); waitForIdleSync();
            View f1 = focus();
            check(f1 instanceof EditText && "اسم المستخدم (Username)".contentEquals(((EditText) f1).getHint()), "DPAD_DOWN must reach Username, got " + f1);
            runOnMainSync(() -> ((EditText) f1).setText(""));
            StringBuilder typed = new StringBuilder();
            for (int key : new int[]{KeyEvent.KEYCODE_T, KeyEvent.KEYCODE_E, KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_T}) { sendKeyDownUpSync(key); waitForIdleSync(); typed.append('[').append(((EditText) f1).getText()).append(']'); }
            progress("LIVEFIX_TYPED " + typed);
            check(((EditText) f1).getText().length() == 4, "Username accepts typing, got " + typed);
            runOnMainSync(() -> ((EditText) f1).setText("test"));
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN); waitForIdleSync();
            View f2 = focus();
            check(f2 instanceof EditText && "كلمة المرور (Password)".contentEquals(((EditText) f2).getHint()), "DPAD_DOWN must reach Password, got " + f2);
            runOnMainSync(() -> { ((EditText) f2).setText("secret"); ((EditText) f0).setText("http://127.0.0.1:8099"); });
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN); waitForIdleSync();
            View save = focus();
            check(save != null && !(save instanceof EditText), "DPAD_DOWN must reach the Save button, got " + save);
            progress("LIVEFIX_FORM_NAVIGATION_OK");
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER); waitForIdleSync();

            // 2) Live list appears from the account.
            check(waitFor(() -> count() >= 2, 40), "Live channels did not appear; status=" + status());
            progress("LIVEFIX_CHANNELS_" + count());
            Object first = ((List<?>) field(field(dialog, "adapter"), "items")).get(0);
            Object second = ((List<?>) field(field(dialog, "adapter"), "items")).get(1);

            // 3) Real playback, both formats, in the built-in player.
            for (Object channel : new Object[]{first, second}) {
                runOnMainSync(() -> { try { call(dialog, "play", channel, 0); } catch (Throwable t) { throw new RuntimeException(t); } });
                boolean ok = waitFor(this::rendered, 60);
                progress("LIVEFIX_PLAY " + field(channel, "name") + " rendered=" + ok + " status=" + status());
                check(ok, "Channel did not render a real video frame: " + field(channel, "name") + " / " + status());
            }
            progress("LIVEFIX_PLAYBACK_OK");
            result.putString("LIVEFIX", "OK");
            finish(android.app.Activity.RESULT_OK, result);
        } catch (Throwable t) {
            progress("LIVEFIX_FAILED " + t);
            android.util.Log.e("LiveFix", "failed", t);
            result.putString("LIVEFIX", "FAILED " + t);
            finish(android.app.Activity.RESULT_CANCELED, result);
        }
    }
}
