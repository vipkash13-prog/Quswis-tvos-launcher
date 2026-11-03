# Prayer glass window — revision 2

Stable rollback: backup/stable-1.0.205 at 4265ac5569815e9f302fbdc35e356686a6176333;
stable APK: https://github.com/vipkash13-prog/Quswis-tvos-launcher/releases/download/approved-v1.0.217/Quswis-tvOS-Launcher.apk.

Revision 2 follows the updated screenshot proportions: the popup occupies 72% of
screen width and 62.2% of height, inset 2.2% from the right and 12.2% from the top.
This keeps the side lenses and bottom app dock visible. These proportions replace
the first revision's smaller centered 62.5% × 48.6% popup per the user's feedback.

The native controls remain interactive. Bundled generated decorative panorama
artwork appears only inside the Islamic-services popup; it never replaces the Home
wallpaper. Scalable Canvas icons avoid device-specific emoji rendering. The glass
window opens from 90% to 100% over 300 ms and closes over 220 ms.

Physical LEFT/RIGHT on the four RTL shortcuts now explicitly selects a neighboring
shortcut and wraps at both ends. Key-up is consumed so firmware focus search cannot
apply a second move. The close button remains accessible with UP/DOWN. BACK closes
the dialog and restores focus to the prayer lens.

Quran and radio are available inside Islamic Services. The duplicate Home Quran
lens is no longer created; its old visibility paths cannot re-enable it. Existing
QuranRadio settings/player, adhan, iqama, prayer scheduling and Home artwork remain
unchanged.

Prayer data still comes from location-matched PrayerCalendar days, with no stale
current-day times or fabricated next-day Fajr. Daily Hijri metadata is preferred;
Android ICU Umm al-Qura in the city timezone is the offline fallback. The monthly
calendar explicitly uses Umm al-Qura.

Actions builds API-34 APKs, verifies their signatures, runs existing prayer calendar
and clock regressions, and tests that every shortcut is reachable from every start
in either direction. Revision 2 publishes a separate prerelease APK under
prayer-glass-1.0.205-r2. Version 1.0.205/205 and frozen auto-updates are retained.
The workflow uses the existing development-signing fallback. An APK signed with a
different key cannot replace an existing installation signed with another key.

Physical MECOOL / Android TV 14 acceptance still requires testing. In particular:
test LEFT and RIGHT through all shortcuts, open each service, verify BACK focus,
check offline and midnight prayer data, and confirm unchanged adhan/iqama behavior.
