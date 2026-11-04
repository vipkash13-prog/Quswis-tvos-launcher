package com.techgenius.tvoslauncher;

import android.content.Context;
import android.media.*;
import android.os.*;

/** One in-app media owner; transient interruptions pause rather than leaving a duck multiplier. */
final class PlaybackAudio {
    interface Client { void pauseForFocus(); void resumeAtUnity(); void replaced(); }
    private static PlaybackAudio current;
    private static boolean adhan;
    private final AudioManager manager;
    private final Client client;
    private final AudioAttributes attributes;
    private final Handler main = new Handler(Looper.getMainLooper());
    private AudioFocusRequest request;
    private boolean desired, focused, interrupted, closed, requested;
    private final AudioManager.OnAudioFocusChangeListener listener = this::focusChanged;
    PlaybackAudio(Context c, int content, Client client) {
        manager = c.getApplicationContext().getSystemService(AudioManager.class);
        attributes = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(content).build();
        this.client = client;
    }
    boolean playIntent() {
        if (closed) return false;
        if (current != this) {
            PlaybackAudio old = current; current = this;
            if (old != null) { old.desired = false; old.interrupted = false; old.abandon(); old.client.replaced(); }
        }
        desired = true;
        if (adhan) { interrupted = true; client.pauseForFocus(); return false; }
        interrupted = false;
        return acquire();
    }
    void pauseIntent() { desired = false; interrupted = false; abandon(); }
    boolean wantsPlay() { return desired; }
    boolean focused() { return focused; }
    boolean interrupted() { return interrupted; }
    private boolean acquire() {
        if (focused) return true;
        if (manager == null) return false;
        int result;
        if (Build.VERSION.SDK_INT >= 26) {
            if (request == null) request = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(attributes).setWillPauseWhenDucked(true).setAcceptsDelayedFocusGain(true)
                    .setOnAudioFocusChangeListener(listener, main).build();
            result = manager.requestAudioFocus(request);
        } else result = manager.requestAudioFocus(listener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        requested = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED || result == AudioManager.AUDIOFOCUS_REQUEST_DELAYED;
        focused = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        if(!focused)interrupted=desired;
        android.util.Log.i("QuswisAudio", "focus request=" + result + " usage=MEDIA content=" + attributes.getContentType());
        return focused;
    }
    private void focusChanged(int change) {
        if (closed || current != this || !requested) return;
        android.util.Log.i("QuswisAudio", "focus change=" + change + " wanted=" + desired + " adhan=" + adhan);
        if (change == AudioManager.AUDIOFOCUS_GAIN) {
            focused = true;
            if (desired && !adhan) { interrupted = false; client.resumeAtUnity(); }
        } else if (change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
            focused = false; interrupted = desired; client.pauseForFocus();
        } else if (change == AudioManager.AUDIOFOCUS_LOSS) {
            desired = false; interrupted = false; focused = false; client.pauseForFocus(); abandon();
        }
    }
    private void abandon() {
        focused = false;
        if (!requested || manager == null) return;
        requested = false;
        if (Build.VERSION.SDK_INT >= 26 && request != null) manager.abandonAudioFocusRequest(request);
        else manager.abandonAudioFocus(listener);
    }
    void release() { if (closed) return; closed = true; desired = false; interrupted = false; abandon(); if (current == this) current = null; main.removeCallbacksAndMessages(null); }
    static void beginAdhan() {
        adhan = true;
        PlaybackAudio owner = current;
        if (owner != null) { owner.interrupted = owner.desired; owner.client.pauseForFocus(); owner.abandon(); }
    }
    static void endAdhan(boolean restore) {
        adhan = false;
        PlaybackAudio owner = current;
        if (!restore && owner != null) { owner.desired = false; owner.interrupted = false; }
        if (owner != null && !owner.closed && owner.desired) {
            owner.interrupted = false;
            if (owner.acquire()) owner.client.resumeAtUnity();
        }
    }
    static boolean adhanRunning() { return adhan; }
}
