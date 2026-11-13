# Heads-up card glass

## Confirmed causes and scope

The reported near-white banner cannot be diagnosed from its color alone. The
retained SystemUI APK (`074486d6856c8eda2764d8864d99365e178d4a4758836e3c44152862ddc8b7e1`)
has different glass baselines for normal rows and heads-up notifications:

- `notification_glass_params_normal[14]` is `0.10`.
- `HeadsUpNotificationGlassEffect.apply` supplies a 42-slot token with slot 14
  `1.5`; `HeadsUpNotificationGlassDarkEffect.apply` uses `0.6`.
- The observed saved inner-alpha adjustment was `+110%`. The shared relative
  conversion produces `0.21` on the normal token and caps the light banner at
  `2.0`. This establishes a material-input difference, not a measured final pixel.
- The previous card blend policy also exempted heads-up from background-opacity
  tuning. A saved `73%` affected normal rows but left the banner at `100%`.

The opacity omission is corrected for notification cards. Volume retains its
separate native alpha owner. No baseline percentages or optical defaults change.

## The background belongs to the card

`HeadsUpNotificationGlassEffect` first applies `HeadsUpNotificationBlurEffect`,
then supplies glass and material type 1 to that row's `NotificationBackgroundView`.
The blur effect selects `notification_heads_up_transparent_bg`: a 24 dp rounded
shape with Android's transparent solid color. It is not an opaque overlay above
the card. Its blend resources are `notification_element_blend_headsUp_color_1/2`
with modes 107/106. `NotificationUtil.applyElementViewBlend` sets the card's
view-blur mode to 1 and applies those points; it does not restore a shared window
sampler. Shared shade-sampler release is a separate ownership concern.

## Explicit soft-glass option

Pinned upstream `ColdP/HyperChanger@5d89372aaa1a6192e098be4baf93effff301cb07`
preserves the mode-specific heads-up token during ordinary tuning. It also has
an independent, default-off `KEY_HEADS_UP_NOTIFICATION_SOFT_GLASS` opt-in in
`installHeadsUpNotificationSoftGlassHooks` (HyperSystemUiModule.kt:10458 onward).
That option dispatches `NotificationRowBlurEffect` and supplies the normal
notification resource token, then selects material type 1.

Lite restores that independent behavior as `glass_heads_up`, default false. All
of the explicit opt-in, card tuning, ordinary-notification range and confirmed
unlocked scene are required. It does not grant lockscreen permission, alter
banner placement/gestures, change ordinary unification semantics, or authorize
whole-window tuning. Only the retained native Blur/Glass/GlassDark effects are
adapted. Missing required methods/resources keep the native effect.
The adapter uses the exact Context supplied by the OEM selector, including its
fixed theme mode, for the normal recipe and blur/blend application.

The existing native-row binder supplies current effects after settings/theme/
scene changes. Converted roots retain weak ownership records; disabling a gate
requests that current native selector through the existing release ledger,
rather than replaying an old token. Native clear follows the same normal-row
clear path used by the upstream opt-in, then removes the owned token with the
zero recipe/material type 0 used by the OEM heads-up glass clear. Scene changes during conversion abort
the override and return to the original native effect.
The exact native row-selector scope revokes old write tickets and permits its
previous-effect clear before the next apply. A late clear outside that scope
cannot retain authority merely because the row reused its Drawable and alpha.

## Preview and acceptance boundary

Previously `HEADS_UP` always used the normal notification preview token, even
though the runtime default used the denser banner token. The default sample now
uses the verified native light/dark banner inputs and its matching live SystemUI
blend resources. Enabling the explicit option with its master/range gates uses
the normal resource token/blends, matching the runtime adapter. These numeric
native-banner snapshots are tied to the retained ROM; the sample is not a live
capture of a different ROM's current material selector or compositor.

Opacity and permission/preview regressions have focused host coverage. GPU
appearance remains device acceptance: compare the option off/on at unchanged
settings, then disable it while a banner is showing and verify the next native
state returns. A high saved inner-opacity adjustment may still look thick with
the option off; correcting a skipped tint control does not alone prove that all
glass detail has returned. Whole-window transparency must remain isolated from
a banner-only window.

## Independent animation scheduler compatibility

The installed build logged `Required value was null` in
`NotificationHeadsUp.Channel` while resolving `scheduleUpdateProperties()` on
the row injector. The retained ROM declares this exact zero-argument scheduler
as a default method on `com.miui.systemui.notification.row.ExpandableViewInjectorBase`;
it forwards to the native View scheduler. Our accessor cache searched declared
class/superclass methods only and therefore missed that interface default.
The narrow compatibility correction retains nearest declared/private lookup,
then falls back to public inherited-method resolution and caches the result.
It does not replace the scheduler with `dispatchPropertiesUpdate`, which has
different child-propagation semantics, or rewrite the animation. A JVM regression
uses an actual inherited Java interface default. Native visual animation remains
part of device acceptance after this reflection contract is repaired.
