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

## Preview 6 usability review

The review found three navigational failures: independent scene/category selectors
could leave an unrelated preview above the setting, category-local search hid
controls elsewhere, and inactive child parameters looked effective. Notification
animation controls also shared one long group and the arbitrary first-six rule
hid the distinctions between entry, exit and curve tuning.

Keep the existing Miuix semantic palette: light page `#F7F7F7`, card `#FFFFFF`,
primary `#3482FF`, dark page `#000000`, card `#242424`, primary `#277AF7`. Use
system sans and the existing title/body/caption scale; left-align controls and
reserve trailing space for values. Keep the fixed preview as the only visual
focus. This is a system settings tool, so recognition and truthful state matter
more than decorative novelty.

```text
Material and corners
  Fixed sample + one-line renderer status
  Optional sample controls (collapsed initially)
  Scrollable: enabled ranges / presets / glass / geometry
    Global material search
    Category description and context
    Named sections with dependency messages
    Familiar-range sliders + single-step adjustment + numeric entry
```

The initial plan retained two equally prominent dropdowns. That repeated the
existing ambiguity, so the revised layout starts at enabled ranges, labels the
sample selector as preview-only, and makes each category's purpose explicit.
Cross-category search is available from the same editor. Inactive child values
remain stored but cannot misleadingly appear active; the missing parent switch
is named beside them. Notification entry/exit and curve sections use stable keys,
not title matching. Existing settings, export/import and hook ownership remain
unchanged. Device visual acceptance remains separate from source verification.
