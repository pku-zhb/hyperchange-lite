# HMC Duo coexistence

Preview 3 implements the Lite side of the shared HMC Duo compatibility handoff.
It does not modify HMC, MiDuo or MiDuo Notification, discover installed modules,
read another module's preferences, or change scope/process state.

## Supported configurations

| Configuration | HMC music settings | Lite music material |
| --- | --- | --- |
| A: OEM/Lite material | Background, flow and theme all default | `glass_media=true`; tune current material or explicitly enable the unified recipe |
| B: HMC background | Cover, gradient, soft-cover or custom flow | `glass_media=false`; ordinary notifications remain independent |

Enabling the music range is permission to tune an existing material. It does not
itself force a glass recipe. `glass_unify_notifications` is independent of
`glass_enabled`, but both must obey the selected object and scene ranges.
Capsule lyrics, content, geometry and HMC's one-way material following remain HMC's
responsibility. Control Center's separate media panel is outside this music range.

## Settings and migration

| Key | New/reset value | Upgrade without an explicit new key |
| --- | --- | --- |
| `glass_notifications` | `true` | Preserve existing value; now ordinary notification material only |
| `glass_media` | `false` | Inherit legacy `glass_notifications`, including its old default `true` |
| `notification_lockscreen_effects` | `false` | `true`, preserving the former scene coverage |

Explicit new values always win. `SettingsMigration.resolve` supplies the same
read-only semantics to hooks before the settings App has ever launched. Local
storage, first service binding, publishing, version-1 preset import/export and
reset use the same resolution. The persisted migration marker is idempotent.
Preparing fresh App defaults does not conceal an existing remote configuration.

Upgrading is not an automatic HMC conflict fix. Existing users must turn off
**lockscreen notification effects** for coexistence, and also turn off **music
material takeover** for configuration B. The separate notification-radius and
media press-feedback settings retain their existing responsibilities.

## Ordinary notification scenes

With `notification_lockscreen_effects=false`, Lite yields ordinary notification
material, press tilt, add/remove animations and collapsed-group stacking while
keyguard remains present, including its expanded shade and AOD. Unknown scene
state is conservative. Confirmed unlocked shade can use the existing feature
toggles. Screen power is not a proxy for keyguard state.

Native music first follows its independent material switch. Heads-up notifications
keep their separate animation switch and existing material exceptions: no forced
unified recipe or duplicate blend-alpha adjustment. Other scopes (Control Center,
volume, shade background, Assistant and sidebar) retain their own switches.

The fold-history policy is a separate process initialization policy and is not
tied to this dynamic scene switch. The Java-only class-loading guard and deferred
plugin installation from preview 2 remain unchanged.

## Release and native recalculation

The engine gates OEM setters, legacy blends, unified effects and queued updates
at execution time. Configuration generations and per-row scene/role tokens expire
old work. Notification baselines are discarded at handoff instead of replaying an
effect chosen for an earlier keyguard, AOD or theme state.

Local ownership records compare the last changed properties, observed material
writes, background identity and relevant child structure. Cleanup yields to newer
values or a replacement overlay. This is conservative local bookkeeping, not a
cross-module lock or detection of every possible private write. An unfinished
release survives an unrelated settings refresh; a new owner or re-enabling the
range revokes the old release. Animation cleanup preserves newer property values
and delivers system completion callbacks once.

The device's SystemUI `17.03.260226.r` (202602260) was checked read-only. Its APK
SHA-256 exactly matches the inspected offline artifact:
`074486d6856c8eda2764d8864d99365e178d4a4758836e3c44152862ddc8b7e1`.
Its ordinary notification selector is the four-argument
`ExpandableNotificationRowViewBinder.bindInternal$updateEffect` (interactor, row,
NotificationViewType, ObjectRef). Lite re-enters that selector without clearing
the type enum or the previous-effect reference. An unsupported signature yields
to the next OEM update rather than guessing argument positions.

For music, Lite observes the existing `MediaViewBinder.bind` to retain its current
interactor. It requests the existing view model's background-update event; the
OEM collector selects the current material. It does not install a second
collector or directly resume a cached effect. During `mWillDoAodAnim`, the native
transition owns the material and the request defers to the next OEM update.
Raw decompiler output stays in the project's DevCache, outside this repository
and the personal archive.

## Verification boundary

The HMC reference checked at implementation start was `5bc06f7`
(`0.2.0-preview.15`), which predates its side of this compatibility work. Its
worktree subsequently showed an independent compatibility implementation in
progress, including the corrected ordinary selector. Neither is a passed pairing.
A final HMC commit/APK must be identified before paired
acceptance; neither a host build nor tests across different version combinations
prove device coexistence. Third-party module participation must be recorded; an
unisolated result remains pending isolated validation.

The owner's next acceptance should be limited to:

1. After independent startup acceptance, configuration B: disable Lite music
   takeover and lockscreen ordinary effects, select an HMC custom background,
   then change ordinary notification parameters. Music must remain stable after
   delayed work has had time to run.
2. Configuration A: select all three HMC music defaults, enable Lite music
   takeover, then change its material. Content/lyrics stay with HMC; material
   follows the current Lite/OEM choice without jumping to a prior state.
3. With lockscreen ordinary effects off, expand/collapse/cancel a notification,
   unlock, enter/leave AOD and switch fold displays. No stale transform may remain;
   unlocked effects, heads-up and unrelated panels must keep their own behavior.

Actual paired startup, these transitions, and standalone operation remain device
acceptance items. This document grants no installation, restart or settings-change
authority.
