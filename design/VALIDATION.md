# Host validation

## 0.1.10 experimental banner text readability

- Live device check: the owner supplied a real heads-up notification after
  authentication. The native helper reported supported sampling, three accepted
  scalar samples across bounded bursts and a valid median brightness. The
  banner's exit reported native restoration, with no active sampler. Disabling
  the setting restored its original false value; sampler/session state remained
  inactive. The temporary local test notifications, channel and device helper
  were removed. No screenshot, full reboot, outbound message or credential
  input was used. Perceived text contrast, final refraction and span appearance
  remain visual acceptance; these diagnostics do not measure a WCAG ratio.
- The complete JVM suite passes 324 tests in 56 suites, without failures,
  errors or skips; Debug lint and `git diff --check` pass. The delivery package
  is assembled separately from clean committed, pushed source with the retained
  signing identity. These host results do not establish live sampling or visuals.
- New tests cover the exact default-off setting, partial imports, reset and
  round-trip persistence, sampling budgets, native-only text admission, separate
  primary/secondary colors, hysteresis, cancellation and owned-value restoration.
  The targeted run passes 64 tests with main Kotlin/Java compilation.
- The implementation uses the retained firmware's native compositor helper with
  a required valid stop surface. Sampling is event-driven, bounded to 192 x 48
  pixels for 250 ms, with at least 750 ms between bursts and no idle heartbeat.
- The native helper's live callback, standard RemoteViews span rendering and
  perceived contrast require device verification. Scalar underlay brightness is
  not a measured final glass contrast ratio. Unknown/opaque materials or
  unavailable APIs keep native ink and use only a small reversible shadow.
- The read-only `hc-lite-banner` dump is independent of `hc-lite-material`.
  No notification text, bitmap, App identity or credential is recorded.

## 0.1.9 ordinary lockscreen material and native-busy recovery

- The complete JVM suite passes 314 tests in 54 suites with no failures, errors
  or skips. Debug lint, Debug assembly and `git diff --check` pass. Delivery uses
  `tools/build.sh` with `LITE_GRADLE_MAX_WORKERS=2`; caches, build outputs and
  diagnostic logs remain in Harbor DevCache. The final versioned package is
  rebuilt with `clean` from committed, pushed source. Existing non-blocking
  platform/Gradle warnings remain.
- The native full-AOD listener refreshes notification/media style before clearing
  its busy flag, and cancellation clears the flag without publishing a scene.
  A material refresh refused during that interval previously lost its remaining
  trigger. Four new tests cover weak owner-bound deferral, deduplication, one-time
  consumption, replacement ownership and clearing a completed request. Only
  actually refused attached roots still bound to the completing controller enter
  the existing current-scene queue. There is no polling, new collector, native
  flag mutation or replay of a captured effect.
- Ordinary rows previously retained the native keyguard base recipe even when
  unification normalized media/live cards. Its untouched base slots materially
  differ from the normal recipe, so a changed setter did not imply player parity.
  Two new tests cover the ordinary/unify/range/scene/heads-up matrix and disabled
  or unknown-keyguard behavior. The settled lit ordinary path now resolves the
  normal recipe and blends from the current native `getFixUiModeContext` only
  after both scope and exact native-row keyguard/nonbusy state permit it.
- Full normal-effect replay remains limited to the existing media/focus path.
  Ordinary rows retain their current native keyguard lifecycle and AOD effect;
  unification off, heads-up, unknown/AOD/native-busy states retain their existing
  base selection. Material and default-off animation permissions remain separate.
  Pinned clock/navigation/unlock/lyric/sampling flicker issues are not adjusted.
- The explicit read-only dump now separates ORDINARY, MEDIA and HEADS_UP glass
  setter calls, changed arguments and denials, reads the last native row effect
  only on demand, and counts successful ordinary normal-base selections. It
  includes no notification text, IDs or credentials. Setter changes, selector
  return and an admitted media event are not GPU or visual acceptance.
- Prior read-only phone evidence supplied during diagnosis confirmed settled
  lit keyguard, allowed ordinary material and a returned native selector, rather
  than relying on media success to infer ordinary success. The packaging task
  itself does not access or change the phone. The coordinating root agent owns
  subsequent archived installation, related scope reload and device tests under
  the owner's standing authorization; no whole-phone reboot is permitted.
