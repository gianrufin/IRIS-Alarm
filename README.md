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
- **Back is disabled** on the challenge screen (it sends the task to the back
  instead), so the alarm cannot be orphaned behind the lock screen.

## Challenge thresholds

All in `domain/model/VisionChallenge.kt` (`ChallengeThresholds`):

| Challenge | Passes when |
| --- | --- |
| Smile (front camera) | `smilingProbability > 0.8` and both eyes open `> 0.7`, held 3s |
| Object hunt (rear camera) | target label confidence `> 0.8` across 5 consecutive frames |
| Lumen | `> 500 lux` sustained for 1.5s |

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

Camera analysis runs on its own executor with `KEEP_ONLY_LATEST` backpressure,
and every ML Kit client is closed when the challenge screen goes away.

## Next steps

- Instrumented tests for the ringing flow (fire an alarm, assert the challenge
  Activity shows over the keyguard).
- A settings screen: default challenge, auto-silence duration, gradual volume
  ramp.
- Per-challenge fallbacks — the lux challenge already reports when a device has
  no light sensor, but nothing yet offers the user a different task.
