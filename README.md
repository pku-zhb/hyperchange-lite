# HyperChange Lite

A focused fork of [HyperChanger](https://github.com/ColdP/HyperChanger), maintained
by [pku-zhb](https://github.com/pku-zhb). Package: `com.zhuhuibin.hyperchange.lite`.

The retained scope is native soft-glass tuning, notification materials and
interactions, Control Center corners, volume controls, Personal Assistant widgets
and the SecurityCenter sidebar. Local interactive previews use the same tuning and
motion models. OEM material support is identified separately from a visual fallback.

This fork does not bundle a launcher, music/lyrics engine, iOS notification-center
replacement, camera module, third-party navigation adaptations, OCR, or prebuilt
native patch library. HMC Duo remains the owner of its lockscreen content.
See [deferred ownership](design/DEFERRED.md), [recovery boundaries](design/RECOVERY.md)
and [host validation](design/VALIDATION.md).

## Build

Requires JDK 21+, Android SDK/build tools 37 and libxposed API 102.
In the maintainer's environment:

```sh
tools/build.sh :app:testDebugUnitTest :app:assembleDebug
```

The script verifies the Harbor mount and places every build output and cache
outside the source tree. For another environment, set `JAVA_HOME`, `ANDROID_HOME`
and `LITE_BUILD_ROOT`, then use `./gradlew -I tools/gradle-init.gradle`.

Before delivering a preview in the maintainer environment, run
`python3 tools/archive-preview.py`. It checks the APK identity and signature,
retains a versioned copy under `releases/local/`, and imports an identical copy
into the owner's existing `02 Personal Files/Miduo` archive. Different builds
cannot replace an archived version. Receipts remain local to the project.

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
