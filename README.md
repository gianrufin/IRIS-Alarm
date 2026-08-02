# IRIS Alarm

An Android alarm clock with no snooze button. Every alarm is dismissed by doing
something in the real world — smiling into the front camera, hunting down a
physical object, or walking somewhere bright — evaluated entirely on-device.

## Status

Feature-complete for the core loop: set an alarm, it rings over the lock screen,
and the only way to silence it is to satisfy a camera or sensor challenge.
Unit tests cover the scheduling maths and the persistence round-trip
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
| Smile (front camera) | `smilingProbability > 0.8` and both eyes open `> 0.7`, held 3s |
| Object hunt (rear camera) | target label confidence `> 0.8` across 5 consecutive frames |
| Lumen | `> 500 lux` sustained for 1.5s |

## Settings

`SettingsScreen`, backed by DataStore:

| Setting | Default | Why |
| --- | --- | --- |
| Default challenge | Mirror Iris | Pre-selects the challenge for new alarms |
| Auto-silence | 10 min | How long an unsolved alarm rings before giving up |
| Volume ramp | 15 s | Fade in from near-silence, so the alarm wakes rather than startles |
| Minimum volume | 60% | Floor the alarm stream is raised to while ringing, then restored |

## Tests

```bash
./gradlew :app:testDebugUnitTest      # 17 tests, no device needed
./gradlew :app:connectedDebugAndroidTest   # needs a device or emulator
```

JVM tests cover the scheduling maths, the Room entity round-trip, and challenge
resolution across every missing-hardware combination — the pure logic was kept
free of Android types precisely so these stay fast.

Instrumented tests cover what only a real framework can answer: that scheduling
registers a broadcast `AlarmManager` can deliver and cancelling removes it, the
Room round-trip against real SQLite, that the challenge Activity renders and
survives a back press, and that every `HuntTarget` exists in the shipped model
vocabulary.

## Build

```bash
echo "sdk.dir=/path/to/android-sdk" > local.properties
./gradlew :app:assembleDebug
```

The debug APK is large (~100 MB) because the ML Kit models are bundled for
offline use; the release build shrinks with R8 (`isMinifyEnabled`).

## Screens

- **Dashboard** (`ui/dashboard`) — hero clock, time to the next alarm, one row
  per alarm with its challenge glyph and repeat summary, and an inline warning
  (tap to fix) when the OS has revoked exact alarms.
- **Editor** (`ui/editor`) — snapping hour/minute wheels at display type size,
  day chips, the three challenges as full-width options, hunt-target chips,
  label, system ringtone picker and vibration.
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

## Not yet verified on hardware

Everything here is compile-verified and covered by the tests above, but no part
of it has run on a phone. The things that can only fail on a device:

- Whether the full-screen intent actually draws over a secured keyguard. Several
  OEMs (Xiaomi, Samsung and others) gate this behind extra per-app permissions.
- ML Kit smile probabilities in a dark bedroom at 6am — plausibly the hardest
  real-world case this app has.
- Whether the alarm is audible in practice, and whether raising the stream
  volume is refused by the Do Not Disturb policy in use.

## Next steps

- Snooze-free is the point, but there is no "I am awake, stop for now" state for
  a user who solves the challenge and falls back asleep.
- Per-alarm overrides for the global volume and auto-silence settings.
