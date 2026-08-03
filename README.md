# IRIS Alarm

An Android alarm clock with no snooze button. Every alarm is dismissed by doing
something in the real world — smiling into the front camera, hunting down a
physical object, or walking somewhere bright — evaluated entirely on-device.

## Install

Grab the newest APK from [Releases](../../releases) — take `arm64-v8a` for any
modern phone, or `universal` if unsure (it is roughly three times the size).
After the first install the app updates itself: **Settings → Updates**.

Releases are signed with the repository's side-load key (`keystore/`), which is
what lets the in-app updater install over an existing IRIS. That key is public
by design and is **not** suitable for Play Store distribution — swap it for a CI
secret before publishing anywhere real.

## Status

Feature-complete: set an alarm, it splashes over the lock screen, and the only
way to silence it is to satisfy a camera or sensor challenge. 43 unit tests
(`./gradlew :app:testDebugUnitTest`).

## Stack

| Concern | Choice |
| --- | --- |
| UI | Jetpack Compose, Material3, edge-to-edge |
| Type | Space Grotesk via Downloadable Fonts (`ui-text-google-fonts`) |
| Vision | CameraX + ML Kit face detection & image labeling (bundled, offline) |
| Sensors | `SensorManager` / `TYPE_LIGHT` |
| Persistence | Room |
| DI | Hilt |
| Scheduling | `AlarmManager.setExactAndAllowWhileIdle` |

`minSdk 26`, `compileSdk`/`targetSdk 35`, JDK 17.

## Design system

Pitch black first, one accent, enormous type.

- Background `#000000`, off-black surfaces `#09090B`, elevated `#141417`
- Text `#FFFFFF`, secondary `#71717A`, hairlines `#27272A`
- Accent `#FFB703` (live detection, active state), confirm `#00FF66`
- Structural radius `32.dp` everywhere; no cards, no dividers, lots of air

`ui/theme/Type.kt` maps Space Grotesk across the entire Material3 scale, plus
`IrisType` for the pieces that sit outside it — the 116sp hero clock, the 72sp
challenge clock, the 36sp vision prompt, and the 56sp metric readout used by the
lux gauge and confidence meter.

## How an alarm fires

```
AlarmScheduler ──setExactAndAllowWhileIdle──▶ AlarmReceiver
                                                  │
                          ┌───────────────────────┴──────────────────┐
                          ▼                                          ▼
             AlarmForegroundService                    re-arm (repeating) or
        MediaPlayer · audio focus · vibration            disable (one-shot)
        wake lock · 10-min auto-silence
                          │
                          ▼ full-screen intent
              AlarmChallengeActivity  (setShowWhenLocked / setTurnScreenOn)
                          │
                          ▼ challenge solved
                 ACTION_DISMISS ──▶ service stops
```

Notes on the pieces that are easy to get wrong:

- **Exact alarms.** `USE_EXACT_ALARM` is declared for API 33+ (auto-granted to
  alarm-clock apps); `SCHEDULE_EXACT_ALARM` covers API 31–32 and is capped with
  `maxSdkVersion="32"`. `AlarmScheduler.canScheduleExact` reports whether the
  permission actually holds, and scheduling degrades to `setWindow` rather than
  dropping the alarm when it does not.
- **Rescheduling.** `AlarmManager` state does not survive a reboot, and a clock
  or timezone change invalidates the computed instants. `BootReceiver` re-arms
  from Room on `BOOT_COMPLETED`, `TIME_SET`, `TIMEZONE_CHANGED` and
  `MY_PACKAGE_REPLACED`.
- **Appearing at all.** A full-screen intent only takes over the screen while
  the phone is locked; when it is already in use Android downgrades it to a
  heads-up banner. `AlarmForegroundService` therefore starts
  `AlarmChallengeActivity` outright as well, which needs `SYSTEM_ALERT_WINDOW`
  ("display over other apps") to be allowed as a background activity start. That
  is why it is one of the required permissions. When it is missing the start is
  refused and the notification is the fallback, so the alarm still rings.
- **The keyguard is shown over, not dismissed.** Asking to dismiss it puts the
  PIN prompt in front of the alarm; `setShowWhenLocked` puts the alarm in front
  of the lock instead.
