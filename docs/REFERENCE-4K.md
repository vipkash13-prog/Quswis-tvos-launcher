# Supplied reference implementation

Source: user-provided `Quswis_Islamic_Window_Reference_Assets.zip`, including its
1107×540 popup reference and individual design crops. The README correctly notes
that the crops contain baked-in labels and example times. They are reference
material, not usable live prayer cards.

The popup now uses those exact reference coordinates, scaled to its existing
72% screen width / 62.2% height window. Four live native shortcuts sit at the
reference positions; the illustrated Hijri card and five live prayer cards use
the reference bounds. The Kaaba/minaret header, mosque calendar artwork and
four transparent dimensional icons were reconstructed from the supplied crops
with text and sample dates removed. The generated header has unused bottom
canvas padding; the image view excludes it from the sampled source rectangle.

Prayer symbols, all labels, borders, focus rims and panel geometry render at
native display resolution. Icon source quadrants exceed their displayed size
at 4K. The panoramic artwork is a reconstructed raster asset; enlarging it does
not create photographic source detail absent from the reference crops.

No changes to MainActivity, wallpaper, original Home lenses, application dock,
prayer scheduler, adhan/iqama services or updater are included in this revision.
The earlier user-requested removal of the duplicate Home Quran lens is retained.
The stable 1.0.205 backup and existing release assets remain available.

Times are read from the existing location-matched dated calendar and displayed
in 12-hour format with ص/م. Hijri metadata and the existing Umm al-Qura fallback
remain live; there are no example prayer times or dates baked into any card.
Missing current-day data displays an explicit refresh message.

Actions builds and verifies the APK, runs existing prayer regressions and DPAD
tests, then tests the native dialog on Android 14 at 1920×1080 and 3840×2160.
The direct prerelease includes the APK plus both actual emulator screenshots.
Physical MECOOL KM2 Plus Deluxe testing is still a separate device check.
