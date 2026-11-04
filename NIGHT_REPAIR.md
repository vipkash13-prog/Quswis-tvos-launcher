# Night Scenes grid, remote response, and additive UHD wallpapers

Base: `49a791b8843e9e1aac435214dc27f797366c7a2b` (eight-section live TV preview). Independent branch `fix/night-grid-remote-4k`; no main merge. Version remains 1.0.205; updater remains frozen.

## Narrow visibility correction

The original visibility predicates allowed `Scenes.isScenesLook()` to bypass the expanded app-grid condition. Prayer visibility was independently restored on every resume. The new `nightAppsOpen()` checks **both** Night Scenes and the real `SceneHome.moreExpanded` state. All four lenses honor it, including resume after Settings. The existing expansion/Back path already synchronizes visibility, so no focus heuristics, input routes, animations, geometry, ordering, Classic rules, or lens functions need changing.

## Remote work found on the UI thread

- `Ui.px()` called `Ui.unit()` -> `Display.getRealSize()` for every coordinate, including icon drawing and focus effects. Unit scale is now cached per weak Context with configuration/metrics invalidation and a one-second fallback refresh for external output changes. Direct `Ui.screen()` still queries real display dimensions.
- `SceneView.onDraw()` built time/date/settings/file-timestamp strings for every invalidation and could decode a scene and bake its glass textures. Painting now uses a revision to reuse the identical cached scene. Clock/date/weather/settings changes advance that revision. Scene decoding and glass baking run on a single image worker; a scene rotation starts its existing 1600ms fade only after decoding. Lifecycle epochs discard obsolete worker results.
- Classic wallpaper changes could wait for a prefetch and decode/shade/blur on the UI thread. They now prepare on the existing IO executor, keep the previous image visible, and apply only the latest request.
- Settings created all scene thumbnails and its blurred backdrop synchronously while building pages. The same thumbnails/backdrop now load on a worker, with cached small thumbnails and weak view references.
- Navigation audio called the audio service inline before focus dispatch. The same system effects and volume now use a bounded worker queue; input does not wait for audio service IPC and stale clicks cannot accumulate.

No repeat throttling, deliberate input delay, focus-scale change, or animation-duration reduction was introduced. Instrumentation reports actual dispatch times and the old-query versus cached-scale cost; these emulator measurements are not a claim of measured MECOOL hardware performance. The target physical device still needs user testing.

## Eight additive wallpapers

Two night scenes (lake/coast), two classic landscapes (Alps/coast), two Saudi-inspired scenes (AlUla/Riyadh), two Moroccan-inspired scenes (Chefchaouen/Atlas). Original AI-generated artwork, not documentary location photographs. Each bundled WebP is **3840×2160**. The generator's 1672×941 source art was resampled for the UHD export; this is disclosed in `assets/backgrounds/uhd/artwork.json`, not described as native 4K photography.

All eight appear in Appearance and scenes and Classic background choices. Original scene/photo entries and their order remain. Selection uses the existing persisted scene IDs and per-profile wallpaper preferences. Default scenes are unchanged. Only selected images are decoded; old and current scenes are retained during a fade. New wallpapers follow actual 1080p/4K output size; settings use small sampled thumbnails. Images are bundled and work offline.

## Validation and boundaries

The dedicated Actions workflow builds Java/Android/Media3, verifies untouched files by SHA-256, and runs native Android 14 tests at 1920×1080 and 3840×2160. Tests use real OK and Back expansion, repeated arrows, a Settings lifecycle round trip, lens-geometry restoration, Classic checks, actual UHD bitmap bounds/display-sized decoding, persisted choices, and zero disk reads during warmed scene painting. The existing live TV tests still exercise real channel video/audio, all eight sections, search, favorites, last channel, fullscreen, failure handling, offline saved data, and other center opening.

Only five production source files change (`MainActivity`, `Scenes`, `SettingsActivity`, `Ui`, `Clicks`), plus the independent `WallpaperPack` and additive art. Prayer/adhan/Quran, matches/news, live TV sources/player, application packages, HOME/Accessibility, AndroidManifest, original build scripts and original art remain protected byte-for-byte. The linked final Actions run and its real native screenshots are authoritative build/test evidence. APK uses a development signing key and may not update over a differently signed installation.
