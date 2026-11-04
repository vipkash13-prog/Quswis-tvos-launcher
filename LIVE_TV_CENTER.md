# Live TV Center preview

Branch: `feature/live-tv-center`, based on the completed global news center. Version stays 1.0.205. No merge to main.

## Scope

- `LiveTvDialog`: independent modal, original live lens opens it. Native horizontally scrolling seven-section bar, recycled ListView rows, keyboard/search, source/EPG/settings panels, fullscreen and explicit player buttons.
- `LiveTvPlayer`: AndroidX Media3 ExoPlayer 1.5.1 with HLS/DASH/progressive media, real decoded-frame confirmation, standard system audio focus and release on close/pause. DPAD focus does not change channels.
- `LiveTvData`/`LiveTvRepository`: supplied seven M3U sources, streaming channel/feed metadata from the same iptv-org project, daily atomic disk updates, stable channel IDs, persistent favorites/last successfully viewed channel. No login or DRM configuration.
- `LiveTvEpg`: real optional XMLTV from a playlist header or user-provided URL. No schedule is invented when a source is missing.
- `LiveTvImages`: independent bounded lazy logo cache with recycled-view identity checks.
- `assets/live-tv`: supplied transparent window/panel/row assets. **The supplied launcher wallpaper is intentionally not installed.**
- `MainActivity`: only opening the modal, lifecycle disposal and idle guard. The former external-app behavior is retained under the center settings. Existing package preference, SettingsActivity, manifest source, HOME and Accessibility remain unchanged.
- `live-tv-build`: isolated Gradle build required for Media3/transitive Android libraries. Existing `build.sh` is unchanged. A generated copy of the original manifest keeps all components identical and enables cleartext traffic for legacy HTTP links actually present in the supplied public playlists. No package/intent/service changes.
- `.github/workflows/live-tv.yml`, new tests and protected-file checks: builds a direct testing APK and real Android screenshots, no main merge.

## Honest availability and language

A public catalogue entry is **not** proof that playback works, is available in every country, or has Arabic audio. The player records success only after a real decoded frame. Failures and geo restrictions remain visible; there is no DRM bypass. Known subscription brands, credential playlists, unknown IP restreams and metadata-marked closed/adult channels are excluded.

The Arabic cartoon section requires a successful current source and actual Arabic audio evidence: a selected Arabic audio track or the viewer explicitly confirming that they heard Arabic after successful playback. Names/country alone never qualify. The source feed metadata can list candidates, but those appear in a separate verification panel and are not labelled confirmed dubbed channels. A source claiming `ara` while its actual track is tagged `eng` is not silently considered confirmed. A clearly explained empty state is expected when no source meets the evidence requirement. No Conan/Hunter/One Piece schedule is claimed without real EPG data.

## Build

JDK 17, Android SDK platform/build tools 35 and Gradle 8.9:

```sh
gradle -p live-tv-build assembleDebug assembleDebugAndroidTest
```

The original plain-SDK build is preserved for the original branch. This Media3 branch must use the independent build above.
