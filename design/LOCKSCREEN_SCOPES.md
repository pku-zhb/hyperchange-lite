# Lockscreen material and animation scopes

The existing `notification_lockscreen_effects` preference now names notification
material permission only. Its saved values and legacy material migration remain
compatible. `notification_lockscreen_animations` is a separate default-false
opt-in; neither an old full export nor a material-only partial preset enables or
resets it implicitly.

Previously press tilt and row animations reused material scene permission, which
also exempted media/heads-up roles from the ordinary lockscreen gate. Heads-up
sessions explicitly ignored scene generations. Material and animation permission
could therefore disagree with the user's intended HMC ownership boundary.

Tilt, heads-up overlays and row add/remove now all require a confirmed unlocked
scene, or a confirmed settled non-dozing keyguard with the animation opt-in.
Unknown state, active controller transitions and AOD always retain native
animations. All roles carry animation-specific scene/role epochs; leaving the
scene or disabling the opt-in restores owned active transforms and blur. Material
permission changes alone do not invalidate an otherwise valid animation epoch.

Awake means native `mWakefulness == 2` and `mScreenState == 2`, not merely `dozing == false`. The
retained ROM writes that field in the exact
`KeyguardLifecyclesDispatcher$KeyguardLifecycleHandler.handleMessage`; optimized
`WakefulnessLifecycle` has no named wake/sleep dispatch methods. Its constructor
and that handler supply current state without a global Handler hook. The outer
controller/handler boundary expires effects applied while a real scene change
was still in progress. Handler codes 0–3 track screen state separately from
wakefulness codes 4–7. Repeated same-state messages/setters do not enter artificial
pending state or issue refreshes; their native effect observers can run normally.

## Ordinary lockscreen glass

The former forced recipe covered focus and media only. An ordinary lockscreen
row selected as native NORMAL/BLUR therefore had no glass token for numeric
setter tuning to adjust. Permission alone could not create the missing material.

The current four-argument row binder still owns selection and required clear
steps. For a confirmed awake keyguard, material permission plus the glass master
and notification range can adapt its plain effect. The adapter reads the current
interactor's `isShowingKeyguardNotification`, `isLightWallPaper`, full-AOD decision
and AOD-transition state. It dispatches the matching native keyguard/light-wallpaper
glass effect with this binder call's actual Context. Nested blur applications do
not cause duplicate conversions. Unknown state, AOD and wake/sleep transitions
never acquire a forced keyguard recipe; existing native AOD tuning remains separate.

A conversion records its actual background identity and native material-write
clock. Notification alpha/geometry changes are not evidence of another material
owner. Disable/scene change asks the current native binder to recalculate; the
adapter clears only its own conversion before the new effect. A cancelled queue
or intentionally suppressed AOD binder keeps the clear record until a current
native effect actually runs. No old recipe, theme Context or scene is replayed.
The native selector's AOD early return does not advance the material-write clock;
its documented grouped-child exception still does. Merely entering a selector
that performs no effect is not a material handoff.

`GlassSurfaces.excluded` was checked against current HMC source. HMC's capsule
and copied artwork layers remain correctly excluded. No source evidence showed
native notification backgrounds reparented under those owned overlay classes,
so this fix does not relax module/clock/island exclusions or claim such an
ancestry was the observed device cause.

Focused policy/migration tests cover the default-off split, settled keyguard,
unknown/AOD/transition denial, legacy imports, partial presets and current
keyguard/light-wallpaper selection. Production source is checked on the host.
The original scope split was host-verified. Later read-only module logs and
preference inspection confirmed the SystemUI material hooks are installed and
the owner's material ranges are enabled. Visual material, native completion and
coexistence behavior remain owner acceptance.

## Material identity after a heads-up disappearance

The retained ROM's `ExpandableNotificationRow.isHeadsUpState()` returns
`mIsHeadsUp || mHeadsupDisappearRunning`. That is a display/animation state,
not the identity used by `bindInternal$updateEffect`. Its material selector reads
the current row view model's `headUpState` and `hasCalledHUNDragDownDisappear`
flows. The previous classifier could reject ordinary keyguard conversion while
only the old disappearance-animation flag remained true.

Material classification now reads the same two live flows. A supported false
result is authoritative even while `isHeadsUpState()` remains true. Animation
classification keeps its original display getter. Missing/unsupported model
members or failed reads fall back to the previous getter; reflection metadata
is cached, but flow values and view models are never cached across calls. Both
true native flags still classify the material as heads-up.

## Selection and refresh trace

1. `NativeMaterialRebind` resolves the exact retained four-argument static row
   selector: interactor, row, `NotificationViewType`, `Ref$ObjectRef`. It stores
   its current interactor/type/effect reference before the native call. The
   retained media binder has the expected `(MiuiMediaHeaderView, interactor)`
   signature. Current SystemUI module logs contain 16 focus/media effect hooks;
   later plugin-only class-loader failures do not undo those installed hooks.
