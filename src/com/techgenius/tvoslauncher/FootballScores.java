package com.techgenius.tvoslauncher;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/** Live and upcoming scores for Saudi, English, Spanish, Italian, French and AFC Elite leagues. */
final class FootballScores {
    private static final String PREFS = "football_scores";
    private static final String K_DATA = "data", K_AT = "at";
    private static final long CACHE_MS = 15L * 60L * 1000L;
    private static volatile boolean busy;

    private static final String[][] LEAGUES = {
        {"ksa.1", "دوري روشن السعودي"},
        {"eng.1", "الدوري الإنجليزي"},
        {"esp.1", "الدوري الإسباني"},
        {"ita.1", "الدوري الإيطالي"},
        {"fra.1", "الدوري الفرنسي"},
        {"afc.champions", "دوري أبطال آسيا للنخبة"}
    };

    private static final class Match {
        String date, league, home, away, homeScore, awayScore, status;
    }

    private FootballScores() {}

    static TextView createView(Context c) {
        TextView v = new TextView(c);
        v.setTextColor(Color.WHITE);
        v.setTextSize(13);
        v.setGravity(Gravity.CENTER_VERTICAL);
        v.setPadding(Ui.px(c, 16), Ui.px(c, 10), Ui.px(c, 16), Ui.px(c, 10));
        v.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        v.setMaxLines(6);
        v.setLineSpacing(Ui.px(c, 2), 1f);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xE51B2633);
        bg.setCornerRadius(Ui.px(c, 15));
        bg.setStroke(Ui.px(c, 1), 0x667DA7D8);
        v.setBackground(bg);
        v.setFocusable(false);
        v.setClickable(false);
        return v;
    }

    static void refresh(Context context, TextView view, boolean force) {
        if (view == null) return;
        Context app = context.getApplicationContext();
        android.content.SharedPreferences prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String cached = prefs.getString(K_DATA, null);
        long at = prefs.getLong(K_AT, 0L);
        if (cached != null) view.setText(cached);
        else view.setText("مباريات الدوريات • جارٍ تحميل النتائج والمواعيد…");
        if (busy || (!force && System.currentTimeMillis() - at < CACHE_MS)) return;

        busy = true;
        new Thread(() -> {
            String result = null;
            int successfulFeeds = 0;
            List<Match> matches = new ArrayList<>();
            HttpURLConnection conn = null;
            try {
                SimpleDateFormat day = new SimpleDateFormat("yyyyMMdd", Locale.US);
                day.setTimeZone(TimeZone.getTimeZone("Asia/Riyadh"));
                String from = day.format(new Date());
                String until = day.format(new Date(System.currentTimeMillis() + 7L * 24L * 60L * 60L * 1000L));

                for (String[] league : LEAGUES) {
                    try {
                        String endpoint = "https://site.api.espn.com/apis/site/v2/sports/soccer/"
                                + league[0] + "/scoreboard?dates=" + from + "-" + until;
                        conn = (HttpURLConnection) new URL(endpoint).openConnection();
                        conn.setConnectTimeout(7000);
                        conn.setReadTimeout(7000);
                        conn.setRequestProperty("User-Agent", Weather.UA);
                        int code = conn.getResponseCode();
                        if (code < 200 || code >= 300) continue;

                        BufferedReader reader = new BufferedReader(
                                new InputStreamReader(conn.getInputStream(), "UTF-8"));
                        StringBuilder json = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) json.append(line);
                        reader.close();

                        JSONObject root = new JSONObject(json.toString());
                        JSONArray events = root.optJSONArray("events");
                        successfulFeeds++;
                        if (events == null) continue;

                        for (int i = 0; i < events.length(); i++) {
                            JSONObject event = events.optJSONObject(i);
                            if (event == null) continue;
                            JSONArray competitions = event.optJSONArray("competitions");
                            if (competitions == null || competitions.length() == 0) continue;
                            JSONObject competition = competitions.optJSONObject(0);
                            if (competition == null) continue;
                            JSONArray competitors = competition.optJSONArray("competitors");
                            if (competitors == null || competitors.length() < 2) continue;

                            JSONObject first = competitors.optJSONObject(0);
                            JSONObject second = competitors.optJSONObject(1);
                            if (first == null || second == null) continue;
                            Match match = new Match();
                            match.date = event.optString("date", "");
                            match.league = league[1];
                            match.home = teamName(first, "الفريق الأول");
                            match.away = teamName(second, "الفريق الثاني");
                            match.homeScore = first.optString("score", "-");
                            match.awayScore = second.optString("score", "-");
                            JSONObject status = event.optJSONObject("status");
                            JSONObject type = status == null ? null : status.optJSONObject("type");
                            match.status = type == null ? "" : type.optString("shortDetail", "");
                            matches.add(match);
                        }
                    } catch (Exception ignored) {
                    } finally {
                        if (conn != null) {
                            conn.disconnect();
                            conn = null;
                        }
                    }
                }

                Collections.sort(matches, Comparator.comparing(m -> m.date == null ? "" : m.date));
                StringBuilder out = new StringBuilder("مباريات كرة القدم • تحديث تلقائي");
                int count = Math.min(5, matches.size());
                for (int i = 0; i < count; i++) {
                    Match m = matches.get(i);
                    out.append("\n").append(m.league).append("  |  ")
                       .append(m.home).append("  ")
                       .append(m.homeScore).append(" - ").append(m.awayScore).append("  ")
                       .append(m.away);
                    if (!m.status.isEmpty()) out.append("  •  ").append(m.status);
                    else if (m.date != null && m.date.length() >= 16) {
                        out.append("  •  ").append(formatDate(m.date));
                    }
                }
                if (count == 0 && successfulFeeds > 0) {
                    out.append("\nلا توجد مباريات مجدولة خلال الأيام السبعة القادمة");
                } else if (count == 0) {
                    out = new StringBuilder("تعذر جلب المباريات الآن • تحقق من اتصال الإنترنت");
                }
                result = out.toString();
                if (successfulFeeds > 0) {
                    prefs.edit().putString(K_DATA, result).putLong(K_AT, System.currentTimeMillis()).apply();
                }
            } catch (Exception ignored) {
            } finally {
                if (conn != null) conn.disconnect();
            }

            final String display = result != null ? result
                    : prefs.getString(K_DATA, "تعذر جلب المباريات الآن • تحقق من اتصال الإنترنت");
            new Handler(Looper.getMainLooper()).post(() -> {
                busy = false;
                if (view != null) view.setText(display);
            });
        }, "football-score-refresh").start();
    }

    private static String teamName(JSONObject competitor, String fallback) {
        JSONObject team = competitor.optJSONObject("team");
        return team == null ? fallback : team.optString("displayName", fallback);
    }

    private static String formatDate(String raw) {
        try {
            SimpleDateFormat input = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'", Locale.US);
            input.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date date = input.parse(raw.substring(0, 16) + "Z");
            SimpleDateFormat output = new SimpleDateFormat("EEE h:mm a", new Locale("ar"));
            output.setTimeZone(TimeZone.getTimeZone("Asia/Riyadh"));
            return output.format(date);
        } catch (Exception e) {
            return "";
        }
    }
}
