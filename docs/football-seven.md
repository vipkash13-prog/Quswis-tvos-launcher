# Seven matches in the existing lens

The fixed MainActivity football popover remains 380 x 456 design units with the
same position, LiquidGlass background, corner treatment and opening animation.
Only its internal rows are fitted to seven slots: teams/logos/score, league/status/
local kickoff date/time, and a rotating event line. Fewer than seven real matches
leaves unused slots blank; fabricated fixtures are never added.

The public ESPN soccer scoreboard is now the single match source. The former
365Scores attempt and MatchGrind diagnostic have been removed. No API key or
paid account is required by the tested endpoint. This is an undocumented public
endpoint with no service or coverage guarantee; failures and missing events are
shown explicitly. Automatic refresh polls every 30 seconds while the lens is
open and the home activity is resumed. This is polling, not a real-time push feed.

Verified league mappings (HTTP 200, 2026-11-02):

| ESPN ID | Competition |
| --- | --- |
| ksa.1 | Saudi Pro League / Roshn |
| afc.champions | AFC Champions League Elite |
| uefa.champions | UEFA Champions League |
| eng.1 | English Premier League |
| esp.1 | Spanish LaLiga |
| ita.1 | Italian Serie A |
| fra.1 | French Ligue 1 |
| ger.1 | German Bundesliga |

A successful empty response is not treated as an error. Additional real fixtures
were verified for ksa.1 on 2026-10-30, uefa.champions on 2026-11-03 and ger.1 on
2026-11-07. Queries use individual dates: the tested date-range form returned 400.

The app fetches today, yesterday (for overnight live matches/recent results), and
up to seven days ahead. Fetches run on at most four workers with per-request and
whole-refresh timeouts. It retains separate league caches; a failed league uses
marked cached data rather than silently presenting it as a fresh live update.
Dates and display times use the device timezone, including day boundaries.

Selection: live first, today's upcoming fixtures, today's results, future fixtures,
and yesterday's remaining results. Leagues rotate fairly within each category.
Seven slots cannot show all eight selected leagues at once; only available
fixtures in the current date window can be displayed. Existing selected leagues
are preserved and can be changed from football settings.

Final scores are retained. Scheduled games never display an invented 0-0 score.
Postponed, canceled, delayed, halftime, extra-time and shootout states are labeled.
The events line cycles every five seconds through the source's provided match
incidents (goals, own goals, cards, substitutions and other reported events).
Missing incident coverage is labeled unavailable, not "no events". Team logos
use a bounded bitmap cache and late responses cannot overwrite another team's logo.

Validation: full APK build/signature/alignment checks; 23 parser/selection checks,
including an ESPN final-match snapshot; direct endpoint checks for all eight
leagues; unchanged host lens geometry, approved adhan, focus fixes and prayer UI.
No physical TV visual or remote-control test has been performed for this build.
The existing local test signing key is retained.

Build:

    VERSION_CODE=193 VERSION_NAME=1.0.193-football7 bash build.sh

Host football checks (org.json json-20240303.jar required):

    javac -cp json-20240303.jar -d /tmp/football-checks src/com/techgenius/tvoslauncher/FootballData.java tests/FootballDataTest.java
    java -cp /tmp/football-checks:json-20240303.jar com.techgenius.tvoslauncher.FootballDataTest

Test snapshot source:
https://site.api.espn.com/apis/site/v2/sports/soccer/eng.1/scoreboard?dates=20261101&limit=100
Only fields used by the parser are retained; this snapshot is test-only and is
never bundled into the APK or displayed as current application data.