- **The notification has no dismiss action** — deliberately. The challenge is the
  only exit, other than the 10-minute auto-silence timeout.
- **Audio.** `USAGE_ALARM` attributes, looping `MediaPlayer`, transient audio
  focus that is never yielded. If focus is refused the alarm rings anyway.
- **Volume.** An alarm on a muted stream never wakes anyone, so the alarm stream
  is raised to a configurable floor while ringing and restored to whatever the
  user had when it stops. Volume fades in from near-silence over a configurable
  ramp. Raising the stream is refused under some Do Not Disturb policies; that
  is caught and the alarm rings at whatever volume it can.
- **Back is disabled** on the challenge screen (it sends the task to the back
  instead), so the alarm cannot be orphaned behind the lock screen.

## Challenge thresholds

All in `domain/model/VisionChallenge.kt` (`ChallengeThresholds`):

| Challenge | Passes when |
| --- | --- |
| Mirror (front camera) | `smilingProbability > 0.8` and both eyes open `> 0.7`, held 3s |
| Target (rear camera) | scene similarity to the captured anchor `> 0.82` across 4 consecutive frames |
| Hunt (rear camera) | ML Kit labels the named object at `> 0.55` confidence across 3 consecutive frames |
| Math (no hardware) | the configured number of arithmetic problems answered correctly |
| Light | `> 500 lux` sustained for 1.5s |

### Hunt Iris can only ask for things the model knows

The random object is drawn from a fixed pool in `HuntTarget`, and every entry was
checked against the label file inside the ML Kit model asset. That vocabulary is
447 labels and it is not the one anyone would guess: there is no *toothbrush*, no
*mug*, no *book*, no *towel*, no *door*. A target outside it produces a challenge
that can never be satisfied, which on a ringing alarm means sitting there until
the auto-silence timeout — so `HuntTargetVocabularyTest` re-checks every target
against a copy of that file, and asserts the file's own label count first so a
truncated copy cannot make the check vacuous.

The object is drawn **at ring time, not when the alarm is set**, so it cannot be
staged on the bedside table the night before. It is also the one challenge that
can be defeated by simply not owning the thing, which is what the swap answers —
twice, and then you are stuck with what you were given, because an unlimited
re-roll is an off switch with extra steps.

### Math Iris is the one that always works

Arithmetic needs no camera, no sensor and no permission, which makes it the last
entry in every fallback order in `resolveChallenge`. Before it existed, a device
with no camera and no light sensor resolved to null and the UI had to offer a
plain dismiss; now there is always a real challenge to run. It is never
substituted while any sensor challenge can run — an alarm that quietly downgrades
to a keypad is not the app anyone installed — and a test asserts exactly that.

The keypad is drawn rather than borrowed from the system IME: a soft keyboard
over the lock screen is at the mercy of whichever keyboard app is installed, can
be dismissed, and hides its number row behind a mode switch. A wrong answer
re-rolls the question and shakes the pad, but never takes back a problem already
solved — resetting a run for one fat-fingered keypad press is the kind of
punishment that gets an alarm clock uninstalled.

### Target Iris is a place, not an object

When setting the alarm you photograph a spot — the kettle, a bookshelf, the
bathroom mirror — and the alarm only stops when the camera is looking at that
spot again. Unlike hunting for a named object, it cannot be satisfied from bed.

`SceneSignature` pairs a **dHash** with a **luminance histogram**. The dHash
compares each pixel to its right-hand neighbour, so it encodes structure rather
than brightness — the room is far darker at 6am than when the anchor was
captured, and a raw pixel comparison would fail on that alone. The histogram
catches the opposite error, where a different wall happens to have a similar
edge layout. Structure carries 72% of the weight.

It reads the YUV luminance plane directly, so there is no colour conversion or
Bitmap allocation on the analysis thread. The stored thumbnail is greyscale and
96×128 — drawn from the same plane the matcher uses, so what you see is what is
compared, and no colour photograph of your home is written to disk.

**Its limits are real.** Matching is on framing and structure, so a very dark
room, a spot that has physically changed, or a wildly different angle will not
match. Capture something with shape to it, not a blank wall. An anchor alarm
cannot be saved without a captured spot, and an anchor whose spot has gone falls
back to a runnable challenge rather than ringing until the timeout.

