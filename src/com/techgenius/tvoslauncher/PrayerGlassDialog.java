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
    private android.graphics.Bitmap panorama;
    private final LinearLayout home;
    private final TextView date, status, next;
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
        surface = new FrameLayout(host);
        MainActivity.LiquidGlass backdrop = new MainActivity.LiquidGlass(px(24), 0x85203246,
                host.glassSource, host.screenW, host.screenH, Ui.unit(host)).adaptive().darker(0x75);
        backdrop.lens = px(5);
        surface.setBackground(backdrop);
        surface.setPadding(px(16), px(10), px(16), px(10));
        surface.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View view, android.graphics.Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), px(24));
            }
        });
        surface.setClipToOutline(true);
        try (java.io.InputStream input = host.getAssets().open("prayer-services-panorama.png")) {
            panorama = android.graphics.BitmapFactory.decodeStream(input);
        } catch (java.io.IOException ignored) { }

        home = column();
        surface.addView(home, new FrameLayout.LayoutParams(-1, -1));

        FrameLayout heading = new FrameLayout(host);
        heading.setClipChildren(false);
        home.setClipChildren(false);
        addArtwork(heading, px(128), .82f);

        TextView title = text("الخدمات الإسلامية", 23, true);
        title.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        title.setPadding(0, px(5), 0, 0);
        title.setShadowLayer(px(3), 0, px(1), 0xDD000000);
        heading.addView(title, new FrameLayout.LayoutParams(-1, -1));
        TextView close = button("×", 22, this::dismiss);
        heading.addView(close, new FrameLayout.LayoutParams(px(28), px(28), Gravity.LEFT | Gravity.TOP));
        home.addView(heading, new LinearLayout.LayoutParams(-1, px(76)));

        LinearLayout row = row();
        String[] labels = {"القرآن الكريم", "الأذكار والتسبيح", "إذاعات القرآن", "التقويم الهجري"};
        Runnable[] actions = {this::openQuran, this::openDhikr, this::openRadio, this::openCalendar};
        for (int i = 0; i < 4; i++) {
            final int index = i;
            LinearLayout tile = column(); tile.setGravity(Gravity.CENTER);
            tile.setBackground(glass(false, 15));
            tile.setContentDescription(labels[i]);
            PrayerServiceIcon icon = new PrayerServiceIcon(host, i);
            LinearLayout.LayoutParams iconSize = new LinearLayout.LayoutParams(px(56), 0, 1);
            iconSize.topMargin = px(5);
            tile.addView(icon, iconSize);
            TextView label = text(labels[i], 15, true);
            LinearLayout.LayoutParams labelSize = new LinearLayout.LayoutParams(-1, px(24));
            labelSize.bottomMargin = px(5); tile.addView(label, labelSize);
            focusable(tile, () -> { selectedShortcut = shortcuts.get(index); actions[index].run(); });
            shortcuts.add(tile);
            LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(0, -1, 1);
            size.setMargins(px(5), px(3), px(5), px(7)); row.addView(tile, size);
        }
        home.addView(row, new LinearLayout.LayoutParams(-1, 0, 1f));
        // Explicit links keep DPAD inside the modal, including on firmware with unusual focus search.
        for (int i = 0; i < 4; i++) {
            View v = shortcuts.get(i);
            v.setNextFocusRightId(shortcuts.get((i + 3) % 4).getId());
            v.setNextFocusLeftId(shortcuts.get((i + 1) % 4).getId());
            v.setNextFocusUpId(close.getId()); v.setNextFocusDownId(v.getId());
        }
        close.setNextFocusDownId(shortcuts.get(0).getId());
        close.setNextFocusLeftId(close.getId()); close.setNextFocusRightId(close.getId());
        close.setNextFocusUpId(close.getId());

        LinearLayout lower = row();
        LinearLayout timesBox = column(); timesBox.setBackground(glass(false, 16));
        TextView timesTitle = text("🕌  مواقيت الصلاة • " + PrayerTimes.city(host), 14, true);
        timesBox.addView(timesTitle, new LinearLayout.LayoutParams(-1, px(29)));
        LinearLayout timesRow = row();
        for (int i = 0; i < 5; i++) {
            LinearLayout tile = column(); tile.setGravity(Gravity.CENTER); prayers[i] = tile;
            tile.addView(text(symbols[i], 16, false), new LinearLayout.LayoutParams(-1, 0, 1));
            tile.addView(text(names[i], 13, true), new LinearLayout.LayoutParams(-1, px(19)));
            clocks[i] = text("—", 17, true); clocks[i].setTextDirection(View.TEXT_DIRECTION_LTR);
            tile.addView(clocks[i], new LinearLayout.LayoutParams(-1, px(24)));
            LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(0, -1, 1);
            size.setMargins(px(3), 0, px(3), px(5)); timesRow.addView(tile, size);
        }
        timesBox.addView(timesRow, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout.LayoutParams timesSize = new LinearLayout.LayoutParams(0, -1, 1.55f);
        timesSize.setMargins(px(4), px(4), px(4), 0); lower.addView(timesBox, timesSize);
        LinearLayout calendar = column(); calendar.setGravity(Gravity.CENTER);
        calendar.setPadding(px(5), 0, px(5), 0);
        FrameLayout calendarCard = new FrameLayout(host);
        calendarCard.setBackground(glass(false, 16));
        calendarCard.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View v, android.graphics.Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), px(16));
            }
        });
        calendarCard.setClipToOutline(true);
        addArtwork(calendarCard, -1, .40f);
        calendarCard.addView(calendar, new FrameLayout.LayoutParams(-1, -1));
        calendar.addView(text("☾  التقويم الهجري", 14, true), new LinearLayout.LayoutParams(-1, px(24)));
        date = text("", 14, true); date.setSingleLine(false); date.setMaxLines(2);
        calendar.addView(date, new LinearLayout.LayoutParams(-1, 0, 1));
        next = text("", 10, false); next.setSingleLine(false); next.setMaxLines(2);
        calendar.addView(next, new LinearLayout.LayoutParams(-1, px(30)));
        LinearLayout.LayoutParams calSize = new LinearLayout.LayoutParams(0, -1, 1);
        calSize.setMargins(px(4), px(4), px(4), 0); lower.addView(calendarCard, calSize);
        home.addView(lower, new LinearLayout.LayoutParams(-1, 0, 1.1f));
        status = text("", 9, false);
        home.addView(status, new LinearLayout.LayoutParams(-1, px(18)));
        setContentView(surface);
        setOnDismissListener(d -> {
            ui.removeCallbacksAndMessages(null); requestGeneration++;
            PrayerTimes.prefs(host).unregisterOnSharedPreferenceChangeListener(this);
            host.prayerGlassDialog = null;
            if (panorama != null) { panorama.recycle(); panorama = null; }
            if (returnFocus != null && returnFocus.isAttachedToWindow()) returnFocus.requestFocus();
        });
        host.prayerGlassDialog = this;
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
        v.setLayoutDirection(View.LAYOUT_DIRECTION_RTL); return v;
    }
    private TextView text(String value, float size, boolean bold) {
        TextView t = new TextView(host); t.setText(value); Ui.textSize(t, size);
        t.setTextColor(Color.WHITE); t.setGravity(Gravity.CENTER); t.setIncludeFontPadding(false);
        t.setSingleLine(true); t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD); return t;
    }
    private GradientDrawable glass(boolean focused, int radius) {
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                focused ? new int[]{0xC057A9E0, 0xB51A416B} : new int[]{0x925A6675, 0xA5222936});
        bg.setCornerRadius(px(radius)); bg.setStroke(px(focused ? 1.5f : .7f), focused ? 0xFFE0F8FF : 0x70FFFFFF);
        return bg;
    }
    private void focusable(View v, Runnable action) {
        v.setId(View.generateViewId()); v.setFocusable(true); v.setFocusableInTouchMode(true); v.setClickable(true);
        v.setOnClickListener(w -> { if (!closing) action.run(); });
        v.setOnFocusChangeListener((w, focused) -> {
            w.setBackground(glass(focused, 15)); w.setElevation(px(focused ? 7 : 0));
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                w.setOutlineAmbientShadowColor(0xFF7FD6FF);
                w.setOutlineSpotShadowColor(0xFF7FD6FF);
            }
            w.animate().scaleX(focused ? 1.025f : 1f).scaleY(focused ? 1.025f : 1f).setDuration(140).start();
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
        android.icu.text.SimpleDateFormat f = new android.icu.text.SimpleDateFormat("d MMMM y 'هـ'", new Locale("ar", "SA"));
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
            clocks[i].setText(times == null ? "—" : PrayerPanelClock.clean(times.optString(PrayerPlan.KEYS[i])));
            prayers[i].setBackground(glass(upcoming != null && upcoming.day.equals(todayKey) && upcoming.index == i, 10));
        }
        java.text.SimpleDateFormat calendarDay = new java.text.SimpleDateFormat("EEEE", new Locale("ar"));
        calendarDay.setTimeZone(TimeZone.getTimeZone(zone()));
        date.setText(calendarDay.format(new Date(now)) + "\n" + hijriText(today, now));
        next.setText(upcoming == null ? "بانتظار مواقيت اليوم" : "القادمة: " + names[upcoming.index] +
                (upcoming.day.equals(todayKey) ? "" : " • " + upcoming.day) + "\n" + PrayerPanelClock.countdown(upcoming.at - now));
        java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("EEEE، d MMMM yyyy", new Locale("ar"));
        f.setTimeZone(TimeZone.getTimeZone(zone()));
        status.setText(times == null ? "مواقيت اليوم غير متاحة؛ تحقق من الاتصال وإعدادات المدينة" :
                f.format(new Date(now)) + " • " + PrayerTimes.city(host) + " • الأذان " +
                (PrayerTimes.prefs(host).getBoolean("adhan_enabled", false) ? "مفعّل" : "متوقف"));
        if (times == null && now - refreshAt > 60000L) {
            refreshAt = now; PrayerTimes.refresh(host, null, false);
        }
    }

    private void addArtwork(FrameLayout container, int height, float alpha) {
        if (panorama == null) return;
        android.widget.ImageView image = new android.widget.ImageView(host);
        image.setImageBitmap(panorama);
        image.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        image.setAlpha(alpha);
        container.addView(image, new FrameLayout.LayoutParams(-1, height, Gravity.TOP));
        View fade = new View(host);
        fade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x15202B40, 0x08202B40, 0xFF202B3A}));
        container.addView(fade, new FrameLayout.LayoutParams(-1, height, Gravity.TOP));
    }

    private LinearLayout page(String title) {
        requestGeneration++; subpage = true; home.setVisibility(View.GONE);
        if (surface.getChildCount() > 1) surface.removeViewAt(1);
        LinearLayout page = column();
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
        android.icu.text.SimpleDateFormat fmt = new android.icu.text.SimpleDateFormat("MMMM y 'هـ'", new Locale("ar", "SA"));
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
