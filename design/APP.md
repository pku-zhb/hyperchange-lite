# Lite settings App

The App uses Miuix native components, semantic colors and the system typeface.
Root navigation has **Home**, **Settings**, and **About**, following the owner's
HMC Duo navigation model without adding a second visual theme system.

Home contains connection status and six feature destinations: scopes, card materials,
pull-down background, corners, notification animations and volume behavior. Settings owns App theme,
language, import/export and full reset. About owns version, fork, issue tracker,
upstream, license links and the owner's WeChat donation image. Import/export are not feature-page actions.

Adjustment editors use one layout at every window width:

```text
App bar
Pinned rendered sample with short action hint and local-background controls
Pinned horizontally scrollable project tabs when needed
Scrollable purpose-based setting groups with advanced controls folded
```

Necessary summaries explain prerequisites, direction/baseline, units and module
ownership; self-explanatory controls remain title-only. Inactive child controls
hide without resetting their values. Advanced optics and animation curves begin
collapsed. Static notes have their own text style instead of resembling controls.
Numeric controls retain compact padding, 44 dp operation regions, exact input,
default reset and visible default/slider/legal ranges. See [the current settings
specification](SETTINGS_UX.md) for the detailed presentation contract.

Scopes are a separate Home destination with controls only: no rendered sample
or preview allocation. Material tabs separate glass appearance,
card background and presets. The independent pull-down background has its own
Home destination and full-panel preview. Corner tabs separate
notification, control-center and volume radii. Legacy-only settings never become
visible merely because they appear in an imported backup.

Control-center corner previews begin with the original composite sample. Only a
real value edit chooses a component; opening an input dialog or scrolling does
not. The entire preview target, not only the sample selector, remains stable
while scrolling. Other editors retain automatic contextual previews.

Volume preview geometry follows the actual side panel: the width-controlled
vertical track, then independent mute and DND pills below it. The narrow phase
hides companion controls; first-show and subsequent-level events are distinct.
The preview illustrates an OEM contract without claiming identical device pixels.

App theme supports System/Light/Dark and their wallpaper-color variants, and
updates the sample theme. Its own theme page also controls floating navigation
and App-local blur. Language uses
Android per-App LocaleManager with System/Simplified Chinese/English; English is
the fallback. Android resources back both Compose and native View preview text.
These preferences never enter the SystemUI remote preference group. Full backups
optionally include App appearance metadata; older backups retain current App
appearance. Appearance metadata is validated before module preferences are written.

About uses the module's own logo/version, donation and companion-project entry.
Credits and complete offline AGPL/Apache/NOTICE texts are separate pages. Third-party
names remain where attribution or a real online API/source requires identification;
ordinary feature ownership uses generic module descriptions.

Full reset restores all active module values and App appearance; applying or
undoing a card preset does not touch the independent full-screen background.
See [material layers](MATERIAL_LAYERS.md) and [shade scope](SHADE_BACKGROUND_SCOPE.md).
