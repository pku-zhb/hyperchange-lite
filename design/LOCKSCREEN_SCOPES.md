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
This iteration does not connect to or operate a phone; visual material, native
completion and coexistence behavior remain owner acceptance.
