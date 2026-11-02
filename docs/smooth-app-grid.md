# Smooth app-grid browsing

Build 1.0.195-smoothgrid retains the 1.0.194 focus frame, all card/dock dimensions,
night-scenes behavior, prayer/match lenses, and the approved adhan recording.

Classic grid scrolling no longer sends every selected row to a fixed vertical
position, or sends row zero to scroll position zero (the large hero area). It
uses a visible band with 80 design units above and 24 below, and scrolls only
when the focused app plus its label/frame would leave that band. A minimum scroll
keeps the first app row in the grid-browsing area; short content still respects
the actual ScrollView extent. Already visible apps do not move the page.

Focus and layout requests are coalesced into one callback per frame. One 260 ms
animator handles the scroll and retargets from its current position during quick
D-pad repeats. Horizontal focus changes reuse an in-flight scroll destination.
This replaces duplicate smoothScrollTo requests, which Android can turn into
immediate jumps when called within its smooth-scroll time gap. An old native
fling/scroller is stopped with zero velocity before the controlled animation.

The hero/recommendations area remains accessible explicitly via UP from the
first app row (and its existing tabs). Loading recommendations no longer switches
it on merely because an app in the first row has focus. Grid animations are
canceled for hero navigation, pause, rebuilds, or switching to night scenes.

Validation: 16 viewport checks, including returning to row zero, keeping visible
rows stationary, small-content limits, rapid-request destination reuse, and
scaled geometry. APK build, v2/v3 signature and alignment passed. Layout creation
and the indicator source were compared against 1.0.194 and remain unchanged.
No physical TV motion test has been performed; final smoothness needs a device test.

Build:

    VERSION_CODE=195 VERSION_NAME=1.0.195-smoothgrid bash build.sh

Host checks:

    javac -d /tmp/grid-checks src/com/techgenius/tvoslauncher/GridViewport.java tests/GridViewportTest.java
    java -cp /tmp/grid-checks com.techgenius.tvoslauncher.GridViewportTest
