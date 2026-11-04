package com.techgenius.tvoslauncher;

import android.app.Dialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/** A modal TV window: Home and all prayer/adhan services remain owned by their existing hosts. */
final class PrayerGlassDialog extends Dialog implements SharedPreferences.OnSharedPreferenceChangeListener {
    private final MainActivity host;
    private final View returnFocus;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final FrameLayout surface;
    private android.graphics.Bitmap panorama, iconAtlas, calendarArt;
    private final FrameLayout home;
    private final TextView date, status, next, hijriWeekday, gregorian;
    private final TextView[] clocks = new TextView[5];
    private final LinearLayout[] prayers = new LinearLayout[5];
    private final List<View> shortcuts = new ArrayList<>();
    private final String[] names = {"الفجر", "الظهر", "العصر", "المغرب", "العشاء"};
    private final String[] symbols = {"☀", "☀", "☀", "☀", "☾"};
    private boolean closing, subpage;
    private int requestGeneration;
    private long refreshAt;
    private View selectedShortcut;
    private final Runnable pulse = new Runnable() {
        @Override public void run() { render(); ui.postDelayed(this, 1000); }
    };

    static void open(MainActivity host, View anchor) {
        if (host.isFinishing()) return;
        new PrayerGlassDialog(host, anchor).show();
    }

