# Independent card and whole-window materials

Version 0.1.4 separates the glass card's sampled backdrop from the whole-window
notification/control-center backdrop. Their values, enable switches and queued
refresh requests are independent. The 0.1.3 expansion, window ownership and
heads-up safeguards remain in force.

## Native source contract

The former shared controls were a configuration choice, not an OEM limitation.
The retained framework `View.java` contains separate, per-View operations:

| Operation | Primary evidence | Meaning for this fork |
| --- | --- | --- |
| Background sampling blur | `framework-current/View.java:12103–12115` calls `RenderNode.setBackgroundBlurRadius` and stores the receiver's blur radius. | The same API can be adjusted independently for a card surface and a distinct panel backdrop. |
| Refraction blur | `framework-current/View.java:12581–12587` calls `RenderNode.setMiGlassBlurRadius` with two radii. | This is a separate card-optics channel, not the whole-screen blur setting. |
| Window self blur and tint | `framework-current/View.java:12358–12365` updates self-blur radius, self-color modes and extend independently of background sampling. | Whole-window radius/tint can be adjusted without changing the card recipe. |

Here `framework-current` is the retained read-only framework source under
`/Volumes/Harbor/DevCache/hmc-duo/reference/`. The original HyperChanger base
`5d89372aaa1a6192e098be4baf93effff301cb07` hooks both background blur and window
self blur: `HyperSystemUiModule.kt:4285–4300` and `4397–4412`. That version reads
the same `settings.backgroundBlur` in both paths. This proves shared settings in
that implementation; it does not mean the rendering layers cannot be separated.

HyperLight 1.2.3 also exposes distinct controls, but their names must not be
confused with a proven card-versus-window split. Its `l21` case 5 (lines 71–80)
installs `global_blur_radius` on `View.setMiBackgroundBlurRadius`; `ya1:14–26`
applies a broad multiplier with exclusions and a minimum radius for real glass.
That hook is not limited to whole-window receivers. Its
`SoftGlassPresetManager.java:202–203` stores both
`soft_glass_global_blur_radius` and `global_blur_radius`. The latter's rendering
consumer is confirmed here; the former's full native rendering consumer was not
established by this pass. Two stored keys alone do not prove the requested layer
boundary. No claim that either original app lacks independent controls is made.

## Settings and runtime boundary

| Layer | Enable and scope | Values | Optical recipe |
| --- | --- | --- | --- |
| Card | Existing `glass_enabled` and ordinary/media/control-center/volume ranges | Existing `glass_background_blur`, `glass_background_scale`, `glass_background_opacity`, refraction and fourteen adjustments | Existing native token, optional unification, relative tuning and A–F recipes. |
| Whole window | New `glass_shade_enabled`; a confirmed expanded panel in the same root is still required | New `glass_shade_blur`, `glass_shade_scale`, `glass_shade_opacity` | OEM recipe and dual-refraction radii pass through unchanged. Only sampling blur/scale and tint are adjusted. |

The old `glass_shade` scope key remains readable/exportable for compatibility but
is hidden from the UI and no longer controls runtime behavior after migration.
Whole-window controls default to disabled and 100/100/100 on a fresh install.
Native scale tuning operates only where the OEM supplies the corresponding
background-scale setter; self blur itself has radius/tint inputs, not a separate
scale argument.

`shadeGlassSettings(values, enabled = null)` resolves the whole layer without
card optical adjustments. `readShadeGlassSettings(preferences)` reads its own
enable flag, while `readSoftGlassSettings` continues to represent the card layer.
`GlassRuntimeSettings` carries both snapshots. `ViewMaterialEngine` additionally
passes whole-window MiGlass recipes/refraction radii through unchanged, so an
accidental card token cannot enter this path.

The `SHADE` preview selects the same independent enable/value snapshot, even
when a card behavior sample would ordinarily force local tuning on. Its native
path uses the SystemUI `shade_blend_colors_bionics` color/mode array and a local
background sampler. It clears MiGlass/refraction and never applies the card's
fourteen optical slots. Other scenes keep the card sampling path. Missing shade
resources/APIs produce the identified fallback, rather than substituting a card
recipe. This is an App-local approximation of radius/scale/tint, not a blur over
another App window or a reproduction of the live panel's original baseline.

`MaterialRefreshPlan` unions independent card/backdrop requests. A scene-only
signal does not promote a card edit into a whole-window replay, and coalescing a
later card edit cannot discard an already-pending whole-window request. Every
replay still retires stale whole-window ownership before selecting its layers.

## Upgrade, import and preset rules

- A separate `_backdrop_migration_version=1` marker leaves the older scope marker
  unchanged. On legacy stored preferences, missing whole-window values inherit
  the old shared three values once, and the new enable flag inherits the old
  effective `glass_enabled && glass_shade` choice. Explicit new values win.
- The hook reader performs the same resolution before the settings App opens.
  Persisted migration-only fresh defaults do not count as an offline user edit
  and cannot hide an existing remote configuration at first binding.
- A legacy complete JSON export inherits the former shared values/enable choice.
  Completeness is checked against the retained legacy catalog, allowing the
  known later media/lockscreen/position keys to be absent. Partial card presets,
  unrelated partial imports and partial whole-window imports change only their
  explicitly supplied whole-window keys; they do not create implicit defaults.
- New JSON exports retain format/version compatibility and add root metadata
  `backdrop_migration_version=1`. Internal migration markers are not public
  material settings. New-format imports do not infer missing whole values from
  card values.
- A–F application, trial undo and card-material reset contain no whole-window
  key. Applying a preset may enable the existing card master, but cannot enable
  the independent whole-window layer. The explicit whole-App reset restores the
  new enable flag to false and its three values to 100.

Host tests cover one-time migration, fresh/remote precedence, complete versus
partial imports, independent enable switches, preset/undo/reset boundaries,
optical token preservation and queued refresh coalescing. They do not establish
pixel equivalence with a live OEM window.
