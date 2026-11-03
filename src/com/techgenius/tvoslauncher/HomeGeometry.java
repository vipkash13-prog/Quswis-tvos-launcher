package com.techgenius.tvoslauncher;

/** Physical pixel bounds shared by the classic grid and Scenes; independent of layout direction. */
final class HomeGeometry {
    final int railLeft, railWidth, contentLeft, contentWidth;
    HomeGeometry(int width, float unit) {
        railLeft = Math.round(24 * unit);
        railWidth = Math.min(Math.round(214 * unit), width / 3);
        contentLeft = railLeft + railWidth + Math.round(24 * unit);
        contentWidth = width - contentLeft;
    }
    int gridTileWidth(int side, int gap, int columns) {
        return (contentWidth - 2 * side - (columns - 1) * gap) / columns;
    }
    int sceneTileSize(int padding, int gap, int columns, int preferred, int edge) {
        return Math.min(preferred, (contentWidth - 2 * edge - 2 * padding - (columns - 1) * gap) / columns);
    }
    int watchCardWidth(int edge, int gap, int count) {
        return (contentWidth - 2 * edge - (count - 1) * gap) / count;
    }
}
