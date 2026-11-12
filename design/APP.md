# Miuix settings App

The owner confirmed that the launcher entry works. Preview 2 replaces the crowded
settings interaction; it does not introduce a second app, change the package name,
or treat the launcher as a confirmed bug.

## Design intent

Use Miuix's own visual language and components. The palette follows its semantic
theme roles: light primary `#3482FF`, white cards `#FFFFFF`, light page `#F7F7F7`,
dark primary `#277AF7`, dark cards `#242424`, and dark page `#000000`. System sans
and Miuix typography provide one consistent hierarchy. Titles and settings remain
left aligned; numerical values and actions have their own trailing area.

Navigation follows the user's task rather than the implementation's setting keys:

```text
Feature home
  Material editor
    One fixed top preview, at every window width
    Scrollable scene / category selection and material controls
    Presets / scopes / surface / background / notification / control / volume / widgets
  Notification behavior / Volume behavior
    Vertical settings sections and optional interaction previews
  Configuration
    Import / Export / Reset
```

The preview is the app's distinctive interaction. Keep the rest quiet and familiar:
real Miuix grouped cards, preferences, switches, sliders, buttons and dialogs. The
material preview stays above the controls on narrow, wide and landscape windows;
short windows reduce its height. Categories use a dropdown and never a large
horizontal strip. Each material category exposes all its parameters directly.
See [the editor brief and acceptance boundary](MATERIAL_EDITOR.md).

## Boundaries

- `SettingsRepository`, preference keys and JSON version remain unchanged, so an
  ordinary package update retains the existing configuration.
- Every exposed catalog setting must remain reachable through the new grouping.
- Keep exact numeric entry, reset confirmation, SAF import/export and user-selected
  preview images. Preview operations do not enable hooks or restart scopes.
- Host a `GlassPlayground` using `AndroidView`; update it only when inputs change,
  and release its animation/image work when the preview leaves composition.
- Compose and Miuix belong to the settings App. Material and interaction hooks do
  not depend on UI state or Compose lifecycle.
- No phone GUI automation, screenshots, scope restart or setting changes are used
  to validate this refactor. Report host checks separately from user acceptance.
