package com.techgenius.tvoslauncher;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/** World headlines in Arabic from public RSS feeds (no key). Cached in memory for a few minutes. */
final class WorldNews {
    static final class Item {
        final String title, source;
        final long time;

        Item(String title, String source, long time) {
            this.title = title;
            this.source = source;
            this.time = time;
        }
    }

    /** Several newsrooms are read together and the newest stories win, so one slow feed cannot freeze the list. */
    private static final String[][] FEEDS = {
            {"BBC عربي", "https://feeds.bbci.co.uk/arabic/rss.xml"},
            {"فرانس 24", "https://www.france24.com/ar/rss"},
            {"الجزيرة", "https://www.aljazeera.net/aljazeerarss/a7c186be-1baa-4bd4-9d80-a84db769f779/73d0e1b4-532f-45ef-b7e0-8d5e5ab4a3a0"},
            {"سكاي نيوز عربية", "https://www.skynewsarabia.com/web/rss"}};
    private static final long TTL_MS = 2 * 60 * 1000L;
    private static List<Item> cache;
    private static long cachedAt;

    private WorldNews() {
    }

    static android.content.SharedPreferences prefs(android.content.Context c) {
        return c.getSharedPreferences("worldnews", android.content.Context.MODE_PRIVATE);
    }

    static boolean enabled(android.content.Context c) {
        return prefs(c).getBoolean("enabled", false);
    }

    static void setEnabled(android.content.Context c, boolean on) {
        prefs(c).edit().putBoolean("enabled", on).apply();
    }

    static synchronized List<Item> cached() {
        return cache != null && System.currentTimeMillis() - cachedAt < TTL_MS ? cache : null;
    }

    /** Blocking; call off the main thread. Returns an empty list when no feed answers. */
    static synchronized List<Item> fetch(int max) {
        List<Item> hit = cached();
        if (hit != null) return hit;
        List<Item> all = new ArrayList<>();
        for (String[] feed : FEEDS) {
            try {
                all.addAll(read(feed[0], feed[1], 12));
            } catch (Exception ignored) {
                // a feed that does not answer is skipped
            }
        }
        // Newest first; stories without a date go last. The same headline from two feeds counts once.
        java.util.Collections.sort(all, (x, y) -> Long.compare(y.time, x.time));
        List<Item> out = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (Item it : all) {
            if (seen.add(it.title) && out.size() < max) out.add(it);
        }
        if (!out.isEmpty()) {
            cache = out;
            cachedAt = System.currentTimeMillis();
        }
        return out;
    }

    /** "قبل ٥ دقائق" style age, with Latin digits. */
    static String age(long time) {
        if (time <= 0) return "";
        long min = Math.max(0, (System.currentTimeMillis() - time) / 60000L);
        if (min < 1) return "الآن";
        if (min < 60) return "قبل " + min + " د";
        long h = min / 60;
        if (h < 24) return "قبل " + h + " س";
        return "قبل " + (h / 24) + " يوم";
    }

    private static long parseDate(String raw) {
        if (raw == null) return 0;
        String[] patterns = {"EEE, dd MMM yyyy HH:mm:ss Z", "EEE, dd MMM yyyy HH:mm:ss z", "yyyy-MM-dd'T'HH:mm:ssZ"};
        for (String p : patterns) {
            try {
                java.util.Date d = new java.text.SimpleDateFormat(p, java.util.Locale.US).parse(raw.trim());
                if (d != null) return d.getTime();
            } catch (Exception ignored) {
                // try the next format
            }
        }
        return 0;
    }

    private static List<Item> read(String source, String url, int max) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        conn.setUseCaches(false);
        conn.setRequestProperty("Cache-Control", "no-cache");
        conn.setRequestProperty("User-Agent", "Mozilla/5.0");
        try (InputStream in = conn.getInputStream()) {
            XmlPullParser p = XmlPullParserFactory.newInstance().newPullParser();
            p.setInput(in, null);
            List<Item> items = new ArrayList<>();
            boolean inItem = false;
            String title = null, date = null;
            for (int e = p.getEventType(); e != XmlPullParser.END_DOCUMENT && items.size() < max; e = p.next()) {
                if (e == XmlPullParser.START_TAG) {
                    String n = p.getName();
                    if ("item".equals(n)) {
                        inItem = true;
                        title = null;
                        date = null;
                    } else if (inItem && "title".equals(n)) title = p.nextText().trim();
                    else if (inItem && "pubDate".equals(n)) date = p.nextText();
                } else if (e == XmlPullParser.END_TAG && "item".equals(p.getName())) {
                    inItem = false;
                    if (title != null && !title.isEmpty()) items.add(new Item(title, source, parseDate(date)));
                }
            }
            return items;
        } finally {
            conn.disconnect();
        }
    }
}