- The archive workflow verifies the existing signing certificate, package/version,
  clean pushed source against the embedded revision, and equal retained/personal
  APK SHA-256 hashes. Formal production signing remains unconfigured. Personal
  archive output is APK-only; receipts and machine evidence stay in the project
  or DevCache. No settings/data reset or previous APK cleanup is part of this build.

## 0.1.8 native fill boundary, lockscreen material identity and App polish

- The complete JVM suite passes 308 tests in 53 suites with no failures, errors
  or skips. Debug lint, Debug assembly and `git diff --check` pass. Builds use
  `tools/build.sh` with `LITE_GRADLE_MAX_WORKERS=2`; outputs stay in Harbor
  DevCache. Existing non-blocking platform warnings remain.
- Slider track customization no longer overwrites native progress clipping or
  the secondary volume progress getter. Tests cover flat-boundary ownership,
  independent native restoration and fresh resource/qualifier values. The
  previous version incorrectly applied one configured radius to both channels.
- The retained native material binder uses `headUpState` and
  `hasCalledHUNDragDownDisappear`, whereas the display getter also includes an
  old disappearance-animation flag. New tests exercise an animation flag that
  stays true after both native material flows become false, live changes to
  either flow, unsupported metadata and a transient read failure. Material now
  uses native identity while animation retains its own display classification.
- Media material carries scene epochs, so a native AOD deferral is not treated
  as a completed refresh after waking. Existing collectors are reused, without
  another `bind()` or a forced ordinary keyguard recipe during AOD. Material and
  default-off animation permissions, current native ownership and cleanup
  remain separate. See [the complete trace](LOCKSCREEN_SCOPES.md).
- The optional `hc-lite-material` SystemUIService dump reports only observed
  scope flags, binder/refresh status and material-setter counts. It changes no
  settings, publishes no scene and triggers no material refresh. A selector
  return or a changed View argument is not asserted as a successful GPU commit.
- App appearance/backup coverage includes the compatible liquid-navigation
  field and the existing theme/language/navigation/blur data. The About page
  has a collapsing animated colored header; the floating navigation removes
  the extra opaque container and supports its selected liquid/blur effect.
  Behavioural credits remain distinct from intact required licence notices.
- Read-only prior phone preferences/module logs confirmed enabled ranges and
  installed SystemUI material hooks. They do not prove visual acceptance or
  establish every affected row's runtime state. This delivery performs no
  phone access, installation, scope reload, settings mutation, GUI operation,
  screenshot or reboot. The next owner check covers actual lockscreen material,
  slider fill edges and App appearance.
- The versioned Debug APK is built from clean committed/pushed source and
  verified against its embedded source revision, existing certificate and
  archive hashes. Formal production signing remains unconfigured. Package,
  preference group, existing saved controls and app data are not reset.
- Pull-down backdrop zoom remains unimplemented; no unrelated whole-background
  behavior or suspended notification grouping is restored.

## 0.1.7 lockscreen scope, slider corners and App appearance

- The full JVM suite passes 302 tests in 52 suites with no failures/errors/skips;
  Debug lint and `git diff --check` pass. Existing non-blocking platform warnings remain.
- Material and animation opt-ins are independent. The new animation key defaults
  false and is excluded from legacy-scope inference/completeness. Tests cover
  screen/wake/scene transitions, repeated no-op lifecycle calls, ordinary and
  special roles, animation cleanup and material-only imports.
- Plain keyguard rows use the current native selector's keyguard/light-wallpaper
  glass effect and actual Context, guarded by settled Screen ON/AWAKE state.
  Current source/epoch/write ownership governs clearing; an AOD selector early
  return does not discard pending responsibility. Existing AOD selection remains
  native. Source/selected Kotlin integration and focused cases precede the full
  Android build; they do not prove phone rendering.
- Slider checks cover independent track/fill baselines, resource replacement,
  fresh same-value native inputs and restoration across a real 24dp/48dp
  qualifier transition. Exact retained methods supply lifecycle hooks, without
  onDraw/touch interception. Runtime defaults now follow the displayed specs.
- Theme/appearance backup accepts old two-field metadata and six theme modes,
  navigation and App-local blur. All presets/defaults lie inside their visible
  slider windows; the legal limits/steps and preset values remain unchanged.
  Source checks cover localized visible UI, companion links and intact legal
  assets. Functional pages use generic module descriptions while credit/licence
  pages preserve attribution; online-service names still identify API sources.
