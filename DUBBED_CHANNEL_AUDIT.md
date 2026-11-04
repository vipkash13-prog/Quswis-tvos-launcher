# Arabic-dubbed live TV audit — 2026-11-04

Eight sections, the first seven in their original order. Version remains 1.0.205. Independent branch `feature/live-tv-arabic-dubbed`; no main merge. No MainActivity, launcher background, lenses, dock, clock/weather, prayer/adhan/Quran, match or news code changes.

## Requested channels and actual findings

| Channel | Finding | Decision |
|---|---|---|
| Spacetoon Arabic | The exact existing `live-uae-next.spacetoongo.com/ST_MENA_NEXT/hls/r9p2hjipmw2kl.m3u8` route returns a manifest and decodable video. The upstream audio tag says `eng`, while IPTV-org's feed language is `ara`; this tag is not treated as new Arabic proof. | Preserve the existing channel, URL, referrer and prior user confirmations; no duplicate, replacement or deletion. |
| MBC3 | The regional `MBC3.ae` has no stream in the six inspected lists. The actual listed **MBC3 USA** feed has decodable video and an explicit `ar` audio track. | Add the verified **USA variant** to Arabic children's TV without pretending it is MBC3.ae. |
| Cartoon Network Arabic | The listed stream uses an unverified numeric-IP restream. No authorized direct broadcaster URL was found. | No new authorized addition; no fabricated alternative. |
| Zee Alwan | The official site documents Indian, Turkish and international Arabic-dubbed programming. No direct stream was found in the six supplied lists or the official pages inspected. | Not added as a playable channel. |
| Zee Aflam | The official site documents Indian films dubbed **and subtitled** in Arabic. No direct stream was found in the inspected sources. | Not added; Arabic subtitles alone do not prove Arabic audio. |
| MBC Bollywood | Its exact movie-list stream decodes video and exposes an `ar` audio track. The operator documents Indian/Bollywood programming that can be Arabic-dubbed or subtitled. | Add the actual verified stream to the eighth section. Do not claim every programme is dubbed. |
| SAT-7 KIDS | The listed public stream decodes video. The broadcaster explicitly documents an Arabic children's channel with cartoons, dramas and religious programming. | Add as Arabic children's TV; it is **not** labelled anime-only. |
| Koogi / Taha | Decodable video was found, but the sampled stream metadata did not specify the audio language. | No new automatic dubbed classification based only on their names or country. Existing catalogue access remains available. |
| B4U Aflam / other Korean or Turkish dubbed channels | No additional authorized, directly playable source with sufficient evidence was established in this audit. | No invented entries or programme availability claims. |

## Sources

The six supplied IPTV-org lists were fetched successfully:

- https://iptv-org.github.io/iptv/countries/ae.m3u
- https://iptv-org.github.io/iptv/regions/arab.m3u
- https://iptv-org.github.io/iptv/categories/animation.m3u
- https://iptv-org.github.io/iptv/categories/kids.m3u
- https://iptv-org.github.io/iptv/categories/movies.m3u
- https://iptv-org.github.io/iptv/categories/series.m3u

Channel/feed identity and language data: https://iptv-org.github.io/api/channels.json and https://iptv-org.github.io/api/feeds.json .

Publisher documentation:

- https://www.zeealwan.com/?page_id=29 — Arabic-dubbed Indian/Turkish/international programming, not a direct-stream promise.
- https://www.zeeaflam.com/ — Hindi films dubbed and subtitled in Arabic.
- https://www.mms.net/ — official MBC channel profiles.
- https://mbc-lite-strapi-s3.s3.me-south-1.amazonaws.com/MBC_GROUP_Annual_Report_2024_English_96654a47af.pdf — MBC Bollywood content is dubbed or subtitled in Arabic.
- https://sat7.org/our-channels/channel-overview/sat-7-kids/ — official Arabic-language/channel-content documentation and public viewing links.
- https://spacetoon.com/ — official broadcaster; the pre-existing stream is preserved verbatim.

The MBC streams were initially rejected with an irrelevant third-party referrer. Normal requests using their actual source configuration succeeded. No login, DRM license, proxy relocation or subscription bypass was used.

## Evidence and preservation

`assets/live-tv/dubbed-verified.json` records the exact catalogue URLs, first-frame decoding evidence, audio proof or explicit publisher language documentation, and the check time. This records a past successful audit, not a guarantee against future outages or regional restrictions. Actual playback errors override the availability status shown on the device.

Successful old endpoints are migrated before replacing a daily cached playlist. They keep their original order; a failed new URL never replaces them. The pre-existing Spacetoon URL is also available as a protected fallback. Stable channel IDs deduplicate rows within each section. Favorites, last channel and existing Arabic confirmations are retained. Historical Arabic confirmations do not silently disappear after 24 hours; they remain separate from the latest playback/error status. A catalogue refresh does not manufacture a new local playback-success record.

The eighth section uses documented channel identities plus playback/audio evidence. A Korean/Turkish name, country or Arabic subtitle is never enough. New candidate sources can be tested through the existing player/settings flow before qualification.

## Validation

Pure parser/preservation tests and Android 14 instrumentation verify eight-section DPAD traversal, complete long labels, actual Media3 playback of MBC Bollywood, MBC3 USA, SAT-7 KIDS and the unchanged Spacetoon endpoint, failure-preservation, search/favorites/fullscreen/Back, 1080p/4K, and offline saved data. The GitHub Actions run linked in the release is the authoritative final result.
