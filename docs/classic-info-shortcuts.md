# Restore the Classic information shortcuts (1.0.198)

The former shortcut alpha/visibility rules still depended on the old shelf scroll
position. The newer app-grid viewport therefore hid weather, prayer and football
while their preferences remained enabled.

Enabled Classic shortcuts now occupy a fixed horizontal row below the status
controls (80 design units from the top). Each retains its original 214 x 52 size;
the existing 12-unit gap separates them. Disabled shortcuts leave no occupied
slot. Three shortcuts fit in a 666-unit row on the launcher's 960-unit design grid.
The app viewport starts below this row at 148 units when any shortcut is enabled;
otherwise the prior 80-unit band remains. Card sizes and lens dimensions are unchanged.

The existing Night Scenes vertical positions remain intact. Football visibility
no longer depends on a stale Classic scroll offset after changing modes. Modal
panels and the Other Apps folder continue to hide the underlying shortcuts.
The weather placeholder can remain visible while enabled weather data loads;
weather data itself is not fabricated or reset.

Classic navigation: UP from the first app row reaches the shortcut row, LEFT/RIGHT
move between visible shortcuts, UP reaches status controls, DOWN/Back returns to
apps, OK opens the corresponding lens. DOWN from the status controls reaches the
shortcut row. The obsolete Classic vertical football/weather shortcut handler
was removed to prevent conflicting or invisible focus routes.

Validation: APK compilation, signature and alignment, 19 viewport checks including
the reserved shortcut row, and byte/source comparison confirming unchanged full
weather/prayer/match lenses, Night Scenes, focus motion and approved adhan.
No physical TV visibility/navigation test has been performed in this environment.

Build:

    VERSION_CODE=198 VERSION_NAME=1.0.198-infostrip bash build.sh
