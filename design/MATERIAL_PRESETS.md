# Built-in material comparisons

Preview 4 restores HyperChanger's six built-in A-F recipes from
`SoftGlassPresets.kt` at the retained upstream base `5d89372`. Names, IDs and all
18 numeric parameters are preserved. No HyperLight recipe or asset is included.

| Preset | Refraction blur % | Background blur % | Background scale % | Blend opacity % |
| --- | ---: | ---: | ---: | ---: |
| 澄光 (A) | 13 | 12 | 32 | 53 |
| 薄纱 (B) | 22 | 29 | 32 | 53 |
| 浮影 (C) | 44 | 36 | 0 | 73 |
| 朦光 (D) | 55 | 28 | 0 | 73 |
| 凝雾 (E) | 115 | 81 | 0 | 73 |
| 云隐 (F) | 117 | 74 | 32 | 59 |

The complete original fourteen adjustment values are defined in
`GlassPresets.kt` and checked against independent fixtures. Start with A and E,
whose blur and material inputs differ substantially.

## Application and restoration

Open **柔光玻璃 → 材质预设**. Applying a recipe commits the complete material
parameter set and `glass_enabled=true` in one local edit, then publishes through
the existing module connection. It preserves music takeover, ordinary and
lockscreen ranges, the unified recipe, Assistant/sidebar and all other features.
The connection indicator reports the preference service, not a loaded SystemUI
hook or a verified visual effect.

Existing OEM binder roots are enumerated even when tuning was previously off.
An admitted, active range receives one current-state recalculation on a material
change; it does not depend on a setter baseline captured while disabled. The
same root/configuration/scene is deduplicated across the inventory and delayed
engine entry paths. Failed/deferred requests can retry. Neither this refresh nor
applying a recipe overrides the HMC compatibility gates.

The first trial stores a material-only snapshot locally. Switching recipes keeps
that original snapshot. **还原试用前材质** restores only those parameters and the
master toggle; it does not migrate or restore compatibility ranges. Import and
full reset clear the trial snapshot. No new public settings key or preset JSON
format is required; current material numbers remain included in normal exports.

## Preview honesty

The preset page respects the saved material master toggle for a parameter-on/off
comparison. Other preview pages retain their existing independent sample mode.
Turning tuning off preserves any separately selected unified recipe, so this
comparison is the current material before relative tuning, not an unconditional
return to every OEM default.

The preview reports **原生材质接口已调用** only after the required native setup and
MiGlass calls succeeded for every displayed sample. This is not a raster-output
test or SystemUI hook validation. Failed setup reports **当前为示意预览**; the
fallback gradient cannot show complete glass tuning.

The new vector test background has sharp contrast boundaries. Local sampling uses
the upstream-established `passWindow=false` and `backgroundBlurType=1` path because
the chart is inside the App window. The earlier behind-window configuration could
conceal local blur changes. Preview changes operate only on App-owned views.

## Device acceptance

Use the same day/night choice and background to compare 澄光 and 凝雾, first in
the App, then at the same position in an enabled unlocked Control Center or
volume panel. Their appearance depends on current OEM material and scene. If
only the App changes, check the scope/load path rather than assuming preset math
failed. If it reports schematic mode, use the actual system panel for comparison.
Keep the established HMC music and lockscreen range choices during this trial.

Phone GUI operation, screenshots, installation and scope restarts are not part of
host verification. The owner controls actual hook activation and visual acceptance.