## The wake check

Solving a challenge proves the user was awake for a few seconds, not that they
stayed awake — the obvious failure mode of a snooze-free alarm. When the wake
check is enabled, dismissing an alarm arms the same challenge again N minutes
later, and only an explicit "I'M UP" on the dashboard cancels it.

A wake check never schedules another one, or it would be an endless chain. It
uses a single fixed request code, so a second check replaces the first rather
than stacking. Deleting an alarm cancels a check belonging to it, so a check can
never outlive the alarm behind it.

## First run

Onboarding walks through what IRIS is and then every permission it needs, since
Android grants none of them without the user visiting a settings page. It can be
skipped — refusing to let someone into an app they just installed is worse — but
it says plainly what breaks. The same rows live in Settings → Permissions.

Two of the rows are "special app access" pages rather than permissions, and
neither can be requested with a dialog: **display over other apps**, which is
what lets the alarm take over an unlocked screen, and **install unknown apps**,
without which the in-app updater can find a release but never install it. Both
link straight to their own settings page for IRIS.

## The ringing screen

A ringing alarm shows the time on a card you push aside: **left snoozes, right
goes on to the challenge**. Two ringed targets sit behind the card and fill in as
it approaches them, so the commitment is legible before the finger lifts. The
card leans, lifts slightly and takes the border colour of the side it is heading
for. It is deliberately a drag across a third of the screen rather than a button,
because half asleep a button is easy to hit by accident — but a fling counts too,
provided it agrees with the direction already travelled.

As the card moves the whole canvas takes the colour of the decision — green for
stop, amber for snooze — and the line under it stops describing the gesture and
starts describing the consequence: "SNOOZING · RINGS AGAIN IN 9 MIN".

### How snooze works

Swiping left arms a one-shot `AlarmManager` alarm for **now + the snooze length**
(default 9 minutes, `Settings → Snooze`), silences the current ring, and posts a
low-priority ongoing notification saying *Snoozed until 06:09* with a **Cancel
snooze** action. When it fires, the same alarm rings again with the same
challenge, and can be snoozed again — each snooze replaces the pending one rather
than stacking, so there is only ever one outstanding. Solving the challenge,
cancelling from the notification, or disabling or deleting the alarm all cancel
it. Set the snooze length to off and the left half of the swipe goes away rather
than silently doing nothing.

Snooze is the one decision that is *not* an escape: it costs the user another
alarm, and the challenge is still waiting at the end of it.

The ordering inside `AlarmForegroundService` matters more than it looks. Arming
the snooze needs a settings read, which suspends, and stopping the service
cancels the scope that read is running in — so an earlier build called
`stopSelf()` first and killed the coroutine before it ever reached the scheduler.
The alarm went quiet and nothing was ever scheduled to bring it back, which made
snooze behave exactly like an off switch. Now the audio is silenced immediately,
the follow-up is armed, and only then does the service stop. The dismiss path had
the same defect, which is why the wake check also never fired.


Past the card comes a hand-off screen held for about 1.7s: the date, the time,
the alarm's label, and — the part that earns its place — **a preview of the
challenge that is about to be asked**, named and described, over a line that
empties as the hold runs out. A camera viewfinder appearing with no preamble
reads as the phone malfunctioning at 6am, not as an alarm. Tapping skips the
hold, because a splash that cannot be skipped only ever gets in the way of the
person who is already awake.

For the mirror challenge the screen becomes the light source: window brightness
goes to full and the scrim thins out, with a white radial wash that is bright at
the edges and clear in the middle — a ring light, not a flood, because washing
the whole screen white would blow out the preview it is meant to help. The
brightness is a window attribute, so it needs no permission, applies to the
ringing screen only, and reverts when it closes.

Then the camera fills the screen behind a scrim and **the instruction sits dead
centre in the largest type on screen**: "OPEN YOUR EYES WIDER", "GO TO YOUR
TARGET SPOT", "FIND BRIGHTER LIGHT". `instructionFor()` is the single place that
text is decided, so every state of every challenge — including the ones where
nothing is being detected yet — puts a usable instruction there. The progress
ring traces the edge of the display itself.

## Settings

`SettingsScreen`, backed by DataStore:

| Setting | Default | Why |
| --- | --- | --- |
| Appearance | System | Light, dark or follow the system. The ringing screen stays dark regardless |
| Clock | 24 hour | 12/24-hour across every surface, including the lock screen |
| Snooze | 9 min | Swipe-left length; off removes that half of the swipe |
| Default challenge | Mirror Iris | Pre-selects the challenge for new alarms |
| Math difficulty | Medium | Easy `47 + 26`, Medium `38 × 7`, Hard `24 × 17` |
| Math problems | 3 | Correct answers needed before a Math Iris alarm stops |
| Auto-silence | 10 min | How long an unsolved alarm rings before giving up |
| Volume ramp | 15 s | Fade in from near-silence, so the alarm wakes rather than startles |
| Minimum volume | 60% | Floor the alarm stream is raised to while ringing, then restored |
| Wake check | Off | Ring again this long after a solved challenge |

### Permissions

A dedicated screen covers everything the OS can withhold that would stop the
alarm appearing over the lock screen — notifications, full-screen intents, exact
alarms, display over other apps, battery optimisation, camera, install unknown
apps — each with why it matters and a hand-off
to the right system page, re-checked on return. It also names the OEM autostart
limits (Xiaomi, Samsung, Huawei, Oppo) that Android cannot report, because
pretending those do not exist is how an alarm silently fails on those phones.

### Updates

IRIS is side-loaded, so **Settings → Updates** checks the project's GitHub
releases, downloads the APK matching the device's ABI, and installs it through
`PackageInstaller` without leaving the app.

It reads the *list* of releases and picks the newest itself, rather than asking
for `releases/latest` — that endpoint silently omits pre-releases, which is how
an earlier build managed to report "up to date" with a newer release sitting on
the releases page. Ordering is decided here rather than trusting the order the
API returns, drafts are ignored, and a release carrying no APK this device can
run is passed over however new it is. Android only accepts an update
signed with the same key as the installed build, which is why the signing key is
in the repository — an APK from anywhere else is rejected by the platform, and
that rejection is surfaced verbatim rather than swallowed.

Android will not let a side-loaded app hand an APK to the installer until
"install unknown apps" is granted for it, and nothing prompts for that on its
own. That is treated as its own state rather than a failure: the card becomes a
button onto the right settings page, and the download resumes on the way back.
Printing "allow IRIS to install apps in Android settings" and stopping — which
is what an earlier build did — is instructions, not a fix.

The download is written to a `.part` file and moved into place only once the
whole body has arrived, so a dropped connection cannot leave a truncated APK for
the installer to choke on.

Auto-silence and volume ramp can be overridden per alarm — a weekday alarm can
ring for half an hour without every alarm doing so. `IrisSettings.effectiveFor`
folds an alarm's overrides over the globals, so everything downstream reads one
settings object and never has to know which value came from where. Null means
"follow the setting", and the editor offers DEFAULT as a real selectable value
so an override can be taken back off.

## Database

Schema v3.

- **v1 → v2** adds the nullable per-alarm override columns.
- **v2 → v3** replaces the object-hunt target with the captured place anchor.
  SQLite cannot drop a column here, so the table is rebuilt and rows copied
  across; an alarm that was an object hunt becomes an anchor with no spot
  captured yet.

There is deliberately no `fallbackToDestructiveMigration`: wiping someone's
alarms on an upgrade means they do not wake up. `IrisDatabaseMigrationTest`
asserts rows survive each step and the whole v1 → v3 path.

## Tests

```bash
./gradlew :app:testDebugUnitTest      # 24 tests, no device needed
./gradlew :app:connectedDebugAndroidTest   # needs a device or emulator
```

JVM tests cover the scheduling maths, the Room entity round-trip, challenge
resolution across every missing-hardware combination, and the per-alarm settings
fold — the pure logic was kept free of Android types precisely so these stay
fast.

Instrumented tests cover what only a real framework can answer: that scheduling
registers a broadcast `AlarmManager` can deliver and cancelling removes it, that
the wake check arms and cancels independently of the alarm, the Room round-trip
against real SQLite, the v1 → v2 migration, that the challenge Activity renders
and survives a back press, and that every `HuntTarget` exists in the shipped
model vocabulary.

## Build

