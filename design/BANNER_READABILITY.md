# Experimental banner text readability

The default-off `notification_banner_readability` switch is titled
`横幅通知可读性优化（实验性功能）`. It is independent of material, animation and
lockscreen permissions. It changes only standard text in a visible native heads-up
child on a confirmed unlocked screen. It never recolors icons, images, action
buttons, the ordinary shade list, media cards or lockscreen content. Only one
current banner is observed. There is no settings-page subtitle or claimed live
sampling preview in the App.

## Actual sampling contract

The retained SystemUI artifact declares
`com.android.wm.shell.shared.handles.RegionSamplingHelper` in
`StatusBarRegionSamplingInteractor`, using its four-argument View/callback/main
executor/background executor constructor. The implementation uses that native
helper through reflection rather than redistributing a helper implementation.
The current [AOSP helper API](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/master/libs/WindowManager/Shell/shared/src/com/android/wm/shell/shared/handles/RegionSamplingHelper.java)
and [framework composition listener](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/view/CompositionSamplingListener.java)
provide the reference contract. Native compositor sampling reports median
brightness for layers below a stop surface; this is not a screenshot or a
TextView drawing-cache read.

Before sampling, the adapter requires the banner's current valid ViewRoot surface
and display 0. A callback is accepted only with an attached, unchanged root and a
valid registered stop layer. A helper's fallback that samples without excluding
its own surface is rejected, so recolored text cannot feed back into the next
sample. An initialized native field is not considered a sample; a native sample
callback must have arrived first. Replacing/dismissing the banner or closing the
feature destroys the listener and revokes late callbacks. Native helper View
listeners and a pending draw-listener removal are cleaned up as well.

The sampled patch is centered behind the native text inside the visible heads-up
child, capped at **192 x 48 physical pixels**. Each event opens at most **250 ms**
of sampling, collects at most eight scalar observations and uses their median.
Sampling starts at least **750 ms** apart. There is no permanent timer or frame
listener. Only banner/content replacement, actual bounds changes and an App
transition-complete event request a new burst. The native worker is serial, and
sampling unregisters after each burst; no bitmap or RGB pixels are retained.
SurfaceFlinger may batch its own other clients; this module requests only the
small patch and cannot promise the compositor's global scheduling cost.

## Contrast and conservative fallback

A first measured sample chooses light or dark opaque text. Subsequent changes use
separate 0.40/0.54 brightness thresholds and a 600 ms minimum hold. Title and body
roles use distinct colors. Native brightness is not asserted to be linear WCAG
luminance, and the sample excludes the banner's own tint, refraction and blur.
This is a local underlay heuristic, not proof of the final composited contrast
ratio. A small median also cannot represent every part of a highly varied image.

Color adaptation requires a confirmed native glass consumer. Unsupported helper
APIs, missing surfaces, non-default displays, timeouts and unknown/opaque material
retain native text colors and spans, adding only a restrained opposite-color
shadow. This fallback does **not** claim actual-background adaptation. Protected
content may withhold samples; no capture permission or secure-content bypass is
added. Custom App template IDs are left untouched.

Only standard template title/body/header IDs in the Android/SystemUI resource
namespaces are admitted, with at most 16 TextViews, 128 visited children and ten
levels. TextView setter observers are installed lazily on the first enabled
banner, then immediately bypass unrelated views. Native nested setter calls are
coalesced so the outer API records native inputs, not this feature's replacement.
ColorStateLists, text/spans and shadows have separate native baselines. Native
rewrites supersede those baselines; restoration changes only values still owned
by the feature. Foreground-color spans are replaced temporarily, while a colored
TextAppearanceSpan delegates its original font/measurement behavior and changes
only its draw color. Other spans remain intact. Cleanup also restores shadows
on disable, dismissal, content replacement, detach, shade entry and lock/AOD.

## Read-only diagnostics and validation boundary

`dumpsys activity service com.android.systemui/.SystemUIService hc-lite-banner`
reports enable/session/sampler state, backend availability, burst/sample/event and
restoration counts, last scalar brightness, tone and primary/secondary target
counts. It contains no notification text, App identity, view IDs or raw pixels.
The existing `hc-lite-material` dump remains independent.

Host policy tests cover invalid/no samples, primary/secondary roles, native-ID
admission, hysteresis/hold, bounded bursts, cancellation/replacement and native
ownership restoration. Settings tests cover the exact title, default/reset,
partial imports and round trips; normal localization/editor checks cover reachability.
The subsequent 0.1.10 live check confirmed the native helper accepts actual
compositor samples for a real heads-up banner and cleans up on exit/disable.
The default-off value and temporary test artifacts were restored. The accepted
sample contract requires a valid stop surface; the diagnostic does not prove
final pixel contrast, visual span appearance or the compositor's total cost.
See [validation](VALIDATION.md) for host and device evidence separately.
