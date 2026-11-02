package com.techgenius.tvoslauncher;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONObject;

/** Compact classic shortcut; prayer details open without leaving the launcher. */
final class PrayerShortcut {
 private PrayerShortcut() {}
 static LinearLayout create(Activity activity) {
  LinearLayout shortcut=new LinearLayout(activity);
  shortcut.setOrientation(LinearLayout.HORIZONTAL);
  shortcut.setGravity(Gravity.CENTER_VERTICAL);
  shortcut.setPadding(Ui.px(activity,14),0,Ui.px(activity,12),0);
  GradientDrawable bg=new GradientDrawable();
  bg.setColor(0x2E000000);
  bg.setCornerRadius(Ui.px(activity,26));
  bg.setStroke(Ui.px(activity,1),0xBFFFFFFF);
  shortcut.setBackground(bg);
  TextView text=new TextView(activity);
  text.setText("مواقيت الصلاة");
  text.setTextColor(0xFFFFD995);
  text.setTextSize(18);
  text.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
  text.setGravity(Gravity.CENTER_VERTICAL);
  shortcut.addView(text,new LinearLayout.LayoutParams(0,-2,1f));
  TextView mosque=new TextView(activity);
  mosque.setText("🕌");
  mosque.setTextSize(29);
  mosque.setGravity(Gravity.CENTER);
  shortcut.addView(mosque,new LinearLayout.LayoutParams(Ui.px(activity,55),-1));
  shortcut.setFocusable(true);
  shortcut.setClickable(true);
  shortcut.setOnFocusChangeListener((v,focused)->{
   shortcut.animate().scaleX(focused?1.045f:1f).scaleY(focused?1.045f:1f).setDuration(190).start();
   bg.setStroke(Ui.px(activity,focused?2:1),focused?0xEEFFFFFF:0x90FFFFFF);
  });
  shortcut.setOnClickListener(v->{if(activity instanceof MainActivity)((MainActivity)activity).showPrayerPanel();else open(activity);});
  return shortcut;
 }
 static View createPanel(Activity activity) {
  return PrayerTimes.createPanel(activity);
 }

 static void open(Activity activity) {
  View content=createPanel(activity);
  AlertDialog dialog=new AlertDialog.Builder(activity).setView(content).create();
  dialog.setOnShowListener(d->{
   if(dialog.getWindow()!=null){
    dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
    dialog.getWindow().getAttributes().windowAnimations=android.R.style.Animation_Dialog;
   }
  });
  dialog.show();
  PrayerScheduler.schedule(activity);
 }
}