```bash
echo "sdk.dir=/path/to/android-sdk" > local.properties
./gradlew :app:assembleDebug     # ~100 MB, all ABIs, debug-signed
./gradlew :app:assembleRelease   # split per ABI, R8, side-load signed
```

The debug APK is large because the ML Kit models are bundled for offline use —
an alarm cannot depend on the network at 6am. The release build shrinks with R8
and splits by ABI, taking arm64 from 81 MB to 27 MB.

### Cutting a release

```bash
# bump versionCode and versionName in app/build.gradle.kts first
git tag v0.3.0 && git push origin v0.3.0
```

`.github/workflows/release.yml` runs the tests, builds the signed split APKs and
publishes them as a GitHub Release — which is also what the in-app updater
reads. The manual trigger builds artifacts without publishing.

## Screens

- **Dashboard** (`ui/dashboard`) — hero clock, time to the next alarm, one row
  per alarm with its challenge glyph and repeat summary, and an inline warning
  (tap to fix) when the OS has revoked exact alarms. **Long-pressing a row starts
  a selection**: a ring appears at the left of every alarm, the enable toggles
  step aside so nothing is flipped by accident, and the bottom action becomes
  select all / cancel / delete. Back leaves the selection rather than the screen,
  and the selection is dropped for any alarm that stops existing while it is
  held.
- **Editor** (`ui/editor`) — a three-step wizard: the radial time picker and day
  chips, then the five challenges as animated full-width cards with **TRY IT
  NOW**, then label, system ringtone picker and vibration.
- **Challenge** (`ui/challenge`) — the ringing surface. A progress ring traces
  the rounded outline of the camera window or lux gauge as the detector closes
  in; the readout under it shows the live number and one line of guidance.

## Detector behaviour

Each detector reports a `ChallengeProgress` (fraction, readout, hint, solved),
so the same ring and readout serve all three.

- **Smile** holds are timed against the wall clock, not frame counts, so 3
  seconds means 3 seconds on a phone that is dropping frames.
- **Object hunt** labels below the pass threshold still drive the readout, so
  confidence visibly climbs as the user gets closer; the pass needs a streak of
  qualifying frames, not one lucky one.
- **Lumen** requires the target to be held, so sweeping a torch past the sensor
  does not end the alarm.

### Hunt targets are validated against the model

Every `HuntTarget` must be a label the bundled labeler can emit — a target
outside its vocabulary can never reach the threshold, so that alarm would ring
until the auto-silence timeout with no way to stop it. `HuntTargetLabelTest`
(instrumented) reads the vocabulary out of the model asset itself and fails if
any target is missing, so bumping the ML Kit version re-validates them.

### Nothing rings without a way out

`resolveChallenge` degrades to a challenge the hardware supports when the
configured one is impossible (no front camera, no light sensor), and the ringing
screen says which substitution it made. If the camera is refused, a second
option switches to a sensor challenge. When a device can run no detector at all,
the screen offers a plain dismiss — an alarm nobody can stop is a worse failure
than a skipped challenge.

Camera analysis runs on its own executor with `KEEP_ONLY_LATEST` backpressure,
and every ML Kit client is closed when the challenge screen goes away.

## The rest of the clock

A floating dock over the bottom of the screen switches between the alarm list and
three tools:

- **Timer** — countdown with presets.
- **Stopwatch** — hundredths, with laps showing both split and total.
- **Pomodoro** — 25/5 focus blocks, a long break every fourth.

All three derive their reading from the monotonic clock rather than accumulating
ticks, so a dropped frame cannot make them run slow.

They live in `ToolsEngine`, owned by the application rather than by a screen, so
leaving the app no longer throws away a running stopwatch. `ToolsService` renders
that state as an ongoing notification with its controls attached — pause, resume,
lap, skip — and holds no state of its own, which is what stops the notification
and the screen disagreeing about whether something is paused. It stops itself the
moment nothing is running.

They are still not alarms: an alarm goes through `AlarmManager` so it survives a
dozing phone, and blurring that line would get someone to trust a countdown to
wake them.

The timer takes an exact `MM : SS` as well as presets, and the pomodoro's focus,
break, long break and cadence are all settable.

## Quick actions and the assistant

Long-pressing the launcher icon offers **create alarm, start timer, start
stopwatch, start focus**, each landing directly on the thing named.

