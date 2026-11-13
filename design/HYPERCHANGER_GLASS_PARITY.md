# HyperChanger soft-glass comparison

The core parameter math and A–F recipe values match HyperChanger. The complete
scope selection and local App preview are not identical to an original
HyperChanger/SystemUI panel. The earlier preview also painted an extra veil over
successful native glass; this round removes it and supplies the normal OEM blend
colors through the native API instead.

## Primary-source baseline

Upstream is `ColdP/HyperChanger@5d89372aaa1a6192e098be4baf93effff301cb07`, the
base recorded by this repository. The retained extraction at
`/tmp/hyperchanger-audit-20261112/source` was read without modification. The four
upstream files below were also fetched in memory from the pinned official raw
URLs and matched the retained files byte for byte. No source history or new
reference tree was created.

Source abbreviations in the tables:

- **U/**: upstream `app/src/main/java/btm/m/os4/systemuihook/`.
- **L/**: this repository's `app/src/main/java/com/zhuhuibin/hyperchange/lite/`.
- **U module**: U/HyperSystemUiModule.kt.
- Line references describe the reviewed source; method names are retained to
  locate the evidence after later edits.

Official pinned sources:
[parameters](https://github.com/ColdP/HyperChanger/blob/5d89372aaa1a6192e098be4baf93effff301cb07/app/src/main/java/btm/m/os4/systemuihook/SoftGlassParameters.kt),
[presets](https://github.com/ColdP/HyperChanger/blob/5d89372aaa1a6192e098be4baf93effff301cb07/app/src/main/java/btm/m/os4/systemuihook/SoftGlassPresets.kt),
[settings](https://github.com/ColdP/HyperChanger/blob/5d89372aaa1a6192e098be4baf93effff301cb07/app/src/main/java/btm/m/os4/systemuihook/SoftGlassSettings.kt),
[SystemUI module](https://github.com/ColdP/HyperChanger/blob/5d89372aaa1a6192e098be4baf93effff301cb07/app/src/main/java/btm/m/os4/systemuihook/HyperSystemUiModule.kt).

## What matches

| Concern | Result | Primary evidence |
| --- | --- | --- |
| Fourteen adjusted slots | Same keys, bounds, adjustment ranges and slots: `4,5,6,14,32,21,33,34,35,19,24,22,28,7`. The slots represent luminance, saturation, brightness, inner opacity, refraction, thickness, sampled-background saturation/brightness, burn, edge width/feather, reflection offset, highlight and darkening. | U/SoftGlassParameters.kt:14–30; L/glass/SoftGlassParameters.kt:16–32. |
| Relative conversion | Same relative multiplication, zero-baseline hundredths fallback, inverted edge-feather adjustment and bounded output. The body from `import` onward in the two parameter files is byte-identical. | U/SoftGlassParameters.kt:57–83; L/glass/SoftGlassParameters.kt:59–85. |
| Blur, scale and alpha conversion | Same integer blur scaling and zero preservation, background-scale conversion, and color-alpha multiplication with RGB/mode preserved. Inner opacity at recipe slot 14 is a separate channel from backdrop color alpha. | U/SoftGlassParameters.kt:112–125; L/glass/SoftGlassParameters.kt:114–127. |
| Default parameter inputs | Both readers use four 100% values, zero adjustments except darkening −100. Storage namespaces and legacy migrations differ. | U/SoftGlassSettings.kt:16–34; L/glass/GlassSettings.kt:9–18. |
| A–F preset numbers | All six presets have identical four main percentages and fourteen adjustments; all 108 numeric inputs match. The data does not contain a HyperLight preset conversion. | U/SoftGlassPresets.kt:106–174; L/GlassPresets.kt:50–94. |
| Native renderer family | Both intercept OEM `View` APIs for MiGlass, dual refraction blur, background blur/scale, blend colors, legacy blend calls, self blur and selected panel background color. This fork forwards adjusted values into the OEM renderer rather than implementing its raster algorithm. | U module:4190–4433; L/glass/ViewMaterialEngine.kt:84–93,184–213,215 onward. |
| Volume and heads-up material arithmetic | Active volume uses the normal-notification resource with +0.48 at sampled-background brightness slot 34. Heads-up keeps its mode-specific incoming recipe and receives no +0.48. Both keep volume/heads-up sampler blend alpha at 100% to avoid an additional multiplication. | U module:4210–4258,4351–4355,4391–4394; L/glass/GlassHooks.kt:48–52; L/glass/ViewMaterialEngine.kt:185–213. |
| Normal-notification resource | Both resolve `notification_glass_params_normal` and fall back to SystemUI package resources when a plugin cannot resolve them. | U module:5762–5794; L/glass/NotificationMaterial.kt:43–57. |

## Deliberate scope boundaries and historical differences

| Difference | Classification and effect | Primary evidence |
| --- | --- | --- |
| Surface admission | Deliberate reduced-fork boundary: upstream can admit anonymous views through notification/control-center/volume call stacks and refresh state. Lite classifies actual views/ancestors and excludes module-owned layers and clock geometry. Coverage is therefore narrower, even when the numeric conversion is identical. | U module:4130–4155; L/glass/GlassSurfaces.kt:71–99. |
| Music and ordinary lockscreen ranges | Deliberate HMC coexistence boundary: Lite has independent ordinary/media ranges and an ordinary-lockscreen permission. A successful local preview cannot establish that the current SystemUI view is admitted. No removed clock/AOD/desktop feature is restored by this review. | L/glass/GlassHooks.kt:28–56; L/glass/NotificationMaterialPolicy.kt:14–20; L/scope/NotificationScopePolicy.kt:6–20; L/glass/GlassSurfaces.kt:71–79. |
| Ordinary-row backdrop alpha | Historical selection difference, not a different alpha formula: upstream's blend admission primarily covers panels and heads-up. Lite also applies backdrop-alpha scaling to admitted ordinary notification surfaces. This review records the difference and does not broaden the runtime scope to reconstruct all original features. | U module:4149–4155,4341–4355; L/glass/ViewMaterialEngine.kt:184–213. |
| Applying a preset | Historical App behavior difference: upstream preserves the current master-enable flag; Lite explicitly enables tuning when applying a built-in preset. Recipe numbers remain identical. | U/SoftGlassPresets.kt:14–16; L/GlassPresets.kt:32–38. |
| Preference loading | Upstream reads its original prefix and migrates older edge-feather/blur/scale keys. Lite reads its established `glass_*` namespace and validates numeric inputs. Existing-user configurations need not contain the same values merely because the defaults match. | U/SoftGlassSettings.kt:16–34; L/glass/GlassSettings.kt:9–21. |

## Why the earlier App preview looked less transparent

At the reviewed pre-change Lite commit
`9bf1165e3093ff606f439d281a4744acc9c20845`,
L/preview/GlassPlayground.kt:920–927 applied a custom top-left-to-bottom-right
Drawable gradient and a stroke even when native material setup succeeded. At
100% backdrop opacity, the native-mode day gradient had alpha 22→102
(approximately 8.6%→40%); night had 20→68 (7.8%→26.7%). These are additional App
paint layers over the native material. Their presence and opacity are confirmed
by source; their exact contribution to a particular screenshot is not measured.

The earlier native preview did not seed the normal notification's OEM blend
texture; its backdrop-opacity control was consumed by that custom gradient.
The original source describes the normal row's shade blend colors and their two
modes at U module:5797–5829, and warns against applying ordinary-row blend layers
twice at 5802–5805. A fresh App-owned sample has no NotificationRowBlurEffect to
seed those points for it.

This round changes only the App preview path:

- L/preview/NativePreviewMaterial.kt:139–159 loads and caches the normal recipe
  and `notification_element_blend_shade_color_1/2` with the matching integer
  `mode_1/2` from the existing day/night SystemUI resource context.
- Its `apply` at 106–126 sends a complete native blend list once through
  `setMiBackgroundBlendColors`, using the shared `softGlassColorAlpha` conversion.
  Opacity remains a native material input after the painted veil is removed.
- Its `clear` at 129–136 clears batch/legacy blend points and the App-owned native
  material configuration. Missing required resources/APIs return fallback status;
  successful setup is still an API-call result, not a raster-output test.
- L/preview/GlassPlayground.kt:963–972 uses no Drawable background or stroke on a
  native-success sample. The schematic fallback retains a distinct gradient.

## Subsequent 0.1.3 ownership correction

Whole-background admission is now intentionally stricter than the original HC
controller-call-stack guard and the earlier Lite window-identity guard. A real
expanded unlocked panel is required; a heads-up-visible window does not qualify.
Old backdrop snapshots retire on close/replacement rather than replaying into a
later heads-up session. Core conversion and preset data remain unchanged. See
[shade background ownership](SHADE_BACKGROUND_SCOPE.md) for evidence and acceptance.

## Remaining preview differences

The App still uses the normal-notification recipe for every local scene. Its
sampler has fixed local sample baselines: background blur 40, background-scale
0.075, and dual refraction blur 36/72 before the same relative adjustments. It
does not capture each live OEM panel's current radii or mode-specific token.
Evidence: L/preview/NativePreviewMaterial.kt:106–116,139–159; in contrast,
L/glass/ViewMaterialEngine.kt:185–201 adjusts native incoming baselines.

Local sampling intentionally uses `backgroundBlurType=1` and
`passWindowBlurEnabled=false` so it can sample content inside the App window.
That local-content setup is established in upstream
U/hypermusiccover/MiBlur.java:130–141 and
U/SettingsSearchMaterialView.kt:64–76. It is not a claim that every SystemUI
surface uses the same sampling owner.

Preview range logic and notes explicitly identify the normal-notification sample,
unmodeled lockscreen transitions and unreconstructed widget/sidebar recipes at
L/preview/NativePreviewMaterial.kt:22–64. Thus equal sliders or a successful
native API status do not establish equal SystemUI pixels. Final build tests and
device appearance remain separate release-acceptance evidence.
