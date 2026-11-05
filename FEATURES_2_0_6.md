# Quswis 2.0.6 preview (build 207)

Based on the verified 1.0.206 audio/performance branch. Changes are limited to the match window's provider/cache/selection, the eighth Live TV tab and its account store, the existing player/image integration, and the app updater/settings. MainActivity, original manifest, HOME/Accessibility, themes, backgrounds, app order and the audio/adhan fixes remain protected by file hashes.

## Matches

ESPN soccer scoreboards accept a day (`YYYYMMDD`) or month (`YYYYMM`), but not the attempted date-range form. UEFA calendars may contain season-stage objects rather than strings. The match window now queries the current and next month through the same ESPN provider instead of guessing five future calendar strings. Today and yesterday have separate refreshed slots. A newly refreshed slot takes precedence over an older monthly snapshot of the same event, and successful partial fetches survive cancellation. All of today's upcoming matches remain available with five visible rows and remote pagination; when none remain today, the next genuinely scheduled fixtures are shown. Live and today's results remain based on provider status and the actual Saudi date. Empty/live/offline/failure messages are distinct; no completed match is fabricated as live and no yesterday result is relabeled as today's result.

## Live subscription

The first seven tabs remain. Tab eight is now `قنوات اشتراكي IPTV`, using the requested server. Only account authentication, `get_live_categories`, and `get_live_streams` are called. No VOD/series API methods exist in the client's action allowlist. Category selection, remote playback, HLS/MPEG-TS according to the authenticated server response, fallback between advertised formats, retry, search, favorites and refresh use the existing modal/player.

Android Keystore AES-256-GCM protects credentials and the subscription catalogue in `noBackupFilesDir`. Ordinary favorites/last-channel records contain opaque account/stream identifiers, never authenticated playback URLs. Logo cache keys are opaque too. Real API/playback URLs are resolved in memory; authenticated HTTP exceptions are sanitized. The configured server uses HTTP: local encryption does not encrypt that network transport. Subscription credentials must never be committed, included in screenshots, put in CI secrets, or uploaded to GitHub. Actual-account validation runs locally with private input, separately from CI's generated test credentials and public NHK streams.

## Updates

Updates are unfrozen. The existing settings page now offers manual checking and automatic **checks**, with versioned Update now/Later consent. No silent LocalAdb install or automatic download remains. A dismissed version is suppressed for automatic prompts for 24 hours, while a manual check is available. The old settings-page check/refresh/check loop is removed.

Stable GitHub Releases are queried. Only HTTPS assets in this project's releases are accepted; GitHub CDN redirects and digest checks are enforced. APK package, increasing Android version code and exact signer certificate are verified before installation. All download/copy work is off the main thread. Android's installer is explicitly required to request user approval; a missing unknown-sources permission has a remote-accessible retry path. No uninstall or data-clearing fallback exists.

The old GitHub `v2.0.5` and preview `1.0.206` used different version naming. This preview uses name `2.0.6`, code `207`; an installed app's Android version code/signature still govern update compatibility. The old 1.0.206 private signing key was not retained by its CI build and cannot be recovered from its APK. A different key cannot update it in place. Do not uninstall it or erase its data automatically.

## Private signing and validation

A persistent private signing key is retained outside the repository. GitHub Actions builds unsigned app/test/higher-version test-fixture APKs. The reviewed exact bytes are signed locally, with v2/v3 signatures and no v1 entry changes. Only public certificate/signature blocks and immutable input/output hashes are returned to Actions. `tests/apk_detached_signatures.py` only inserts that public signing block and adjusts the ZIP directory offset; it rejects any changed APK entry or hash. Actions independently verifies APK signatures, then exercises Android 14/1080p/4K remote behavior and consent/signature protection. Neither subscription credentials nor the private signing key enter GitHub.

CI's Xtream-compatible fixture is test-only: its generated account serves a real public NHK HLS stream and a freshly captured transport-stream sample. It checks the native integration and verifies that no VOD/series methods were called. It does not prove that the user's actual subscription works. Final delivery requires separate successful actual-account native playback with authorized temporary credentials, recorded without those credentials. Until that account is supplied and tested, that requirement remains pending.
