# Reference launcher spring motion (1.0.196)

The user supplied https://github.com/udhaykumarbala/cleanyourtv-app . Its README
identifies the actual bundled launcher source as:
https://github.com/Gathin23/cleanyourtv/tree/main/launcher

Inspected source:
- launcher/app/src/main/java/com/gathin/tvhome/MainActivity.java
  (spring initialization and onTileFocused)
- launcher/app/src/main/java/com/gathin/tvhome/GlassView.java
- launcher/app/src/main/res/values/config.xml

The inspected defaults have advanced_effects=false and focus_overshoot=true.
Their positional springs use stiffness 260 and damping ratio 0.58, retain velocity
when retargeted, and separate grid scrolling from spring displacement. First
appearance fades in for 180 ms. The focus frame is sized for the final focused
card scale instead of chasing the icon's zoom animation.

This project now applies those motion parameters and that coordinate-space
approach using a small independently implemented closed-form damped spring.
It does not import AndroidX or the reference launcher's app/debloat features.

AppFocusIndicator moves its existing spring positions by the content container's
root-space displacement before stepping toward the selected card. Scrolling
therefore moves the frame and app together, rather than becoming a second spring
target. New left/right/up/down targets preserve velocity. Long idle gaps do not
produce a first-frame jump. The same behavior covers Classic and night scenes.
Frame styling, card sizes, layout, 1.0.195 grid scrolling, match/prayer lenses,
approved adhan and media restoration remain unchanged.

Validation: 18 spring checks including overshoot, settling, retarget continuity,
30/60/120 Hz equivalence, scroll-space equivalence, and rapid changes. Complete
APK build/signature/alignment verified. Rendering/motion has not been tested on
a physical TV; exact visual agreement with the user's reference video is not
claimed without that test.

Build:

    VERSION_CODE=196 VERSION_NAME=1.0.196-springfocus bash build.sh

Host spring checks:

    javac -d /tmp/focus-spring-tests src/com/techgenius/tvoslauncher/FocusSpring.java tests/FocusSpringTest.java
    java -cp /tmp/focus-spring-tests com.techgenius.tvoslauncher.FocusSpringTest
