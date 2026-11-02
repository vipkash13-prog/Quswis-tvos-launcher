# Restore the original home composition (1.0.199)

The user requested a return to the established home layout after the horizontal
shortcut strip and minimum-scroll changes altered its composition.

Classic now uses release-182 placement again: the first dock row starts below
the original 73%-height shelf; app dimensions, gaps, margins and corner radii
are the original values. Weather, prayer and football shortcuts retain 214 x 52
sizes and their original vertical positions at 24, 88 and 152 design units.
The new horizontal strip and reserved 148-unit top band are removed.

Row zero returns to scroll position zero. Lower rows use the original 30%-height
anchor, bounded by actual content extent. Coalescing/one scroll animator and the
new spring frame remain to avoid duplicate native scroll jumps. Lower app rows
and their frame are clipped below the fixed status controls so they cannot draw
behind settings. The status/settings controls remain available, as does Menu.

The original home shelf shows the enabled information shortcuts. While browsing
lower app rows, Classic shortcuts fade/hide with the shelf to avoid covering app
art. Back from the app grid and Home return to the first dock row and reveal the
shortcuts again. Night Scenes keeps its existing visible vertical shortcuts and
is unaffected by the Classic scroll offset.

On the first Classic launch of this revision, only the remembered focus index
is reset to zero. App order, city, enabled features, teams and other preferences
are not cleared. Later launches resume normal focus persistence.

The full prayer detail design, seven-match ESPN lens, weather lens dimensions,
approved adhan and media pause/resume are retained unchanged.

Validation: 11 original-anchor/boundary checks; exact comparison of app/shelf
sizing constants to commit 69c7a03 (release 182); comparison of full lens code and
other features to 1.0.198; complete APK build, signature and alignment checks.
No physical TV layout or remote validation was performed in this environment.

Build:

    VERSION_CODE=199 VERSION_NAME=1.0.199-layoutrestore bash build.sh
