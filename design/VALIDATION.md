# Host validation

## 0.1.0-preview2 candidate

The candidate combines the Miuix settings App with the startup repair documented
in [STARTUP_SAFETY.md](STARTUP_SAFETY.md). It has not been accepted on the phone.

- All 35 JVM tests passed: the original 25, two checks that every setting remains
  reachable in the new App, and eight plugin-discovery startup regressions.
- Debug and R8-minified release builds passed, including release lint-vital.
- The Java discovery callback, queued delivery and main-thread scheduler were
  inspected as compiled bytecode: none references Kotlin helpers. Keep rules
  preserve this boundary in the optimized APK.
- Miuix 0.9.4 uses the explicitly selected Kotlin/Compose compiler 2.4.20; AGP
  remains 9.2.1. Existing settings keys, JSON format, application ID and signing
  identity are retained.
- AndroidX's open-source graphics-path native library is now a transitive UI
  dependency. There is still no native Xposed entry or HyperChanger patch library.
- No GUI automation, screenshots, installation, scope reload or safe-mode change
  was used to verify this candidate. First-open plugin behavior and actual SystemUI
  startup remain mandatory acceptance items under the owner's control.

The App now uses feature navigation, Miuix preferences, numeric entry and optional
previews. Shared serialized settings work and main-thread observation preserve
pending writes across Activity recreation. Visual usability remains user-verified.

## 0.1.0-preview1 historical host checks

Validated on 2026-11-12 against the extracted HyperChanger base `5d89372`.

## Scope and build

- Main-source files reduced from 632 to 31, counting Kotlin, Java and native source/header files.
- No launcher, embedded HMC, iOS notification-center replacement, native patch library,
  camera, rear-display, OCR or per-application navigation hooks remain.
- One module entry and four scopes: SystemUI, its plugin, Personal Assistant and SecurityCenter.
- Both debug and R8-minified release APKs built successfully using JDK 21, Gradle 9.6.1,
  Android Gradle Plugin 9.2.1 and Android SDK/build tools 37.
- Debug APK signature verified. Release is unsigned; no release was published.
- APK metadata, scope list, entry name and absence of native libraries were verified
  in both variants. R8 retains the module, Application and Activity entry names.
- `git diff --check` passed. Phone dumps, decompiled trees, credentials, screenshots
  and build intermediates are not part of the tracked source.

Reproduce in the maintainer environment:

```sh
tools/build.sh :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease
```

Build artifacts and reports are under `/Volumes/Harbor/DevCache/hyperchange-lite/build`.
Platform status/navigation-bar styling APIs produce deprecation warnings; they did
not fail compilation or the release lint-vital check.

## Automated checks

All 25 JVM tests passed with no failures, errors or skips:

| Area | Tests | What is covered |
| --- | ---: | --- |
| Soft-glass arithmetic | 4 | Relative tuning, neutral behavior and bounded material inputs |
| Notification motion | 7 | Curve endpoints, spring behavior, tilt and grouped-card transforms |
| Volume motion | 6 | Pop semantics, drag symmetry, icon transition, retargeting and numerical stability |
| Radius baseline | 2 | Nested/repeated setter output and native baseline restoration |
| Preset codec | 4 | Round-trip, type/range validation and rejecting an invalid document before mutation |
| Complete catalog | 2 | All exposed settings round-trip and behavior gates default off |

The volume regression includes a 200 ms, damping 0.5 pop at 30 fps and 64 ms
frame stalls. The integration step satisfies the damped-system stability bound.

## Remaining acceptance

These checks establish buildability and tested arithmetic, not OEM compatibility
or visual parity. No phone was installed, reconfigured, restarted or rebooted for
this implementation. The next device trial must cover class/method discovery,
material sampling, notification lifecycle and gesture priority, foldable layouts,
volume expansion and restoring native behavior when each feature is disabled.

The interactive preview shares material tuning and motion functions, with an
explicit fallback when native OEM glass is unavailable. Notification animation
blur has a further baseline-restoration guard described in [RECOVERY.md](RECOVERY.md).
Ownership candidates remain separate in [DEFERRED.md](DEFERRED.md).
