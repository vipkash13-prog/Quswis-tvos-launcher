# Sliding app focus frame

Based on 1.0.193-football7. A single AppFocusIndicator above the home layers replaces
the two older focus lenses. It renders a transparent rounded rectangle with a
bright white rim, a contrasting dark rim, and a soft pale-blue glow. The app art
remains visible. Movement and resizing interpolate for 220 ms; a new direction
starts from the currently drawn frame, so quick remote presses do not snap back
to the previous app. First appearance fades in and leaving the apps fades out.

The same frame follows Classic app tiles, the night-scenes dock, and expanded
Other Apps. It maps the complete ancestor transform/scroll chain into root
coordinates on pre-draw, including the existing icon zoom animation. It needs no
bitmap blur or per-frame allocations for bounds. It does not alter icon/grid/dock
sizes or remote navigation. Its elevation keeps the rim above lifted app artwork.

The view is neither focusable, clickable nor long-clickable, and is excluded from
the accessibility tree. Only registered app tiles qualify as targets. It is
hidden for sheets, popovers, menus, launches, loss of window focus, pause/ambient
mode and idle mode. Animators and view-tree observers are released on detachment.

The approved adhan, seven-match lens and remote navigation fixes are unchanged.
Build, APK v2/v3 signature and zip alignment checks passed. No physical Android
TV rendering or remote-control test was performed in this environment.

Build:

    VERSION_CODE=194 VERSION_NAME=1.0.194-focusframe bash build.sh

Suggested device checks: left/right and up/down transitions; fast repeated D-pad
presses; scroll across Classic grid rows; expanded night-scenes apps; white and
dark icons; open/close a context menu or Control Panel; launch/return from an app;
change appearance mode. The frame should neither take focus nor remain above a menu.