IRIS also answers the standard clock intents — `SET_ALARM`, `SET_TIMER`,
`SHOW_ALARMS`, `SHOW_TIMERS` — so "Hey Google, set a timer for five minutes"
offers it alongside the phone's own clock, arriving with the duration already
loaded and running.

One honest limit: **Android has no "default alarm app" role to claim.** Unlike
the browser or the SMS app, there is no setting that makes one clock app the
system's. The chooser appears because more than one installed app answers those
intents, and picking "always" is the user's own per-intent default.

## Creating an alarm

Three steps rather than one long form: **when**, **how you'll stop it**, then the
details. The single form buried the challenge — the one choice that actually
distinguishes IRIS — under sound and vibration toggles, and left the anchor
capture floating in a row of its own instead of belonging to the choice that
needs it.

Each challenge card animates when selected, and the motion is specific to what it
does: the smile breathes, the target sweeps like a scanner, the light glows. Each
also says what it costs you at 6am, which is the real difference between them —
a smile can be done in bed, an anchor cannot.

## Trying a challenge before you trust it

The challenge step has a **TRY IT NOW** card that runs the chosen challenge
immediately, against the real detector, in the real screen — `ChallengeScreen`
with `practice = true`, which skips the splash, adds a visible way out, and does
not stop any alarm. A practice mode that exercised a different code path would
prove nothing, so it exercises the same one.

This exists because the interesting questions about every challenge are ones no
amount of description can answer. Can ML Kit find your face in your bedroom's
light? Does it recognise the objects in *your* kitchen? Is the spot you captured
actually matchable from where you will be standing? Thirty seconds here beats
finding out at 6am with the alarm going. A rehearsal that succeeds stays on
screen rather than closing after 900ms — the whole point of running one is to see
that it worked.

## Setting the time

A clock face: minutes on the outer ring, hours on the inner one, the time and
AM/PM in the middle. Both rings are draggable *and* tappable, with hands and
knobs pointing at the current values.

- The minute ring scrubs **a minute at a time**, not in fives, so exact times are
  reachable without a second control. Labels appear every five and the nearest
  one lights up.
- The ring you grab **keeps the gesture** until you lift, so a sloppy arc cannot
  jump to the other ring halfway round.
- The centre is dead to the dial, so tapping AM/PM never reads as a time change.
- Hands settle with a spring rather than gliding — a knob you let go of should
  come to rest.

`RadialMath` holds the angle maths (`turns` clockwise from 12 o'clock, and the
conversions onto each ring) outside the composable, so the wrap cases that
usually harbour off-by-ones are unit tested: a full turn is 0 and not 60, the top
of a 12-hour ring reads 12 and not 0, and every minute is reachable.

A new alarm opens at the current time; editing one opens at its own time.

The dial's gesture handlers are deliberately not keyed on the time — re-installing
them mid-drag would drop the gesture — so the running handler keeps the closure it
was created with. That made an earlier build read `hour` as it was when the
handler was installed: setting the hour and then dragging the minutes reported the
*old* hour alongside the new minute, and the hour silently snapped back. Both
values are now read through `rememberUpdatedState`, so the handler always reports
the time as it currently is.

## Not yet verified on hardware

Everything here is compile-verified and covered by the tests above, but no part
of it has run on a phone. The things that can only fail on a device:

- Whether the full-screen intent actually draws over a secured keyguard. Several
  OEMs (Xiaomi, Samsung and others) gate this behind extra per-app permissions.
- Whether the direct activity start from the service is accepted on an unlocked
  phone once "display over other apps" is granted. The notification is the
  fallback either way, but the fallback is the behaviour this change exists to
  replace.
- ML Kit smile probabilities in a dark bedroom at 6am — plausibly the hardest
  real-world case this app has.
- Whether the alarm is audible in practice, and whether raising the stream
  volume is refused by the Do Not Disturb policy in use.

## Next steps

The feature set is complete. What is left is a device pass against the list
above, and then judgement calls that want real use to answer:

- Whether the object-hunt vocabulary should grow beyond five targets, and which
  ones are findable in a bedroom at 6am.
- Whether the wake check should escalate — a second check, or a longer challenge
  — for someone who keeps going back to sleep.
