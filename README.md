# hc-lite

A focused fork of [HyperChanger](https://github.com/ColdP/HyperChanger), maintained
at [pku-zhb/hc-lite](https://github.com/pku-zhb/hc-lite). Package:
`com.zhuhuibin.hyperchange.lite`.

Version 0.1.4 separates card materials from the whole notification/Control Center
backdrop, including independent values, enable flags, migration and refresh.
The App now has Home, Settings and About pages, compact peer controls, separate
material/corner destinations, and native system/light/dark and Chinese/English
preferences. Notification grouping is suspended. Ordinary hardware-volume
updates now enter the narrow phase, with an accurate two-button preview.
Both maintained Apps share a restrained vector icon family.
See [independent layers](design/MATERIAL_LAYERS.md) and the
[current host checks](design/VALIDATION.md); phone rendering remains acceptance.

Version 0.1.3 isolates whole-background tuning from heads-up banners and retires
closed-panel background snapshots. It requires a real expanded panel in a
confirmed unlocked/non-AOD scene; unknown/locked scenes retain native backgrounds.
See [shade ownership](design/SHADE_BACKGROUND_SCOPE.md) for the confirmed input
path, retained OEM contracts and owner-operated acceptance.

Version 0.1.2 adds single-tap scrollable editor tabs and corrects the borrowed
first-show/repeated-key volume behavior. The visible volume track has an explicit
physical-pixel edge gap (6 px by default; 0 is flush). Existing package, signing
identity, preference group and configuration format remain upgrade-compatible.
See [HyperLight behavior review](design/HYPERLIGHT_PARITY.md) for recovered
contracts, corrected differences, retained defaults and device-test boundaries.
The [HyperChanger glass comparison](design/HYPERCHANGER_GLASS_PARITY.md) records
matching core math/recipes and remaining scope/sampling differences. The native
App preview now uses OEM blend colors without a second painted veil.
Earlier preview versions and their archived filenames remain historical records.

The retained scope is native soft-glass tuning, notification materials and
interactions, Control Center corners, volume controls, Personal Assistant widgets
and the SecurityCenter sidebar. Local interactive previews use the same tuning and
motion models. OEM material support is identified separately from a visual fallback.

This fork does not bundle a launcher, music/lyrics engine, iOS notification-center
replacement, camera module, third-party navigation adaptations, OCR, or prebuilt
native patch library. HMC Duo remains the owner of its lockscreen content.
See [deferred ownership](design/DEFERRED.md), [recovery boundaries](design/RECOVERY.md)
and [host validation](design/VALIDATION.md).
The settings App uses [Miuix](https://github.com/compose-miuix-ui/miuix). Material
editing has one entry with a pinned top preview; notification and volume behavior
have separate pages. See the [App design](design/APP.md).

Preview 1 had a plugin-discovery startup regression. Preview 2 contains a guarded,
deferred discovery repair; read the [startup recovery boundary](design/STARTUP_SAFETY.md)
before any device scope trial. Host verification does not establish device recovery.

Preview 3 separates notification music material from ordinary notifications and
adds an ordinary-lockscreen effects range. Fresh installs/reset disable both new
ranges; upgrades preserve their old behavior until explicitly changed. Read
[HMC coexistence](design/HMC_COMPATIBILITY.md) for the supported configurations,
migration and paired acceptance boundary. The modules do not exchange settings
or require a shared service.

Preview 4 adds the six original HyperChanger material recipes under
the material editor's **预设** category, with A/E shortcuts, a material-only trial restore and a
contrast test background. Native-versus-schematic preview status is explicit;
existing bound notifications can refresh on first tuning enable without waiting
for a later OEM update. See [material comparisons](design/MATERIAL_PRESETS.md).


Preview 6 integrates the unified material editor and a usability/behavior review:
clearer task sections and dependency hints, smaller slider windows with one-step
and exact input, thin-volume companion-button handling, accurate disabled-effect
previews, and corrected overdamping. Existing values and scopes are preserved.
See [Preview 6 review](design/PREVIEW6_REVIEW.md). The normal-notification sample
is a local preview, not a guarantee of identical rendering for every OEM scene.

Preview 7 simplifies every feature page to one pinned, automatically focused
rendered sample and title-only controls. Notification group lifecycle repair is
included in the same iteration. See [Preview 7 review](design/PREVIEW7_REVIEW.md).

## Build

Requires JDK 21+, Android SDK/build tools 37 and libxposed API 102.
In the maintainer's environment:

```sh
tools/build.sh :app:testDebugUnitTest :app:assembleDebug
```

The script verifies the Harbor mount and places every build output and cache
outside the source tree. For another environment, set `JAVA_HOME`, `ANDROID_HOME`
and `LITE_BUILD_ROOT`, then use `./gradlew -I tools/gradle-init.gradle`.
Concurrent worktrees must set `LITE_DEV_CACHE` to their own directory under
`/Volumes/Harbor/DevCache/`; the default is `/Volumes/Harbor/DevCache/hc-lite`.
This isolates build, project, Kotlin and temporary
outputs while retaining the shared downloaded Gradle dependencies.

Before delivering an APK in the maintainer environment, run
`python3 tools/archive-apk.py`. It checks the APK identity and signature,
requires a clean source revision already pushed to main, checks the embedded
source identity, retains `hc-lite-<version>.apk` under `releases/local/`, and imports an identical copy
into the owner's existing `02 Personal Files/Miduo` archive. Different builds
cannot replace an archived version. Receipts retain the full commit as
`release_name`; both historical and current receipt names enforce signing and
version continuity. Receipts remain local to the project.

## Preview status

This is a source and host-validation preview. No phone installation or device
acceptance is implied. Features are individually configurable; new system behavior
starts disabled until selected. Presets cover all exposed settings and validate
the full document before changing state. The app never restarts hooked packages.

Enable only the four declared scopes when testing: SystemUI, its Control Center
plugin, Personal Assistant and SecurityCenter. No framework or launcher scope is
required. Do not enable equivalent HyperLight behavior at the same time.

## Attribution

Derived from HyperChanger commit `5d89372`, with original copyright and licensing
retained. Reconstructed effects use behavioral evidence rather than redistributed
HyperLight assets or binaries. This fork is distributed under AGPL-3.0; see
[LICENSE](LICENSE) and [NOTICE](NOTICE).
