# Match Center preview

Development branch: `feature/match-center-glass`, based on the completed Islamic
window commit `b89b7c3bb9f4125bcf70fd517a9819efb9b09d9f`. No merge into main.

The only existing application source changed is MainActivity: connect the existing
football-lens action to a native MatchCenterDialog, dispose it with the host, and
exclude it from idle mode. The previous football popover is retained as
showLegacyFootballPanel, with its implementation unchanged. No Home view is
replaced and no new Home section is added.

SettingsActivity, AndroidManifest, build.sh, original FootballScores/FootballData,
all prayer/adhan/Quran sources, updater, original wallpapers and Islamic-window
artwork are protected by baseline SHA-256 checks in Actions. HOME, package names,
Accessibility, version 1.0.205/205 and frozen updates remain unchanged.

Reference viewport: 2400×1050 on 3840×2160, 1200×525 on 1080p. The popup sits right
of the original side lenses and above the original app dock. Stadium artwork is
restricted to the 2400×300 header. Glass panels, borders, text, focus states and
cards are native Android views/drawables. Logos are fetched from the provider;
no example match, score or hardcoded crest is bundled into the app.

Provider: the existing ESPN public soccer scoreboard. The original provider and
its settings are not edited or replaced. MatchCenterData reuses FootballData.parse
and FootballScores.arabicTeam. Separate match-center preferences import existing
selected leagues once; subsequent choices do not alter football settings or
existing team filters. All matches in each selected league are eligible.

Saudi Arabia national-team ID 655 is verified from ESPN's returned team object.
The same provider's /all/teams/655/schedule and ?fixture=true supply national-team
results and upcoming matches. Other leagues use today's/yesterday's scoreboards
and upcoming dates declared in ESPN's own league calendar. Future requests stop
after enough real matches or five match days. Invalid/empty responses are handled
separately; empty displays «لا توجد مباريات متاحة حاليًا».

All classification, day boundaries and displayed kickoff times use Asia/Riyadh.
Upcoming shows the five earliest scheduled matches. Live and completed-today
views paginate through all available results in five visible rows with UP/DOWN.
Completed-today means the match's provider kickoff date in Saudi time. Unknown
team translations retain their provider name. Missing scores/logos/incident
coverage are explicit; no 0–0 score or substitute team crest is invented.

Selecting a row opens its provider incident/venue details inside this same dialog.
BACK closes the dialog and restores the original football lens. DPAD reaches the
tabs, multi-select league list, match rows and close control. Live polls at most
once per 60 seconds while visible; other sections refresh every five minutes.
Requests use three workers, bounded timeouts, cache TTLs and 429/503 Retry-After
backoff. Closing/backgrounding cancels pending UI work. ESPN is an undocumented
public endpoint: there is no guaranteed coverage, uptime or push-based latency.

Each group/date has a separate persistent cache. Network failure preserves the
last usable response, marks displayed data as cached and shows the oldest cache
update time. Disconnected devices immediately use that cache. Logo downloads use
6 MiB of bitmap cache and 24 MiB of disk cache, preserve transparency, limit decode
size and ignore responses for recycled rows. There is no second provider/API.

The independent match-center.yml workflow runs baseline integrity checks, all
existing prayer/football regressions, new selection/timezone cases, and Android 14
instrumentation for the unchanged prayer window and new center on 1080p and 4K.
It also checks persisted league choices and cache display with networking disabled.
Real provider responses used as test fixtures stay under tests/, outside APK assets.
The prerelease includes a directly downloadable APK and actual viewport screenshots.
It retains the repository's existing development-signing fallback; matching the
installed app's signing key is necessary for an in-place installation.

Physical MECOOL KM2 Plus Deluxe acceptance is still a device-side check.
