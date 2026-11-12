# Host validation

## Unified material editor source branch

- `LITE_DEV_CACHE=/Volumes/Harbor/DevCache/hyperchange-lite-material-editor
  ./tools/build.sh :app:testDebugUnitTest :app:compileDebugKotlin` passed.
  All 80 tests in 15 suites passed, with zero failures, errors or skips. New
  checks cover unique material ownership, behavior separation, scene ranges,
  independent Assistant/sidebar switches and enabled geometry overrides.
- Build, project cache, Kotlin state and temporary output were isolated from the
  main checkout. No delivery APK was assembled, versioned or archived. The
  existing Preview 4 package version remains unchanged for this source branch.
- Source review confirms one material destination, one preview outside the
  scrolling settings list at every width, integrated preset/undo controls, and
  direct access to all controls in the selected material category. Existing
  validation, migration, reset and import/export consumers are unchanged.
- Preview coverage includes six Control Center corner samples and a new shade
  scene. Native setup still uses the installed normal-notification recipe; its
  caption distinguishes this sample from actual OEM scene recipes and SystemUI
  scope activation. Fallback approximates shade blur/scale and background mix;
  it cannot reproduce all glass/refraction/light parameters.
- No phone installation, GUI operation, screenshots, settings changes or scope
  reload were performed. Narrow-screen and landscape inner-display appearance,
  scroll pinning and native A/E material comparison remain visual acceptance.

## 0.1.0-preview4 material presets

- All 73 JVM tests passed: the previous 64, five built-in recipe/backup checks and
  four current-material refresh checks. Recipe IDs, names and all 18 numeric
  values match the six upstream A-F fixtures. Profile application and the trial
  backup are limited to 19 material keys, including the master toggle.
- Debug and R8-minified Release builds passed, including release lint-vital.
- A first-enable source defect was corrected: admitted ordinary notification and
  media roots now come from existing OEM binders even if tuning was disabled
  when their setters ran. Active feature/range checks and queue tokens still
  apply; configuration/scene stamps deduplicate the two refresh entry paths.
- The App preview now samples its own content using the established OEM local
  path, reports required native-call success versus schematic fallback visibly,
  and offers a vector contrast chart. Its status does not establish GPU raster
  output or a loaded SystemUI hook.
- The preset page's master toggle can compare tuned and untuned sample inputs.
  Other preview pages retain their independent parameter-preview behavior.
- Device access checked only the installed version before delivery; no GUI,
  screenshots, settings changes, installation or scope reload were used for host
  verification. Actual App/system A/E visual comparison is owner acceptance.
- Signed preview 4 was archived in `releases/local/` and the personal Miduo
  folder, then copied to phone Download with matching SHA-256. Transfer-only
  temporary files were removed; previous APK versions remain intact. The owner
  retains manual installation and scope activation.

## 0.1.0-preview3 compatibility candidate

This is the Lite half of [HMC coexistence](HMC_COMPATIBILITY.md). The HMC
compatibility work is separate; no fixed-version pair has passed device acceptance.

- All 64 JVM tests passed: the previous 35 plus 11 settings/migration, nine
  material-range/release, four scene/role and five animation-ownership checks.
  They cover tuning off with unification on, explicit new values, legacy and
  fresh configuration, first service binding, off/on and scene/role round trips,
  interrupted release delivery, an enabled refresh cancelled by immediate disable,
  newer writes and reentrant completion ordering.
- Debug and R8-minified Release builds passed, including release lint-vital.
  The delivered APK uses the existing debug signing identity and version code 3;
  the unsigned Release output is retained only as a build check.
- Canonical reset values now use the same validation/step quantization as export,
  import and publication. The initial round-trip test exposed seven pre-existing
  one-Float-ULP differences; exact canonical round trips pass after correction.
- The native notification selector, media flow and controller contracts were
  inspected in the actual SystemUI artifact. A read-only device version/hash
  check confirmed the offline APK is identical. No GUI, screenshots, settings
  changes, installation or scope reload was used for these checks.
- Startup Java discovery/guard code is unchanged, and its eight regression tests
  still pass. Host checks do not replace independent startup acceptance.
- New `glass_media` and `notification_lockscreen_effects` defaults are false;
  existing settings migrate to their former behavior. An upgrade alone does not
  select the HMC coexistence configuration.
- The signed debug APK was archived as `hyperchange-lite-0.1.0-preview3.apk` in
  `releases/local/` and the owner's existing Miduo folder, then copied to the
  phone's Download folder with matching SHA-256. The owner retains manual
  installation and scope control. Transfer-only temporary files were removed;
  all prior preview APKs remain intact.

Actual first unlock repaint, AOD deferral, fold-display changes, current media
collector delivery and Folme channel cleanup remain device acceptance items.
The local tests exercise pure decisions/lifecycles, not an ART/SystemUI instance.

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
