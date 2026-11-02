# Haram adhan and interrupted media — Ali Mulla recording

Base: release 182 / 69c7a03e2e1f32b2a45e5132f899caad53974867, retaining the
vertical prayer UI from 1.0.189-prayer182. The prayer UI, window dimensions,
MainActivity, SceneHome and manifest are unchanged from that build.

The former 2012 crowd recording has been replaced with Assabile recording a119,
attributed by the source to Ali Ibn Ahmad Mala at Masjid al-Haram (246.412 seconds).
The source is https://www.assabile.com/adhan-call-prayer/makkah . Original MP3
bytes are retained with no filters, trimming, or resampling. The source does not
document a recording date. Credits and source metadata are in assets/audio/ATTRIBUTION.txt
and prayer settings; the former CC BY 3.0 claim does not apply to this replacement.
No license is asserted beyond the source's published download offering.

A technical comparison of mono 16 kHz PCM in 100 ms windows found 5th-percentile
RMS of -48.4 dBFS in this file versus -24.9 dBFS in the former recording.
This indicates lower levels in quiet windows, not proof of voice-only content.
A complete listening review for people/crowd noise has NOT been performed.
The recording uses a 16 kbps mono MP3, so low background levels should not be
confused with studio fidelity. A separate copy is provided for listening.
Audio playback needs no network. Refreshing prayer times still needs Internet.

Enable prayer alerts, select country/city, and allow notification-listener access
using the new prayer-settings row. On Android 12+ allow exact alarms using the
provided settings row for timely background starts. On Android 13+ enabling
prayer alerts requests notification permission. Settings preview invokes the
same foreground playback service as a real alarm, including media control.

The service prepares audio before interrupting a film, captures currently playing
media sessions, acquires transient audio focus, and pauses supported sessions.
It never changes apps or seeks the film. On completion it releases audio focus,
then resumes only the captured session that is still paused on the same media
and position. It skips sessions changed, stopped, manually played again, sought
elsewhere, or destroyed during the adhan. Previously paused sessions are excluded.
Audio focus may itself pause/resume compatible players, including without session
access. No generic PLAY key is sent. A competing audio-focus request cancels the
adhan without explicitly resuming the film. Errors and explicit Stop restore
eligible paused media; a watchdog prevents indefinite interruption if playback stalls.
The notification has an explicit Stop action.

MediaSession pause/play support and notification-listener permission are needed
for targeted restoration. Apps that do not publish controllable media sessions
or respect audio focus cannot be guaranteed to pause/resume. No process is killed
or suspended. If Android terminates the service, no later automatic relaunch or
unconditional PLAY is issued. User/system playback decisions take precedence.

Validation: full compilation, 11 restoration-policy cases, 14 countdown cases,
APK v2/v3 signing and alignment, bundled audio byte comparison, unchanged UI
source comparison. Physical-device, real background alarm, and streaming-app
integration tests have NOT been run in this environment.

Build: VERSION_CODE=191 VERSION_NAME=1.0.191-mulla bash build.sh
Output uses the existing local test keystore. It is not signed with the owner's
release key, so Android may reject an update over a differently signed install.
Do not remove an existing install without backing up settings.

Host checks after building:

    javac -cp build/classes -d /tmp/adhan-checks tests/*.java
    java -cp build/classes:/tmp/adhan-checks com.techgenius.tvoslauncher.MediaResumeGuardTest
    java -cp build/classes:/tmp/adhan-checks com.techgenius.tvoslauncher.PrayerPanelClockTest