- No phone connection, GUI, screenshot, install, reload, tuning or reboot is used.
  The release is built from committed/pushed source and retained with the existing
  signing identity. Native GPU sampling, lockscreen cleanup, theme/layout comfort
  and actual slider outlines remain deferred owner acceptance.
- Pull-down backdrop zoom is not shipped. Tracing the plausible depth/zoom flow
  to its final consumer showed a keyguard-root transform, not backdrop zoom; no
  fake preview slider or retired sampling-ratio key is exposed as that feature.

## 0.1.6 real background channels, readable settings and banner glass

- All 281 JVM tests in 49 suites pass with no failures, errors or skips.
  `:app:lintDebug` and `git diff --check` pass. Existing non-blocking platform
  warnings remain. Android compilation includes the actual Miuix UI, Xposed and
  OEM reflection adapters; focused stubs are not the final integration result.
- The retained native controllers establish the real compositor radius and
  separate sampler/blend/solid channels. The expanded, unlocked-panel gate remains
  strict. Only actual changed writes gain ownership; current native owners
  reclaim exact View/window/surface channels on release, without saved-scene
  replay or an extra full refresh when the master is off. Tests cover same-value
  native supersession, window/surface replacement, detach, cancelled delivery,
  reentry/once consumption and valid-surface checks.
- Banner tint consumes the card-opacity control. The independent banner-glass
  opt-in preserves upstream behavior and requires all four permissions; default
  off retains OEM style. Its preview now distinguishes the verified native
  light/dark banner token from the opt-in normal-card recipe. The scheduler
  accessor resolves inherited interface default methods instead of silently
  falling back to native animations.
- UI tests cover every visible control/group/note, prerequisites, mirrored
  masters, default-collapsed advanced controls, independent backdrop routing and
  complete default/English/Chinese text. Numeric controls retain their ranges,
  exact input and units. Existing 111 setting keys/defaults and six preset values
  were compared; one new opt-in is added without renaming the old schema.
- A frozen 107-setting pre-separation fixture checks full old-backup migration
  without the new banner key. A new-only partial edit does not acquire legacy
  media/lockscreen scopes or an implicit whole-background edit. Backup, reset,
  local/remote binding and material-only preset coverage remain in the full suite.
- The About donation entry embeds the owner's unchanged raw PNG and supports
  background MediaStore saving with pending-row failure cleanup. Both maintained
  apps' source asset hashes match the supplied image; final APK verification also
  checks the embedded raw bytes. Actual gallery saving/QR recognition remains
  owner acceptance.
- No phone GUI, screenshot, installation, scope restart, device-setting edit or
  reboot was used. Read-only state/source evidence is separate from visual
  acceptance. The final signed package is retained from the clean pushed source;
  the optimized Release check and source/signature/hash checks use the archive
  workflow. APKs may be copied to Download without installing them.


## 0.1.4 independent layers and settings usability

- All 210 JVM tests in 36 suites pass with no failures, errors or skips.
  `:app:lintDebug` passes; existing non-blocking platform/compatibility warnings
  remain. `git diff --check` passes.
- Migration checks cover fresh/local/remote state, complete legacy exports,
  card-only presets, whole-only partial imports with/without migration metadata,
  reset and backup round trips. Card recipes/undo do not alter backdrop values
  or its independent enable flag. Refresh masks keep native setter channels apart.
- Native preview routing uses the independently enabled shade settings, including
  OEM blend-color initialization. Unsupported OEM calls retain the schematic
  fallback; successful setup is not proof of GPU appearance or loaded hooks.
- Volume tests cover first-show wide state, real level changes versus redundant
  frames, touch/release, delayed attachment, native width changes and the separate
  mute/moon footer geometry. Exact OEM event declarations were checked in the
  existing SystemUI artifact; the old endpoint-only key animation was insufficient.
- UI checks cover complete resource labels and format signatures, App appearance
  backup validation, deferred-setting exclusion, actual-edit-only Control Center
  preview selection and stable preview identity across scroll changes.
- Notification grouping/fold-history hooks and group source are retired from the
  active tree, with the suspected Noti conflict recorded rather than repaired.
