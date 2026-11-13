# Settings presentation in 0.1.6

The owner supplied a combined hc-lite/hmc-duo usability specification on
2026-11-14. Its principles supersede the earlier title-only editor convention:
show concise explanations where prerequisites, baselines, units or module
ownership are not obvious; omit redundant prose on self-explanatory controls.

## Structure and persistence

- Home retains Scopes, Card materials, Pull-down background, Corners,
  Notification animations and Volume panel. Home/Settings/About remain root tabs.
- `SettingEditorPlan` organizes controls by stable keys, never translated titles.
  Mirrored card/backdrop masters reuse the same stored settings without changing
  the catalog's unique ownership. Groups hide inactive children without resetting
  values. Advanced optics, curves and volume pop details start collapsed.
- Scopes groups distinguish card tuning, the independent full backdrop,
  Assistant/sidebar and optional lock-screen/music ownership. Deferred notification
  grouping/fold-history and the retired shade-scale key stay hidden.
- Numeric dialogs expose default, usual slider window and legal range, including
  units. Changed values are marked and each dialog can restore its default.
  Existing 111 setting keys, defaults, validation ranges and preset values remain
  unchanged. The separate top-banner glass feature adds an opt-in key, default off.
- Necessary summaries and disabled reasons actually render in the UI. Notes are
  plain text, not dead preference rows. All visible explanations, group labels,
  presets and format arguments are checked in default/English/Chinese resources.

## Preview contracts

One rendered sample remains pinned above horizontal single-tap project tabs.
Control-center radius samples change after real edits, not scroll focus. Short
action hints overlay the sample; technical native/fallback status stays in
accessibility rather than consuming several lines of the editor.

The pull-down scene draws App content, an independently blurred/dimmed full
backdrop and unchanged foreground cards. Both controls at zero reveal clear App
content. This local schematic establishes which object changes; it is not a
WindowManager/GPU parity claim. The OEM preblurred screenshot fallback only has a
provable transparent endpoint, not an adjustable already-baked blur kernel.

Palette/image controls are wired to the actual preview scene, including the
full backdrop. They do not modify the phone wallpaper. Native card sampling uses
its original scale-ratio API; the fallback no longer invents an artwork zoom to
represent that ratio. Pixel-based volume offsets stay in px, preserving imports;
there is no unsupported fixed px-to-dp conversion or sampling-power claim.

## Verification boundary

Tests cover control reachability, expected mirrors, known gates, independent
Assistant/sidebar scopes, spring/curve dependencies, exact card/backdrop separation,
resource completeness, formatting signatures and configuration round trips.
Android builds/lint verify integration. Owner testing still determines layout
comfort, large-font clipping, real glass rendering and native interaction quality.
No phone GUI or screenshots are used for host validation.
