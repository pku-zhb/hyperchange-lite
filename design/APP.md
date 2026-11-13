# Lite settings App

The App keeps Miuix semantic colors, native components and the system typeface.
Every feature has one vertical layout on narrow, wide and landscape windows:

```text
App bar
Pinned rendered sample only
Pinned horizontally scrollable project tabs
Scrollable title-only controls
```

The same structure applies to material/radius, notification effects and volume.
Each project is a directly selectable tab, using Miuix's native scrollable TabRow.
Selecting a tab updates its representative preview and starts its controls at the
beginning. Only the rendered sample and tab row remain above the scrolling list.
Tab labels retain font scaling, a minimum touch height and selection semantics.
There is no independent preview-scene selector, caption, status card inside the
preview, separate preview page, or permanently visible playback controls.

The first visible setting while scrolling selects its representative surface.
Editing a setting selects that exact key and keeps priority until actual list
scrolling resumes. Motion inputs automatically replay their own enter/exit,
press, width, pop or nudge effect after saved edits settle. Shared material
parameters choose ordinary notifications as their core sample.

Persistent subtitles and instructional paragraphs are absent. Known HMC music
and lockscreen range conflicts use only a short title marker. Disabled state
retains non-visible accessibility information; necessary numeric validation and
reset confirmation remain dialogs. Preview native/schematic metadata lives in
accessibility rather than occupying the adjustment area.

Existing values, full numeric validation, common slider windows, import/export
and the launcher/LSP entry remain compatible. The new physical-pixel volume edge
gap migrates the old OEM-dependent horizontal offset without exporting the alias. Preview behavior
cannot enable hooks, restart scopes or change a system effect by itself.