2. `NotificationScope` observes the keyguard, status-bar, screen and wakefulness
   controllers. Current module logs report five supported method signals. An
   active controller boundary denies ordinary keyguard conversion; its final
   state publication changes the material epoch and schedules a current rebind.
   Snapshot refresh also reads fade flags directly because this ROM has no
   matching fade-complete callback. Unknown fields never mean settled keyguard.
3. Material role/permission and the current scene are captured only for a pure
   setter preparation. `MaterialCallMemo` exists only inside that synchronous
   preparation and is discarded before calling native code. A scene revision,
   role or parent change invalidates the decision; there is no frame-to-frame
   cached permission or cached heads-up Boolean.
4. Current NORMAL/BLUR ordinary effects may convert only when the live keyguard
   gate and ordinary range allow it. Existing glass effects are tuned in native
   View setters. Media/focus normalization is independent. AOD's selected native
   effect is not replaced with a forced ordinary keyguard effect.
5. Refresh admission is deduplicated per root/configuration/material epoch.
   Detached roots, a missing binding, an AOD-transition deferral or an invocation
   failure return false and release the claim. MEDIA now carries the material
   scene epoch too, so an AOD/wake round trip cannot be mistaken for a previously
   completed media refresh. The media path emits into the existing collector;
   it never calls `bind()` again. A successful selector return or admitted event
   is explicitly not proof of a GPU material commit.

## Read-only diagnosis

After loading this version, use:

```sh
adb shell dumpsys activity service com.android.systemui/.SystemUIService hc-lite-material
```

Only that exact dump argument is handled by Lite. It prints observed scope
fields, material/animation permissions and epochs, current binding counts, last
native refresh result, keyguard conversion decisions, and native notification
glass-setter/tuned/denied counts. It contains no notification text, view IDs,
phone identifiers or credentials and adds no broadcast receiver. Reading the
dump does not publish a new scene, request a refresh or change settings.

This distinguishes a denied scope or missed binder from a card that did receive
changed glass values. HMC capsule relay capture/application remains a separate
downstream result; Lite's enabled App switches alone prove neither result.

## Finishing a native-busy material request

The retained full-AOD listener calls `updateNotificationStyle`,
`updateMediaNotificationStyle` and `finishForMediaNotificationAnim` before it
clears `mWillDoAodAnim`. Its cancellation also clears that flag without publishing
a new keyguard/status-bar state. A settled awake scope can therefore issue its
one material refresh while the native selector still refuses work. Previously
that refusal released the deduplication claim, but the root had already been
stamped with the current scene, and no later signal requested it again. A live
read-only sample showed settled lit keyguard alongside `lastRefresh=row-aod-transition`.

Only roots actually refused for that native busy flag now keep a weak reference
to its transition controller. After the same controller's native completion or
cancellation returns with the flag false, attached roots still bound to that
controller enter the existing current-scene queue once. A request for another
controller cannot consume them. A successful ordinary refresh clears its pending
retry; a newer controller replaces the old identity. Configuration, scope and
role guards are re-evaluated by the normal queue, including guarded releases of
disabled material ranges. Unknown native state does not create a retry.

This does not poll, create a new collector, write native transition flags,
replay a cached recipe or tune clock/lyrics/position animations. Deferred-root
and completion-retry counters are included in the existing read-only dump.

The dump also separates glass setter calls, changed arguments and denied calls
into ORDINARY, MEDIA and HEADS_UP roles, and reads the last row binder's exact
native effect enum on demand. The counter stage is explicitly `viewSetterRoles`:
it proves a View-level argument adjustment, not a downstream RenderNode/GPU
commit or visual transparency. No notification text, IDs or recipe payloads are
included, and enum reflection/string formatting run only on the explicit dump.


## Unified ordinary material on the settled lit keyguard

The previous unification path normalized only media and focus/live cards.
Ordinary keyguard rows could reach the enabled View setter tuner and receive
changed arguments while retaining a substantially different keyguard base recipe.
For example, the retained normal and keyguard recipes differ in their first four
slots, which the exposed tuning controls intentionally do not overwrite. A changed
ordinary setter therefore did not imply the same material as a normalized player.

With the existing unification option enabled, ordinary rows now use the normal
base recipe and shade blend colors only when the ordinary range permits it, the
scope is confirmed settled lit keyguard, and that exact row's current native
interactor confirms keyguard notification state, known wallpaper mode, no full AOD
and no native AOD animation. The recipe and colors resolve from that interactor's
current `getFixUiModeContext(notifStyle, false)`, preserving its current day/night
resource selection. Unknown state or unavailable context leaves the native recipe.

This is setter-level unification, not replacement of the ordinary row effect.
The native keyguard effect retains sampling, lifecycle and AOD ownership. The
full normal-effect replay remains restricted to its existing media/focus targets.
Disabling unification, heads-up rows, unlocked ordinary notifications, AOD and
native-busy rows retain their existing base selection. Material permission does
not enable any lockscreen animation. `keyguardNormalRecipes` counts successful
ordinary normal-base selections, alongside the separate View setter role counters;
neither counter is visual/GPU acceptance.
