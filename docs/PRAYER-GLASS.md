# Prayer glass window

Stable rollback source: `backup/stable-1.0.205` at
`4265ac5569815e9f302fbdc35e356686a6176333`. The feature branch is based on this
exact commit. No merge into main or automatic APK release is part of this change.
Version name/code remain 1.0.205 / 205 and AppUpdater.UPDATES_FROZEN stays true.

The native Dialog opens only from the existing prayer shortcut. It occupies
62.5% of the display width and 1050/2160 of its height (2400×1050 at 4K,
1200×525 at 1080p). Existing LiquidGlass paints the window. It has no full-screen
shade or wallpaper replacement. Opening scales 0.9→1 in 300 ms; dismissal takes
220 ms and returns focus to the prayer shortcut. BACK closes the entire window;
the on-screen Back button on each utility returns to its main panel.

Prayer cards read only location-matched, date-bound PrayerCalendar data.
Unavailable current-day data is explicitly shown as unavailable, with the existing
sync service refreshing it. The next event uses PrayerPlan, including the actual
next day's calendar after Isha. Scheduling, adhan, iqama, settings and all Home
widgets retain their existing code. Hijri dates use the daily AlAdhan metadata,
with Android ICU Umm al-Qura in the city's timezone as the offline fallback.
The browsable monthly calendar is explicitly labelled Umm al-Qura.

Utilities: Quran chapter selection and verse reading via api.alquran.cloud;
local dhikr counters; playback, stop and station selection using the existing
QuranRadio; a month calendar with previous/next navigation. Quran reading needs
Internet. Cached/fallback radio stations use the existing player. Network results
are ignored after changing pages or closing the window.

Validation: Actions compiles the complete APK against Android API 34, runs the
existing prayer clock and calendar regressions and verifies the APK signature.
Artifact builds use the existing workflow's development signing fallback when no
release keystore is configured. Such a build cannot replace an installed APK
signed with a different key. No signing credentials are changed by this feature.

Device acceptance still needs MECOOL KM2 Plus Deluxe / Android TV 14: open the
prayer lens with OK; verify all four shortcuts and DPAD focus; scroll a long
surah; change radio station and stop playback; browse Hijri months; BACK restores
the prayer lens; verify five times against the configured city, including offline
and midnight rollover; confirm the existing adhan and iqama continue normally.