- No phone GUI, screenshots, installation, settings writes, scope reload or reboot
  was performed. Owner acceptance is limited to the changed material channels,
  editor behavior/language, real volume phases and launcher appearance.
  Final signed/optimized package checks are recorded by the archive workflow.

## 0.1.3 heads-up and whole-background isolation

- The owner reproduced the unwanted foreground-App blur/dim, then confirmed that
  disabling only **下拉背景** stopped it. This A/B result identifies the full
  backdrop range; the exact radius/blend setter was not captured on the phone.
- All 170 JVM tests in 30 suites passed with no failures, errors or skips;
  `:app:lintDebug` and `git diff --check` passed. New checks cover actual panel
  versus pinned heads-up state, unknown/AOD/locked inputs, per-root/kind ownership,
  replacement/detach/stop/reopen, and native-cleanup versus stale replay dispatch.
- The retained SystemUI/CC contracts were checked read-only, including exact
  notification callback declarations in DEX. The window-expanded boolean includes
  heads-up and is unsuitable for this boundary. The new scope requires real
  positive panel expansion and confirmed unlocked/non-AOD state.
- Background baselines now retire across scope epochs. Both ordinary and legacy
  setter paths reject stale module replay, while native cleanup remains intact.
  Prepared writes validate after OEM callbacks, preventing late cache recreation.
- Android GPU output and the new loaded hook have not been accepted on the phone.
  No GUI, screenshots, installation, device settings changes, scope reload or reboot
  was used. The owner's diagnostic A/B is separate from APK acceptance.
- See [shade background scope](SHADE_BACKGROUND_SCOPE.md) for evidence, ownership
  decisions and the short owner-operated tests. Final packaging and retained APK
  identity are verified by the committed-source archive workflow.

## 0.1.2 editor, volume and reference review

- All 156 JVM tests in 28 suites passed, with no failures, errors or skips.
  Added coverage includes first-show/key/touch volume phases, independent native
  width restoration, zero-gap and interpolated edge placement, legacy preset and
  first-binding migration, integer stack spacing, heads-up delay and blur timing.
- `:app:lintDebug` and `git diff --check` passed. Final signed Debug and optimized
  Release packages are built from the committed source; local archival receipts
  bind each retained APK to its source revision and signing identity.
- Material, notification and volume editors use native Miuix scrollable project
  tabs under the pinned rendered sample. Numeric controls keep exact input and
  validated import/export. The visible track gap uses physical pixels; only its
  narrow phase approaches the edge, leaving native wide placement intact.
- The first-show error came from narrowing during show/refresh. A shared session
  model now distinguishes showing from subsequent hardware-key updates. Native
  slider touch handling remains intact; automatic footer hiding follows the
  actual narrow phase. Existing offset presets migrate to the explicit edge gap.
- HyperLight source review corrected nested press arbitration, minimum flip/blur
  exit delays, integer spacing, positive-half-up blur quantization, heads-up blur
  throttling, and the missing OEM add/remove property-update dispatch. Intentional
  ownership/stability protections and historical defaults are documented in
  [the comparison](HYPERLIGHT_PARITY.md).
- HyperChanger's parameter body and all six recipes match the pinned upstream.
  Native preview setup now seeds normal-notification blend colors and removes
  the previous extra gradient/stroke. Scope and sampling differences remain
  explicit in [the glass review](HYPERCHANGER_GLASS_PARITY.md).
- No phone GUI, screenshots, settings changes, scope restarts or reboot were
  used. Source/host checks do not establish device rendering, OEM first-key
  callback ordering or edge clipping/touch accessibility. Acceptance is limited
  to this round: direct tabs and auto-preview; first/repeated/touch volume phases
  with edge gap 0/6; original glass preset comparison in App and a live surface.

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

Current reproduction puts build artifacts and reports under
`/Volumes/Harbor/DevCache/hc-lite/build`.
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

## Preview 5 performance candidate

Preview 5 preserves Preview 4 presets and addresses material classification CPU
cost. All 96 tests in 18 suites passed; Debug, R8 Release and vital lint passed.
See [AOD performance](AOD_PERFORMANCE.md). The paired candidate is HMC Preview 17
with Lite Preview 5; post-install performance comparison remains pending. The
material-editor UI worktree is intentionally excluded from this functional build.
