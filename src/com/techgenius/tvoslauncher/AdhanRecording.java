package com.techgenius.tvoslauncher;

import android.content.Context;

/** Offline recordings with explicit attribution; neither source documents a recording date. */
final class AdhanRecording {
 static final String[] TITLES={"الحرم المكي • الشيخ علي ملا","المسجد النبوي • عاصم بخاري"};
 private static final String[] ASSETS={"audio/haram_ali_mulla.mp3","audio/madinah_assem_bukhrare.mp3"};
 static int index(Context c){int i=PrayerTimes.prefs(c).getInt("adhan_recording",0);return i>=0&&i<ASSETS.length?i:0;}
 static String title(Context c){return TITLES[index(c)];}
 static String asset(Context c){return ASSETS[index(c)];}
}
