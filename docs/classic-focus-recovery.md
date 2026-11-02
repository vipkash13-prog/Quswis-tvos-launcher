# Classic focus recovery (1.0.197)

User report: after entering Classic, the app grid is visible but controls and
selection appear unavailable. The earlier grid viewport change still used the
old scroll-dependent alpha for the entire clock/control row. It could therefore
hide the settings/escape controls while leaving their views focusable.

This build keeps that row visible during Classic browsing and launch return,
and cancels stale row fades when restoring focus. The top-focus helper excludes
invisible tab bars. Resume, window-focus gain and Classic key-down recover focus
when it is absent, outside the known home targets or on an invisible ancestor.
Modal overlays retain priority and focus is not taken away from their controls.

Classic horizontal movement and app OK/Enter handling are explicit. Row edges
do not wrap; vertical routes remain explicit as before. Holding OK keeps the app
options action. Menu now opens Control Center from Classic home (app options
remain available with long OK). Back from the regular app grid selects the visible
top controls; Back from Other Apps continues to exit that folder. A hidden night
scene sheet cannot suppress the Classic focus indicator.

The spring motion, card geometry, grid viewport, night-scene navigation, prayer/
match panels and adhan behavior are retained. Build, APK v2/v3 signature and
alignment passed. The certificate was compared to 1.0.196 and matches, permitting
an in-place update of that APK without removing the app or clearing settings.

Physical TV switching/resume/remote behavior has not been tested in this environment.
Device checks needed: switch Night Scenes -> Classic; arrows and short/long OK;
Menu -> Control Center -> Settings; Back -> top row; launch/return; restart home.

Build:

    VERSION_CODE=197 VERSION_NAME=1.0.197-classicfix bash build.sh
