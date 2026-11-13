# Shade background ownership

## Report and evidence

The owner reported that changing glass inputs could make the foreground App look
blurred, shadowed or dimmed when a heads-up banner appeared. In an owner-operated
A/B check, disabling only **下拉背景** stopped the symptom. This locates the
problem in the whole-background range. No device trace identifies whether one
particular occurrence used radius, blend color, or a replayed old input.

The old engine admitted NotificationShadeWindowView.setMiSelfBlur by window
identity, with no real panel-expansion requirement. The shade range allowed
non-notification writes and configuration refresh could replay saved window
inputs after the panel closed. Scene-only refresh skipped those non-notification
caches. These are confirmed source defects, not an inferred material-formula error.

## Native contracts

The retained main SystemUI APK SHA-256 is
`074486d6856c8eda2764d8864d99365e178d4a4758836e3c44152862ddc8b7e1`,
matching the existing keyguard/status-bar evidence. Exact method declarations
were checked in its DEX, including the OEM dollar-sign suffixes.

- PanelViewController.isExpanded() includes pinned or animating-away heads-up
  notifications. NotificationShadeWindowControllerImpl's window-expanded state
  also includes heads-up, keyguard and other owners. Neither authorizes a
  whole-background edit.
- NotificationPanelViewController provides mExpandedFraction and
  isExpandedWithoutHeadsUp(). Authorization requires a positive finite real
  fraction, that predicate, mBarState=0, and confirmed unlocked/non-AOD state.
- The retained control-center plugin's ControlCenterExpandController exposes
  getExpansion(). A positive finite fraction identifies its actual panel,
  including collapse animation. Appearance is only the target; visibility is
  lifecycle state. onStop/onDestroy revoke ownership even if fraction is stale.
- Each controller is associated with its actual live View/root. Notification and
  control-center kinds can coexist in a root. Replacement applies within that
  root and kind; an active controller in another window cannot authorize writes.

No decompiled code or APK is redistributed by this note. These are retained-sample
contracts, not a fresh read of the currently installed phone artifact.

## Fix

ShadeScope keeps weak controller/root leases. Zero expansion, detach, replacement,
stop, destroy and unconfirmed lockscreen/AOD state revoke them. Reads may revoke
stale ownership; a positive stale getter alone cannot reopen it. A new OEM
expansion signal is required. Revisions change on ownership transitions rather
than every animation frame; listener delivery is coalesced outside native setters.

SystemGlass applies this scope to dedicated shade backgrounds and the whole
notification window. Inactive or unknown backgrounds keep original native inputs.
Recognized notification backgrounds/media retain their existing card scopes;
other notification-row descendants cannot fall through into SHADE/CONTROL_CENTER.

The engine retires backdrop baselines on scope/revision changes, before a replay
can see them. Prepared writes check the backdrop revision again after native work,
so a close or root replacement inside that work cannot recreate old ownership.
The replay guard suppresses only the module's stale saved setter calls. External
native zero-radius, clear-color and cleanup calls still pass through unchanged.
A close/reopen round trip cannot revive the previous panel session's inputs.

Whole-background tuning now waits for a confirmed unlocked, actually expanded
panel. Lockscreen/AOD and unknown controller contracts retain the OEM background.
The existing HMC/card lockscreen permissions, card glass arithmetic and preset
numbers are unchanged. No new setting or broad call-stack admission is introduced.

## Acceptance

Host checks cover heads-up without real expansion, non-finite/unknown inputs,
positive collapse fractions, per-root/kind replacement, lifecycle revocation,
reopen leases, native cleanup, and interrupted/nested replay. Android dispatch,
GPU output and actual scope startup still require owner-operated acceptance.

1. Re-enable **下拉背景** after loading the new scope code. Keep an App visible,
   trigger a heads-up banner and verify its full background stays unchanged.
   Repeat with visibly different material presets/background-blur inputs.
2. Pull down notification/control center: its real background should still adjust.
   Close it, change inputs and trigger another banner; no old full-screen dim or
   blur may return.
3. Check one lock/AOD or fold/root change, followed by an App/banner. Old panel
   inputs must not survive; lockscreen/AOD background remains native.

No phone GUI, screenshots, settings writes, install, scope restart or reboot were
used for this source fix. The owner's A/B result is distinct from APK acceptance.
