# Whole-window shade backdrop, 0.1.6

## Confirmed defect

The three previous shade controls described an entire notification/control-center
background, but the preview rendered a local card-like sampler. Runtime also
missed the OEM's actual compositor path. Splitting preference keys did not by
itself connect that path. A card-looking preview changing correctly therefore
could not prove that the App behind a pulled-down panel would become clear.

The retained SystemUI APK has SHA-256
`074486d6856c8eda2764d8864d99365e178d4a4758836e3c44152862ddc8b7e1`.
Its complete method bodies establish these separate channels:

| Native owner | Receiver/channel | Relevant behavior |
| --- | --- | --- |
| `ShadeWindowBlurController$updateBlurRatioRunnable$1.run` | `BlurUtils.applyBlur(ViewRootImpl, int)` | Reads the current window StateFlow, converts ratio to radius, and submits compositor blur to the exact window root. The zero-radius native clear must pass through. |
| `ShadeBlendBlurController$BlurProvider` | `view.setMiBackgroundBlurRadius` and `setMiGlassBlurRadius` | The three providers are constructed from `shadeWindowView`, `notificationPanelView`, and `controlCenterContainer`. They are whole-panel receivers, not notification/media card backgrounds. |
| `ShadeBlendBlurController$BlendBackground` | Dedicated shade background blend colors | Structural transforms and tint are separate recipe entries. Scaling every color alpha is incorrect. |
| `ShadeSolidBackgroundController.updateWindow` | `shade_background` | Its normal branch writes a solid RGB/alpha background. The screenshot fallback instead changes the alpha of a preblurred image. |

Framework source independently confirms that `View.setMiBackgroundBlurMode(0)`
disables that receiver's background sampler, `setMiGlass(null)` clears its optical
token, and `clearMiBackgroundBlendColor` clears its structural transforms. These
are per-View operations. This feature does not replace notification controller
state, window-expanded flags, shared GPU configuration, or compositor transactions.

## Exposed values

- `glass_shade_enabled` is independent of card tuning and card presets.
- `glass_shade_blur`: 0 removes native whole-window blur; 100 keeps the current
  native radius. This includes compositor radius, whole-panel sampling radius,
  and whole-panel bionic glass's dual blur radii. It does not use card refraction
  adjustments. Native animation-zero remains zero at every percentage.
- `glass_shade_opacity`: 0 removes native dim/tint alpha; 100 retains it. The
  normal solid-color background is included. Blend mode 106 is the retained
  shade tint and mode 9 is the native self-blur dim. Structural mode 21 and
  unknown modes remain unchanged outside the explicit fully transparent state.
- The common slider range is 0–100. Existing 0–200 imported/numeric values remain
  valid, with the existing bounded blur/alpha math.
- `glass_shade_scale` stays in the catalog and JSON for backup compatibility. It
  has no visible editor and runtime always preserves the native scale. A hidden
  historical zero/200 cannot silently zoom the underlying App.

When both visible values are zero, tuning clears only the admitted whole-panel
sampler's blur mode, known glass token and blend recipe. Native scale remains
unchanged; disabling the sampler removes its visual scaling. Leaving a structural
transform or optical token active can still alter the App even when blur/tint
alpha is zero. Its native inputs remain retained for a same-scene restoration;
100/100 or disabling tuning returns those current native inputs. Foreground
card materials remain independent throughout.

The preblurred-screenshot fallback cannot have its baked radius changed by the
compositor adapter. Do not claim radius equivalence for that fallback without a
separate native snapshot adapter and device evidence.

## Admission and refresh

`WindowBackdropCallGuard` admits a compositor submission only inside the exact
native controller Runnable, for the identical ViewRoot, in the same shade
ownership generation. Existing strict `ShadeScope` still requires an actually
expanded, unlocked, non-AOD panel in that root. A heads-up-visible window is not
authorization. Nested unknown/ineligible calls remove the outer permission;
failed probes fall back to one native call. No permission crosses threads or
survives a native exception.

Provider identities come from the exact native BlurProvider field after
construction, with method entry as a late-attachment fallback. They are weakly
held. Module/clock/row/media receivers are excluded, including a provider View
later reparented into such a surface.

Some native mode/recipe writes can occur before the strict expansion lease is
granted. Do not retain those inactive writes for later replay. Instead, weakly
registered current BlurProviders supply their current `enabled` value to the
receiver's blur-mode setter and call native `updateMaxRadius` with their current
three max fields. That method always recalculates sampling/glass radii from its
current ratio. Current BlendBackgrounds call native `applyBlendColors`, which
reads their current enabled/ratio/recipe. Each writer checks its current View,
root and scene epoch before submitting, without changing provider state.

Setting edits coalesce to one frame. The compositor refresh reruns the original
Runnable against the current StateFlow and current valid surface, never a saved
radius. An invalid surface does not cause this feature to start an OEM retry
loop. View-setter refresh retains its existing generation, ownership, parent,
reentry and stale-replay checks. Closing a panel retires historical backdrop
inputs; it never replays them into a heads-up-only or lockscreen window.

## Verification boundary

Host tests cover 0/100/200/disabled radius behavior, the explicit two-zero clear
state, exact root identity, close/generation revocation, nested calls, failed
scope probes, native exceptions, thread isolation, tint-mode distinction and the
stored-scale/runtime distinction. They do not establish pixel equivalence on a
phone. Device acceptance should compare a pulled-down notification center and
control center over the same App at 100/100 and 0/0; card appearance should remain
unchanged, closing should restore the App, and a top banner alone must not alter
its background.
