# Lite settings App

The App uses Miuix native components, semantic colors and the system typeface.
Root navigation has **Home**, **Settings**, and **About**, following the owner's
HMC Duo navigation model without adding a second visual theme system.

Home contains connection status and five feature destinations: scopes, materials,
corners, notification animations and volume behavior. Settings owns App theme,
language, import/export and full reset. About owns version, fork, issue tracker,
upstream and license links. Import/export are not feature-page actions.

Feature editors use one layout at every window width:

```text
App bar
Pinned rendered sample
Pinned horizontally scrollable project tabs when needed
Scrollable peer setting cards
```

There are no external subsection headings, persistent subtitles, preview captions
or separate scene picker. Each setting has one title; compatibility is a short
title marker. Numeric controls use reduced vertical padding and 44 dp operation
regions, retaining exact numeric entry and validation dialogs.

Scopes are a separate Home destination. Material tabs separate glass appearance,
card sampling, full-screen background and card presets. Corner tabs separate
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

App theme supports System/Light/Dark and updates the sample theme. Language uses
Android per-App LocaleManager with System/Simplified Chinese/English; English is
the fallback. Android resources back both Compose and native View preview text.
These preferences never enter the SystemUI remote preference group. Full backups
optionally include App appearance metadata; older backups retain current App
appearance. Appearance metadata is validated before module preferences are written.

Full reset restores all active module values and App appearance; applying or
undoing a card preset does not touch the independent full-screen background.
See [material layers](MATERIAL_LAYERS.md) and [shade scope](SHADE_BACKGROUND_SCOPE.md).
