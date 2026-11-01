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
  heading.setTextColor(0xFFFFD995);
  heading.setPadding(0,0,0,Ui.px(activity,14));
  for(int i=0;i<names.length;i++){
   LinearLayout row=new LinearLayout(activity);
   row.setOrientation(LinearLayout.HORIZONTAL);
   row.setGravity(Gravity.CENTER_VERTICAL);
   row.setPadding(Ui.px(activity,16),Ui.px(activity,11),Ui.px(activity,16),Ui.px(activity,11));
   GradientDrawable rowBg=new GradientDrawable();
   rowBg.setColor(i%2==0?0x353B5A70:0x223B5A70);
   rowBg.setCornerRadius(Ui.px(activity,12));
   row.setBackground(rowBg);
   String value=times==null?"--:--":times.optString(keys[i],"--:--");
   int cut=value.indexOf(' ');if(cut>0)value=value.substring(0,cut);
   TextView time=new TextView(activity);
   time.setText(value);time.setTextSize(19);time.setTextColor(0xFFFFD995);
   time.setGravity(Gravity.CENTER_VERTICAL);
   row.addView(time,new LinearLayout.LayoutParams(0,-2,1f));
   TextView name=new TextView(activity);
   name.setText(names[i]);name.setTextSize(19);name.setTextColor(Color.WHITE);
   name.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
   name.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
   row.addView(name,new LinearLayout.LayoutParams(0,-2,1f));
   LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,0,1f);
   rp.bottomMargin=Ui.px(activity,5);
   content.addView(row,rp);
  }
  TextView note=new TextView(activity);
  note.setText("التنبيهات الصوتية من إعدادات مواقيت الصلاة");
  note.setTextSize(12);note.setTextColor(0xFFCFD8E3);
  note.setGravity(Gravity.CENTER);
  content.addView(note);
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
