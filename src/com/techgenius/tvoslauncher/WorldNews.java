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

        Item(String title, String source) {
            this.title = title;
            this.source = source;
        }
    }

    private static final String[][] FEEDS = {
            {"BBC عربي", "https://feeds.bbci.co.uk/arabic/rss.xml"},
            {"فرانس 24", "https://www.france24.com/ar/rss"}};
    private static final long TTL_MS = 10 * 60 * 1000L;
    private static List<Item> cache;
    private static long cachedAt;

    private WorldNews() {
    }

    static synchronized List<Item> cached() {
        return cache != null && System.currentTimeMillis() - cachedAt < TTL_MS ? cache : null;
    }

    /** Blocking; call off the main thread. Returns an empty list when no feed answers. */
    static synchronized List<Item> fetch(int max) {
        List<Item> hit = cached();
        if (hit != null) return hit;
        List<Item> out = new ArrayList<>();
        for (String[] feed : FEEDS) {
            try {
                out.addAll(read(feed[0], feed[1], max));
            } catch (Exception ignored) {
                // try the next feed
            }
            if (out.size() >= max) break;
        }
        if (out.size() > max) out = new ArrayList<>(out.subList(0, max));
        if (!out.isEmpty()) {
            cache = out;
            cachedAt = System.currentTimeMillis();
        }
        return out;
    }

    private static List<Item> read(String source, String url, int max) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(6000);
        conn.setReadTimeout(6000);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0");
        try (InputStream in = conn.getInputStream()) {
            XmlPullParser p = XmlPullParserFactory.newInstance().newPullParser();
            p.setInput(in, null);
            List<Item> items = new ArrayList<>();
            boolean inItem = false;
            for (int e = p.getEventType(); e != XmlPullParser.END_DOCUMENT && items.size() < max; e = p.next()) {
                if (e == XmlPullParser.START_TAG) {
                    String n = p.getName();
                    if ("item".equals(n)) inItem = true;
                    else if (inItem && "title".equals(n)) {
                        String t = p.nextText().trim();
                        if (!t.isEmpty()) items.add(new Item(t, source));
                    }
                } else if (e == XmlPullParser.END_TAG && "item".equals(p.getName())) {
                    inItem = false;
                }
            }
            return items;
        } finally {
            conn.disconnect();
        }
    }
}