    private PrayerGlassDialog(MainActivity activity, View anchor) {
        super(activity);
        host = activity; returnFocus = anchor;
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setCancelable(false);
        panorama = loadArt("prayer-reference/header.png");
        iconAtlas = loadArt("prayer-reference/icons.png");
        calendarArt = loadArt("prayer-reference/hijri.png");
        surface = new FrameLayout(host);
        GradientDrawable windowGlass = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xD32B364A, 0xEA1B202B});
        windowGlass.setCornerRadius(px(28));
        windowGlass.setStroke(px(.9f), 0x9AFFFFFF);
        surface.setBackground(windowGlass);
        rounded(surface, 28);
        home = new FrameLayout(host);
        home.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        surface.addView(home, new FrameLayout.LayoutParams(-1,-1));
        // Coordinates come directly from the supplied 1107 x 540 popup crop.
        View header = new View(host) {
            private final android.graphics.Paint p = new android.graphics.Paint(3);
            @Override protected void onDraw(android.graphics.Canvas c) {
                if (panorama == null || panorama.isRecycled()) return;
                int layer=c.saveLayer(0,0,getWidth(),getHeight(),null);
                // The generated production banner includes unused lower canvas padding.
                android.graphics.Rect src=new android.graphics.Rect(0,0,panorama.getWidth(),Math.round(panorama.getHeight()*.73f));
                c.drawBitmap(panorama,src,new android.graphics.RectF(0,0,getWidth(),getHeight()),p);
                p.setShader(new android.graphics.LinearGradient(0,0,0,getHeight(),
                        new int[]{0xFFFFFFFF,0xFFFFFFFF,0x00FFFFFF},new float[]{0,.87f,1},android.graphics.Shader.TileMode.CLAMP));
                p.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN));
                c.drawRect(0,0,getWidth(),getHeight(),p);
                p.setShader(null);p.setXfermode(null);c.restoreToCount(layer);
            }
        };
        put(home,header,0,0,1107,193);
        TextView title=text("الخدمات الإسلامية",25,true);
        title.setShadowLayer(px(2),0,px(1),0xEE000000);
        put(home,title,235,18,650,58);
        TextView close=button("×",27,this::dismiss);
        put(home,close,32,27,48,48);

        String[] labels={"القرآن الكريم","الأذكار\nوالتسبيح","إذاعات القرآن","التقويم الهجري"};
        int[] left={828,575,320,61}, widths={243,238,239,242};
        Runnable[] actions={this::openQuran,this::openDhikr,this::openRadio,this::openCalendar};
        for(int i=0;i<4;i++) {
            final int index=i;
            FrameLayout tile=new FrameLayout(host);
            tile.setBackground(glass(false,20));tile.setClipChildren(false);
            tile.setContentDescription(labels[i].replace('\n',' '));
            View icon=atlasIcon(i);
            FrameLayout.LayoutParams ip=new FrameLayout.LayoutParams(refX(112),refY(94),Gravity.TOP|Gravity.CENTER_HORIZONTAL);
            ip.topMargin=refY(12);tile.addView(icon,ip);
            TextView label=text(labels[i],16,true);label.setSingleLine(false);label.setMaxLines(2);
            FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(-1,refY(53),Gravity.BOTTOM);
            lp.leftMargin=refX(6);lp.rightMargin=refX(6);lp.bottomMargin=refY(7);tile.addView(label,lp);
            focusable(tile,()->{selectedShortcut=shortcuts.get(index);actions[index].run();});
            shortcuts.add(tile);put(home,tile,left[i],148,widths[i],164);
        }
        for(int i=0;i<4;i++) {
            View v=shortcuts.get(i);
            v.setNextFocusRightId(shortcuts.get((i+3)%4).getId());
            v.setNextFocusLeftId(shortcuts.get((i+1)%4).getId());
            v.setNextFocusUpId(close.getId());v.setNextFocusDownId(v.getId());
        }
        close.setNextFocusDownId(shortcuts.get(0).getId());
        close.setNextFocusLeftId(close.getId());close.setNextFocusRightId(close.getId());close.setNextFocusUpId(close.getId());

        FrameLayout calendar=new FrameLayout(host);calendar.setBackground(glass(false,21));rounded(calendar,21);
        android.widget.ImageView picture=new android.widget.ImageView(host);picture.setImageBitmap(calendarArt);
        picture.setScaleType(android.widget.ImageView.ScaleType.FIT_XY);picture.setAlpha(.85f);
        calendar.addView(picture,new FrameLayout.LayoutParams(-1,-1));
        View calendarShade=new View(host);
        calendarShade.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{0x001C293D,0x80202939}));
        calendar.addView(calendarShade,new FrameLayout.LayoutParams(-1,-1));
        TextView calendarTitle=text("التقويم الهجري  ▦",14,true);calendarTitle.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        put(calendar,calendarTitle,140,8,235,33);
        hijriWeekday=text("",14,true);hijriWeekday.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        put(calendar,hijriWeekday,150,55,225,30);
        date=text("",14,true);date.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        put(calendar,date,112,97,263,32);
        gregorian=text("",12,true);gregorian.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        put(calendar,gregorian,150,140,225,25);
        put(home,calendar,34,332,400,181);

        FrameLayout timesBox=new FrameLayout(host);timesBox.setBackground(glass(false,21));
        TextView timesTitle=text("مواقيت الصلاة • "+PrayerTimes.city(host)+"  🕌",15,true);
        timesTitle.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        put(timesBox,timesTitle,20,8,575,38);
        for(int i=0;i<5;i++) {
            LinearLayout tile=column();tile.setGravity(Gravity.CENTER);prayers[i]=tile;
            tile.setPadding(0,refY(7),0,refY(5));
            View symbol=new PrayerTimeIcon(host,i);
            tile.addView(symbol,new LinearLayout.LayoutParams(refX(38),0,1));
            TextView label=text(names[i],14,true);
            tile.addView(label,new LinearLayout.LayoutParams(-1,refY(31)));
            clocks[i]=text("—",16,true);clocks[i].setTextDirection(View.TEXT_DIRECTION_LTR);
            tile.addView(clocks[i],new LinearLayout.LayoutParams(-1,refY(33)));
            put(timesBox,tile,495-i*120,58,108,115);
        }
        put(home,timesBox,451,332,622,181);
        next=text("",9,false);next.setVisibility(View.GONE);home.addView(next);
        status=text("",8,false);put(home,status,50,518,1000,17);
        setContentView(surface);
        setOnDismissListener(d->{
            ui.removeCallbacksAndMessages(null);requestGeneration++;
            PrayerTimes.prefs(host).unregisterOnSharedPreferenceChangeListener(this);
            host.prayerGlassDialog=null;
            for(android.graphics.Bitmap bitmap:new android.graphics.Bitmap[]{panorama,iconAtlas,calendarArt})
                if(bitmap!=null&&!bitmap.isRecycled())bitmap.recycle();
            panorama=null;iconAtlas=null;calendarArt=null;
            if(returnFocus!=null&&returnFocus.isAttachedToWindow())returnFocus.requestFocus();
        });
        host.prayerGlassDialog=this;
    }

    private android.graphics.Bitmap loadArt(String path) {
        try(java.io.InputStream in=host.getAssets().open(path)){return android.graphics.BitmapFactory.decodeStream(in);}
        catch(java.io.IOException error){android.util.Log.w("Quswis-Prayer","Missing bundled artwork: "+path,error);return null;}
    }
    private void rounded(View view,float radius) {
        view.setOutlineProvider(new android.view.ViewOutlineProvider(){
            @Override public void getOutline(View v,android.graphics.Outline o){o.setRoundRect(0,0,v.getWidth(),v.getHeight(),px(radius));}
        });view.setClipToOutline(true);
    }
    private int refX(float n){return Math.round(n*host.screenW*.72f/1107f);}
    private int refY(float n){return Math.round(n*host.screenH*.622f/540f);}
    private void put(FrameLayout parent,View child,float x,float y,float w,float h) {
        FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(refX(w),refY(h),Gravity.TOP|Gravity.LEFT);
        lp.leftMargin=refX(x);lp.topMargin=refY(y);parent.addView(child,lp);
    }
    private View atlasIcon(final int index) {
        return new View(host){
            private final android.graphics.Paint paint=new android.graphics.Paint(3);
            @Override protected void onDraw(android.graphics.Canvas canvas){
                if(iconAtlas==null||iconAtlas.isRecycled())return;
                int w=iconAtlas.getWidth()/2,h=iconAtlas.getHeight()/2;
                android.graphics.Rect src=new android.graphics.Rect(index%2*w,index/2*h,(index%2+1)*w,(index/2+1)*h);
                float side=Math.min(getWidth(),getHeight()),x=(getWidth()-side)/2f,y=(getHeight()-side)/2f;
                canvas.drawBitmap(iconAtlas,src,new android.graphics.RectF(x,y,x+side,y+side),paint);
            }
        };
    }

    @Override protected void onStart() {
        super.onStart();
        Window window = getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            // Match the reference: keep the left lenses and bottom dock visible.
            window.setGravity(Gravity.TOP | Gravity.RIGHT);
            window.setLayout(Math.round(host.screenW * .72f), Math.round(host.screenH * .622f));
            WindowManager.LayoutParams bounds = window.getAttributes();
            bounds.x = Math.round(host.screenW * .022f);
            bounds.y = Math.round(host.screenH * .122f);
            window.setAttributes(bounds);
            window.setWindowAnimations(0);
            window.getDecorView().setSystemUiVisibility(host.getWindow().getDecorView().getSystemUiVisibility());
        }
        surface.setScaleX(.9f); surface.setScaleY(.9f); surface.setAlpha(0f);
        surface.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(300)
                .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
        PrayerTimes.prefs(host).registerOnSharedPreferenceChangeListener(this);
        shortcuts.get(0).requestFocus();
        surface.post(() -> { if (!closing && !subpage) shortcuts.get(0).requestFocus(); });
        refreshAt = System.currentTimeMillis();
        PrayerTimes.refresh(host, null, false); pulse.run();
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            if (event.getAction() == KeyEvent.ACTION_UP) dismiss();
            return true;
        }
        if (closing) return true;
        int key = event.getKeyCode();
        if (!subpage && (key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT)) {
            int index = shortcuts.indexOf(getCurrentFocus());
            if (index >= 0) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    int target = PrayerFocusNavigation.horizontal(index, key == KeyEvent.KEYCODE_DPAD_RIGHT, shortcuts.size());
                    shortcuts.get(target).requestFocus();
                }
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override public void dismiss() {
        if (closing) return;
        closing = true;
        surface.animate().cancel();
        surface.animate().scaleX(.9f).scaleY(.9f).alpha(0f).setDuration(220)
                .withEndAction(() -> super.dismiss()).start();
    }

    void dispose() {
        closing = true; surface.animate().cancel(); super.dismiss();
    }

    @Override public void onSharedPreferenceChanged(SharedPreferences p, String key) { ui.post(this::render); }

    private int px(float units) { return Ui.px(host, units); }
    private LinearLayout column() {
        LinearLayout v = new LinearLayout(host); v.setOrientation(LinearLayout.VERTICAL);
        v.setLayoutDirection(View.LAYOUT_DIRECTION_RTL); return v;
    }
    private LinearLayout row() {
        LinearLayout v = new LinearLayout(host); v.setOrientation(LinearLayout.HORIZONTAL);
        v.setLayoutDirection(View.LAYOUT_DIRECTION_LTR); return v;
    }
    private TextView text(String value, float size, boolean bold) {
        TextView t = new TextView(host); t.setText(value); Ui.textSize(t, size);
        t.setTextColor(Color.WHITE); t.setGravity(Gravity.CENTER); t.setIncludeFontPadding(false);
        t.setSingleLine(true); t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD); return t;
    }
    private GradientDrawable glass(boolean focused, int radius) {
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                focused ? new int[]{0xDE3D83B8, 0xDB112C4B} : new int[]{0xAF59606B, 0xD0242730});
        bg.setCornerRadius(px(radius)); bg.setStroke(px(focused ? 1.5f : .7f), focused ? 0xFFE0F8FF : 0x88FFFFFF);
        return bg;
    }
    private void focusable(View v, Runnable action) {
        v.setId(View.generateViewId()); v.setFocusable(true); v.setFocusableInTouchMode(true); v.setClickable(true);
        v.setOnClickListener(w -> { if (!closing) action.run(); });
        v.setOnFocusChangeListener((w, focused) -> {
            w.setBackground(glass(focused, 20)); w.setElevation(px(focused ? 7 : 0));
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                w.setOutlineAmbientShadowColor(0xFF7FD6FF);
                w.setOutlineSpotShadowColor(0xFF7FD6FF);
            }
            w.animate().scaleX(focused ? 1.015f : 1f).scaleY(focused ? 1.015f : 1f).setDuration(140).start();
        });
    }
    private TextView button(String value, float size, Runnable action) {
        TextView t = text(value, size, true); t.setBackground(glass(false, 15)); focusable(t, action); return t;
    }

    private String zone() { return PrayerCalendar.zone(host); }
    private android.icu.util.IslamicCalendar hijri(long at) {
        android.icu.util.IslamicCalendar c = new android.icu.util.IslamicCalendar(
                android.icu.util.TimeZone.getTimeZone(zone()), new Locale("ar", "SA"));
        c.setCalculationType(android.icu.util.IslamicCalendar.CalculationType.ISLAMIC_UMALQURA);
        c.setTimeInMillis(at); return c;
    }
    private String hijriText(JSONObject today, long now) {
        JSONObject h = today == null ? null : today.optJSONObject("hijri");
        if (h != null && h.optJSONObject("month") != null)
            return h.optString("day") + " " + h.optJSONObject("month").optString("ar") + " " + h.optString("year") + " هـ";
        android.icu.util.IslamicCalendar c = hijri(now);
        android.icu.text.SimpleDateFormat f = new android.icu.text.SimpleDateFormat("d MMMM y 'هـ'", Locale.forLanguageTag("ar-SA-u-ca-islamic-umalqura"));
        f.setCalendar(c); return f.format(new Date(now));
    }
    private void render() {
        if (closing) return;
        long now = System.currentTimeMillis();
        JSONObject days = PrayerCalendar.days(host);
        String todayKey = PrayerPlan.day(now, zone());
        JSONObject today = days.optJSONObject(todayKey);
        JSONObject times = today == null ? null : today.optJSONObject("timings");
        List<PrayerPlan.Event> events = PrayerPlan.events(days, now, true, false, new int[5]);
        PrayerPlan.Event upcoming = events.isEmpty() ? null : events.get(0);
        for (int i = 0; i < 5; i++) {
            clocks[i].setText(times == null ? "—" : displayPrayerTime(times.optString(PrayerPlan.KEYS[i])));
            prayers[i].setBackground(glass(upcoming != null && upcoming.day.equals(todayKey) && upcoming.index == i, 10));
        }
        java.text.SimpleDateFormat calendarDay = new java.text.SimpleDateFormat("EEEE", new Locale("ar"));
        calendarDay.setTimeZone(TimeZone.getTimeZone(zone()));
        hijriWeekday.setText(calendarDay.format(new Date(now)));
        date.setText(hijriText(today, now));
        next.setText(upcoming == null ? "بانتظار مواقيت اليوم" : "القادمة: " + names[upcoming.index] +
                (upcoming.day.equals(todayKey) ? "" : " • " + upcoming.day) + "\n" + PrayerPanelClock.countdown(upcoming.at - now));
        java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("d MMMM yyyy", new Locale("ar"));
        f.setTimeZone(TimeZone.getTimeZone(zone()));
        gregorian.setText(f.format(new Date(now)) + " م");
        status.setText(times == null ? "مواقيت اليوم غير متاحة؛ تحقق من الاتصال وإعدادات المدينة" : "");
        home.setContentDescription("الخدمات الإسلامية • " + next.getText());
        if (times == null && now - refreshAt > 60000L) {
            refreshAt = now; PrayerTimes.refresh(host, null, false);
        }
    }

    private String displayPrayerTime(String raw) {
        String clean=PrayerPanelClock.clean(raw);
        if(clean.equals("--:--"))return "—";
        int hour=Integer.parseInt(clean.substring(0,2));
        return String.format(Locale.US,"%d:%s %s",hour%12==0?12:hour%12,clean.substring(3),hour<12?"ص":"م");
    }

    private LinearLayout page(String title) {
        requestGeneration++; subpage = true; home.setVisibility(View.GONE);
        if (surface.getChildCount() > 1) surface.removeViewAt(1);
        LinearLayout page = column();
        page.setPadding(px(18), px(14), px(18), px(14));
        LinearLayout header = row();
        header.addView(text(title, 18, true), new LinearLayout.LayoutParams(0, px(34), 1));
        TextView back = button("رجوع", 12, this::showHome);
        header.addView(back, new LinearLayout.LayoutParams(px(62), px(30)));
        page.addView(header);
        surface.addView(page, new FrameLayout.LayoutParams(-1, -1)); back.requestFocus(); return page;
    }
    private void showHome() {
        requestGeneration++; subpage = false;
        if (surface.getChildCount() > 1) surface.removeViewAt(1);
        home.setVisibility(View.VISIBLE);
        (selectedShortcut == null ? shortcuts.get(0) : selectedShortcut).requestFocus();
    }
    private LinearLayout scrolling(LinearLayout page) {
        ScrollView scroll = new ScrollView(host); scroll.setFillViewport(true);
        LinearLayout list = column(); list.setPadding(px(4), px(4), px(4), px(4));
        scroll.addView(list); page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1)); return list;
    }
    private void item(LinearLayout list, String label, Runnable action) {
        TextView b = button(label, 14, action); b.setPadding(px(10), 0, px(10), 0);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, px(36)); p.bottomMargin = px(5); list.addView(b, p);
    }
    private void openRadio() {
        LinearLayout list = scrolling(page("إذاعات القرآن"));
        item(list, "إيقاف الإذاعة", () -> { QuranRadio.stop(); openRadio(); });
        for (String[] station : QuranRadio.stations(host)) {
            item(list, (station[1].equals(QuranRadio.current(host)[1]) && QuranRadio.state() != QuranRadio.IDLE ? "▶  " : "") + station[0],
                    () -> { QuranRadio.choose(host, station[1]); QuranRadio.play(host); openRadio(); });
        }
        item(list, "إعدادات الإذاعة والرابط المخصص", () -> {
            dismiss(); host.startActivity(new android.content.Intent(host, SettingsActivity.class).putExtra("page", "إذاعة القرآن"));
        });
        QuranRadio.refreshStations(host, null);
    }
    private void openDhikr() {
        LinearLayout list = scrolling(page("الأذكار والتسبيح"));
        String[] words = {"سبحان الله", "الحمد لله", "الله أكبر", "لا إله إلا الله وحده لا شريك له، له الملك وله الحمد وهو على كل شيء قدير",
                "سبحان الله وبحمده، سبحان الله العظيم", "أستغفر الله وأتوب إليه", "اللهم صل وسلم على نبينا محمد"};
        for (String word : words) {
            int[] count = {0}; TextView b = button(word + "  •  0", 14, () -> {});
            b.setSingleLine(false); b.setMaxLines(2);
            b.setOnClickListener(v -> b.setText(word + "  •  " + (++count[0])));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, px(45)); p.bottomMargin = px(5); list.addView(b, p);
        }
    }
    private void openCalendar() {
        LinearLayout page = page("التقويم الهجري • أم القرى");
        LinearLayout body = column(); page.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        android.icu.util.IslamicCalendar month = hijri(System.currentTimeMillis()); month.set(android.icu.util.Calendar.DAY_OF_MONTH, 1);
        LinearLayout nav = row();
        TextView label = text("", 14, true);
        TextView previous = button("‹", 20, () -> { month.add(android.icu.util.Calendar.MONTH, -1); drawMonth(body, label, month); });
        TextView following = button("›", 20, () -> { month.add(android.icu.util.Calendar.MONTH, 1); drawMonth(body, label, month); });
        nav.addView(previous, new LinearLayout.LayoutParams(px(45), px(30)));
        nav.addView(label, new LinearLayout.LayoutParams(0, px(30), 1));
        nav.addView(following, new LinearLayout.LayoutParams(px(45), px(30))); page.addView(nav);
        drawMonth(body, label, month);
    }
    private void drawMonth(LinearLayout body, TextView label, android.icu.util.IslamicCalendar month) {
        body.removeAllViews();
        android.icu.text.SimpleDateFormat fmt = new android.icu.text.SimpleDateFormat("MMMM y 'هـ'", Locale.forLanguageTag("ar-SA-u-ca-islamic-umalqura"));
        fmt.setCalendar(month); label.setText(fmt.format(month.getTime()));
        LinearLayout weekdays = row();
        for (String name : new String[]{"الأحد", "الإثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت"})
            weekdays.addView(text(name, 10, true), new LinearLayout.LayoutParams(0, px(21), 1));
        body.addView(weekdays);
        int offset = month.get(android.icu.util.Calendar.DAY_OF_WEEK) - 1;
        int length = month.getActualMaximum(android.icu.util.Calendar.DAY_OF_MONTH);
        android.icu.util.IslamicCalendar today = hijri(System.currentTimeMillis());
        for (int week = 0; week < 6; week++) {
            LinearLayout row = row();
            for (int day = 0; day < 7; day++) {
                int n = week * 7 + day - offset + 1;
                TextView value = text(n > 0 && n <= length ? String.valueOf(n) : "", 12, false);
                if (n == today.get(android.icu.util.Calendar.DAY_OF_MONTH) && month.get(android.icu.util.Calendar.MONTH) == today.get(android.icu.util.Calendar.MONTH)
                        && month.get(android.icu.util.Calendar.YEAR) == today.get(android.icu.util.Calendar.YEAR)) value.setBackground(glass(true, 7));
                row.addView(value, new LinearLayout.LayoutParams(0, -1, 1));
            }
            body.addView(row, new LinearLayout.LayoutParams(-1, 0, 1));
        }
    }

    private void openQuran() {
        LinearLayout list = scrolling(page("القرآن الكريم • اختر السورة"));
        TextView loading = text("جارٍ تحميل السور…", 14, false); list.addView(loading);
        fetch("https://api.alquran.cloud/v1/surah", response -> {
            JSONArray chapters = response.getJSONArray("data"); list.removeAllViews();
            for (int i = 0; i < chapters.length(); i++) {
                JSONObject chapter = chapters.getJSONObject(i); int number = chapter.getInt("number"); String name = chapter.getString("name");
                item(list, number + " • " + name, () -> readSurah(number, name));
            }
        }, loading);
    }
    private void readSurah(int number, String name) {
        LinearLayout page = page(name);
        ScrollView scroll = new ScrollView(host); scroll.setFocusable(true);
        TextView verses = text("جارٍ تحميل الآيات…", 19, false);
        verses.setSingleLine(false); verses.setMaxLines(Integer.MAX_VALUE); verses.setEllipsize(null);
        verses.setTextDirection(View.TEXT_DIRECTION_RTL); verses.setPadding(px(12), px(8), px(12), px(12));
        verses.setLineSpacing(px(7), 1f); scroll.addView(verses);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1)); scroll.requestFocus();
        item(page, "اختيار سورة أخرى", this::openQuran);
        fetch("https://api.alquran.cloud/v1/surah/" + number, response -> {
            JSONArray ayahs = response.getJSONObject("data").getJSONArray("ayahs");
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < ayahs.length(); i++) out.append(ayahs.getJSONObject(i).getString("text"))
                    .append(" ﴿").append(ayahs.getJSONObject(i).getInt("numberInSurah")).append("﴾  ");
            verses.setText(out.toString());
        }, verses);
    }
    private interface Response { void accept(JSONObject value) throws Exception; }
    private void fetch(String address, Response callback, TextView message) {
        final int generation = requestGeneration;
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(address).openConnection();
                connection.setConnectTimeout(10000); connection.setReadTimeout(12000);
                if (connection.getResponseCode() != 200) throw new java.io.IOException("HTTP " + connection.getResponseCode());
                StringBuilder text = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"))) {
                    char[] buffer = new char[4096]; int count;
                    while ((count = reader.read(buffer)) != -1) {
                        text.append(buffer, 0, count); if (text.length() > 2000000) throw new java.io.IOException("Response too large");
                    }
                }
                JSONObject response = new JSONObject(text.toString());
                ui.post(() -> {
                    if (closing || !isShowing() || generation != requestGeneration) return;
                    try { callback.accept(response); } catch (Exception e) { message.setText("تعذّر قراءة المحتوى. ارجع وحاول مجددًا."); }
                });
            } catch (Exception e) {
                ui.post(() -> { if (!closing && isShowing() && generation == requestGeneration)
                    message.setText("تعذّر التحميل. تحقق من الإنترنت ثم ارجع وحاول مجددًا."); });
            } finally { if (connection != null) connection.disconnect(); }
        }, "Quswis-Quran").start();
    }
}

