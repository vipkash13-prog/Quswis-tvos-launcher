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
  shortcut.setPadding(Ui.px(activity,15),0,Ui.px(activity,15),0);
  GradientDrawable bg=new GradientDrawable();
  bg.setColor(0x65304459);
  bg.setCornerRadius(Ui.px(activity,20));
  bg.setStroke(Ui.px(activity,1),0x90FFFFFF);
  shortcut.setBackground(bg);
  TextView text=new TextView(activity);
  text.setText("🕌  مواقيت الصلاة");
  text.setTextColor(Color.WHITE);
  text.setTextSize(17);
  text.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
  text.setGravity(Gravity.CENTER_VERTICAL);
  shortcut.addView(text);
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
  LinearLayout content=new LinearLayout(activity);
  content.setFocusable(true);
  content.setOrientation(LinearLayout.VERTICAL);
  content.setPadding(Ui.px(activity,28),Ui.px(activity,22),Ui.px(activity,28),Ui.px(activity,22));
  GradientDrawable glass=new GradientDrawable();
  glass.setColor(0xE51C3045);
  glass.setCornerRadius(Ui.px(activity,24));
  glass.setStroke(Ui.px(activity,1),0xCCFFFFFF);
  content.setBackground(glass);
  TextView heading=new TextView(activity);
  heading.setText("مواقيت الصلاة • "+PrayerTimes.city(activity));
  heading.setTextColor(Color.WHITE);
  heading.setTextSize(23);
  heading.setGravity(Gravity.CENTER);
  content.addView(heading);
  String[] names={"الفجر","الظهر","العصر","المغرب","العشاء"};
  String[] keys={"Fajr","Dhuhr","Asr","Maghrib","Isha"};
  JSONObject times=null;
  try {times=new JSONObject(PrayerTimes.prefs(activity).getString("data","{}"));}catch(Exception ignored){}
  for(int i=0;i<names.length;i++){
   TextView row=new TextView(activity);
   String value=times==null?"--:--":times.optString(keys[i],"--:--");
   int cut=value.indexOf(' ');if(cut>0)value=value.substring(0,cut);
   row.setText(names[i]+"                    "+value);
   row.setTextSize(19);row.setTextColor(Color.WHITE);
   row.setPadding(0,Ui.px(activity,13),0,Ui.px(activity,13));
   content.addView(row);
  }
  PrayerTimes.refresh(activity, null, false);
  return content;
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
