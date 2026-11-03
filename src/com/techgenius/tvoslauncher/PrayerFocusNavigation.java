package com.techgenius.tvoslauncher;

/** Physical DPAD directions for the RTL shortcut strip, wrapping at either end. */
final class PrayerFocusNavigation {
    private PrayerFocusNavigation() { }
    static int horizontal(int current, boolean right, int count) {
        if (count <= 0) return -1;
        if (current < 0 || current >= count) return 0;
        return (current + (right ? count - 1 : 1)) % count;
    }
}
