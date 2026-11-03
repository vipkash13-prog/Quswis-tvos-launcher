package com.techgenius.tvoslauncher;

/** Assert that focus growth, six classic columns and twelve scene tiles fit separate regions. */
public final class HomeGeometryTest {
    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    static int px(float value, float unit) { return Math.round(value * unit); }
    public static void main(String[] args) {
        int[][] displays = {{1280,720}, {1920,1080}, {3840,2160}, {1024,768}, {2560,1080}, {1366,768}};
        for (int[] display : displays) {
            int width = display[0], height = display[1];
            float unit = Math.min(width / 960f, height / 540f);
            HomeGeometry g = new HomeGeometry(width, unit);
            check(g.railLeft + g.railWidth * 1.05f < g.contentLeft, "Focused widget crosses app viewport at " + width);
            check(g.contentLeft + g.contentWidth == width, "App viewport exceeds screen");
            int tile = g.gridTileWidth(px(45, unit), px(25, unit), 6);
            check(tile > 0, "Classic app tile has no space");
            int gridWidth = 6 * tile + 5 * px(25, unit) + 2 * px(45, unit);
            check(gridWidth <= g.contentWidth, "Classic grid exceeds app viewport");
            int scene = g.sceneTileSize(px(13, unit), px(10, unit), 12, px(54, unit), px(40, unit));
            check(scene > 0, "Scene app tile has no space");
            int dockWidth = 12 * scene + 11 * px(10, unit) + 2 * px(13, unit);
            check(dockWidth <= g.contentWidth - 2 * px(40, unit), "Twelve scene apps exceed viewport");
            int watch = g.watchCardWidth(px(40, unit), px(12, unit), 3);
            check(3 * watch + 2 * px(12, unit) <= g.contentWidth - 2 * px(40, unit), "Keep-watching cards overflow");
            // Header + 3 watch cards finish before the regular single-row dock begins.
            int watchBottom = px(312 + 11.5f + 7 + 64, unit);
            int dockTop = height - px(14 + 24 + 20, unit) - scene;
            check(watchBottom < dockTop, "Scene cards overlap regular app dock");
            check(px(24 + 4 * 64 + 52, unit) < height, "Five widgets exceed the rail height");
        }
        System.out.println("Home geometry checks passed for 720p, 1080p, 4K, 4:3 and ultrawide");
    }
}
