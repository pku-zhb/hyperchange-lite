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
sampler's background-blur mode and the dedicated blend background's view-blur
mode, and clears its blend colors. Its native glass token and scale are preserved.
This follows the OEM's own disabled-provider/disabled-blend sequence rather than
erasing a glass token that may only be written again on a theme change. Native
sampling and dual glass radii are zero at this endpoint. Its native inputs remain
retained for a same-scene restoration;
100/100 or disabling tuning returns those current native inputs. Foreground
card materials remain independent throughout.

The preblurred-screenshot fallback cannot have its baked radius changed by the
compositor adapter. The explicit 0/0 endpoint hides only that dedicated native
background View after its normal update; leaving the endpoint reruns the native
update to restore its current alpha. Intermediate blur percentages do not imply
radius equivalence for the preblurred bitmap.

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

The current `ShadeSolidBackgroundController.updateWindow` is the final refresh
writer. Its native `setBackground(null)` retires only the plain-color baseline,
so switching from a solid fallback to compositor blur cannot resurrect a stale
color on a later parameter edit. The native Lazy background is resolved only
after the controller has already used it, preserving startup order.

A weak `SolidBackdropAlphaLease` is created only when tuning changes a nonzero
native alpha to zero. It records the exact native owner, background View,
ViewRootImpl and nullable Drawable identity. When tuning is disabled, the explicit
0/0 state is left, or strict scope ownership is revoked, the lease authorizes one
fresh native update only while those identities remain unchanged, the View is
attached and alpha is still our zero. A newer native alpha, replaced
View/root/Drawable, detach or missing weak target retires the lease without a
write. The lease is consumed before invoking native code, so reentry/failure
cannot retry an old release or erase a newly created lease.

This retained APK's complete `ShadeSolidBackgroundController.updateWindow`
method explicitly calls `view.setAlpha(1.0f)` in its non-screenshot branch before
setting/clearing the solid background. Its screenshot branch calls
`view.setAlpha(currentWindowRatio)` before returning. Therefore fresh native
update is sufficient to restore current alpha on this ROM; no historical alpha
value is stored or replayed. Natural native updates first retire the old lease,
including when the native result itself is alpha zero.

## Verification boundary

Host tests cover 0/100/200/disabled radius behavior, the explicit two-zero clear
state, exact root identity, close/generation revocation, nested calls, failed
scope probes, native exceptions, thread isolation, tint-mode distinction and the
stored-scale/runtime distinction, and one-shot alpha ownership release with
identity replacement, superseding native alpha, detach, native failure and
reentry. They do not establish pixel equivalence on a
phone. Device acceptance should compare a pulled-down notification center and
control center over the same App at 100/100 and 0/0; card appearance should remain
unchanged, closing should restore the App, and a top banner alone must not alter
its background.

## Shared sampler release after a transparent panel

The retained notification-combine flow has seven inputs: unoccluded notification
state, notification ratio, MiBlur enabled, keyguard showing, heads-up showing,
flip-screen state and visible-notification state. With MiBlur enabled and the
phone unlocked, either unoccluded notifications or heads-up showing selects ratio
1. Thus expanding the shade at 1 and closing it while a heads-up remains can leave
the StateFlow unchanged. The R8 collector writes a provider's background-blur mode
only when its `enabled` Boolean changes. `BlendBackground.setEnabled` has the same
Boolean early return for its view-blur mode and blend colors. A native card does
not necessarily repair those shared sampler modes itself.

A strict admission gate prevents new out-of-scope edits but does not release an
already applied zero. Changed-channel ownership is therefore separate from the historical setter
baseline. The engine observes native writes on registered receivers, including
visually equal writes outside the shade scope. Only actual adjusted arguments or
suppressed blend additions claim a write stamp. Each claim weakly identifies its
exact native owner, View and ViewRootImpl. Current plain-color claims additionally
check their Drawable identity and color; compositor claims check the original
SurfaceControl identity and validity. A newer native write, replacement identity
or detach retires the claim without writing.

On master-off or scope revocation, still-owned changed channels request one
current native batch per exact owner/View/root. A provider submits its current
`enabled` mode decision only if that mode is owned, and invokes native
`updateMaxRadius` when a sampling or glass-radius channel is owned. The blend
owner similarly restores only owned mode/color groups with `applyBlendColors`.
The solid owner runs `updateWindow`; a compositor claim reruns only its exact
current Runnable after verifying its original SurfaceControl is still valid.
No historical mode, radius, color, or glass token is replayed.

Candidate selection does not consume delivery responsibility. Immediately before
a group enters native code it rechecks the scene epoch and every channel stamp,
then consumes once. The epoch and target identities remain checked between native
submissions. If a phase change cancels delivery without native supersession,
still-current claims remain queued for reassessment; a replaced target is never
revived. Releases run before normal scoped refresh and do not expand the general
tuning scope to heads-up, keyguard or AOD.

The same ledger covers partial nonzero radius/tint tuning as well as 0/0.
Known covered paths are compositor `applyBlur`, provider background mode/radius
and dual glass radii, blend view mode and batch/legacy blend colors, and solid
background color. The OEM's `MiBlurCompat.setMiBackgroundBlendColors` clears the
legacy list then adds each resource color/mode; replacing/clearing the list
supersedes its earlier entry claims. Unchanged pass-window flags, native scale
and native glass tokens have no fabricated ownership or release. An unregistered
whole-window setter with no known current writer remains native.

Focused host tests cover unchanged enabled state across shade-to-heads-up,
current enabled/disabled decisions, equal-value native supersession, target
replacement, exact valid SurfaceControl, detach, failure, reentry and cancelled
candidate delivery. The retained framework's radius, blend, view-mode and glass
radius setters directly update RenderNode/bookkeeping, rather than synchronously
calling user callbacks. Entry stamps are conservative if an unknown third-party
hook introduces same-channel reentry. Arbitrary direct private RenderNode or
SurfaceControl writes are outside this contract; no phone visual result is
claimed by these host tests.

With the exact native adapter installed, ViewMaterialEngine skips direct replay
of historical SHADE setter/legacy baselines. Such a replay could otherwise
invalidate a live ownership stamp before the native owner can release it.
Master-off runs owned-channel and alpha-lease retirement only; ordinary provider,
blend and window refreshes are gated by the current independent enabled state.
Foreground card and notification replay behavior remains unchanged.
