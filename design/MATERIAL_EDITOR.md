# Unified material editor

## Brief and design decision

All controls that change a material belong to one Material entry. Notification and
volume behavior keep separate pages. The editor has one preview pinned at the top
on narrow, wide and landscape windows; only the categories and controls scroll.
Existing keys, validation, presets, undo, import, export and reset stay intact.

The visual system remains native Miuix Compose. The system typeface and Miuix type
scale carry labels, values and summaries. Existing semantic colors remain the
authority; the preview's original coast palette uses deep teal `#396575`, sea
green `#72A3A2`, pale sand `#D2DCBD`, white `#FFFFFF` and ink `#1E2931`. These are
preview content colors, not a new application theme.

```text
Small app bar: Material editor                         Configuration
Fixed preview image: selected scene, current material and geometry
Fixed compact status and day/night, background, replay controls
---------------------------------------------------------------
Scrollable area:
  Scene dropdown
  Category dropdown
  Selected category: presets / scope / parameters / geometry
```

The preview is the one visual focus. Native preference groups, left aligned
Chinese labels and familiar sliders do the rest. Dropdown categories avoid an
endless horizontal strip, and all controls in the selected category are visible
without a second hidden advanced-parameters layer. Short windows reduce the
preview height while retaining an independently scrollable settings area.

## Review against the brief

A split pane was considered for wide windows, then rejected because the requested
position is explicitly above the controls. The same pinned-top structure is used
at every width. Scene selection and category navigation live below the preview so
they do not consume the fixed viewing area. Presets are a category in this editor,
not a separate destination with another preview.

## Acceptance boundary

Host compilation and unit tests verify source integration, category completeness,
unchanged preference ownership and preset behavior. Device installation, UI
operation and screenshots are outside this source-only round. Visual acceptance
on a narrow screen and a landscape inner display remains a separate check.

## Implementation critique

The finished source keeps the preview outside the settings `LazyColumn`; there
is no wide-window exception. Scene/category dropdowns scroll with the parameters,
while the preview caption retains the scene and current range state. Presets and
trial restoration are controls in the same editor, with no second preview page.
The previous advanced-parameter collapse is absent from material categories.

No preference specification, persisted key, repository or hook consumer changed.
Normal-notification material remains the only native sample recipe, explicitly
identified in the caption. Missing OEM APIs still produce a labeled schematic
preview rather than a claim of complete scene fidelity. Source and 80 host tests
support this integration; they do not establish a visual device result.
