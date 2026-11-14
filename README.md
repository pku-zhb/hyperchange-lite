# hc-lite

A focused fork of [HyperChanger](https://github.com/ColdP/HyperChanger), maintained
at [pku-zhb/hc-lite](https://github.com/pku-zhb/hc-lite). Package:
`com.zhuhuibin.hyperchange.lite`.

Version 0.1.10 adds the default-off **横幅通知可读性优化（实验性功能）** switch.
It requests brief, bounded native compositor brightness samples behind the
visible banner and adapts standard title/body text with separate roles and
hysteresis. The banner's own surface is excluded; there are no screenshots,
retained pixels or permanent sampling timer. Native colors, spans and shadows
are restored on exit. Unknown sampling/material contracts retain native text
colors with a small contrast shadow. See [implementation and limits](design/BANNER_READABILITY.md)
and [validation](design/VALIDATION.md). Material and animation settings remain independent.

Version 0.1.9 restores ordinary lockscreen material after the native AOD
controller finishes a previously refused refresh. With material unification
enabled, settled lit lockscreen notifications now use the normal base recipe
from their current native day/night context, alongside media and live cards.
The native ordinary-row effect still owns its lifecycle and AOD selection;
material permission does not grant animation permission. Read-only diagnostics
separate ordinary, media and heads-up setter results and expose the latest
native row effect. The full host suite passes 314 tests, Debug lint and assembly.
See [lockscreen material tracing](design/LOCKSCREEN_SCOPES.md) and
[host validation](design/VALIDATION.md). Native appearance, actual parameter
response and scope reload behavior still require device acceptance. Existing
package, signing identity, saved settings and backup compatibility are retained.

Version 0.1.8 restores the native flat brightness/volume fill boundary while
keeping the enclosing track corners adjustable. Lockscreen notification material
now follows the native binder's live heads-up flows rather than a lingering
disappearance-animation flag; media material also refreshes after keyguard/AOD
scene changes. Material and default-off animation permissions remain separate.
An explicit read-only material dump distinguishes scope, binder and setter
results. The App restores a collapsing animated About header, colored artwork
and text, liquid navigation and floating-background blur without a second
opaque navigation layer. Existing settings and backup compatibility are retained.
See [lockscreen material tracing](design/LOCKSCREEN_SCOPES.md),
[slider corners](design/CONTROL_SLIDER_RADIUS.md) and
[host validation](design/VALIDATION.md). The existing Debug signing identity is
retained; formal production signing is not configured. Phone appearance and
transition behavior remain owner acceptance; this delivery does not install or
reload any phone scope.

Version 0.1.7 separates lockscreen notification material from default-off
lockscreen animations. Plain native lockscreen rows can acquire their current
keyguard glass recipe; settled screen/wake state and current native ownership
govern acquisition and release. Control-center sliders now cover independent
track/fill corners and native resource replacements, including fold qualifiers.
The App adds six theme modes, floating navigation, App-local blur, a complete
About/credits/offline-licence structure and a companion link. Material slider
windows include all supplied presets while retaining legal import ranges.
See [lockscreen scopes](design/LOCKSCREEN_SCOPES.md),
[slider corners](design/CONTROL_SLIDER_RADIUS.md) and
[host checks](design/VALIDATION.md). Pull-down background zoom remains
[unimplemented](design/PULLDOWN_ZOOM.md): the verified candidate scales keyguard
content, not the requested backdrop. This release uses no device connection.

Version 0.1.6 targets the actual whole-window compositor and native backdrop
controllers, with independent blur and dimming controls and a full-panel preview.
Card glass remains separate. Exact current native writers restore owned backdrop
changes at the shade/heads-up handoff instead of replaying a previous scene.
Settings now group scopes, hide inactive children, fold advanced optics/curves,
show necessary prerequisites and baselines, and connect local preview wallpapers.
The optional **顶部通知柔光玻璃** switch restores upstream's separate banner-glass
style; it defaults off and requires card tuning plus the notification range.
Native banner tint tuning and scene-specific previews are corrected too. Existing
setting keys, defaults and preset values remain upgrade-compatible. See
[settings presentation](design/SETTINGS_UX.md),
[whole-window ownership](design/WHOLE_WINDOW_BACKDROP_016.md) and
[banner materials](design/BANNER_MATERIAL.md). About also provides the owner's
WeChat donation image with gallery saving. GPU rendering still needs owner acceptance.

Version 0.1.5 removes generated-view-ID exception storms, coalesces slider writes,
reads each material snapshot once, and keeps the current volume shape through its
native dismiss animation. Whole-screen tint adjusts mode 106 while preserving
structural/unknown blend modes; zero extra zoom retains its native meaning.
Scopes no longer show an unrelated preview. Launcher art directly identifies the
module's function. See [implementation evidence](design/MATERIAL_PERFORMANCE_015.md).
Device rendering and cold-entry smoothness remain owner acceptance.

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
