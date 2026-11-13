# HC function icon family

The common solid orange `#EA6500` derives from HyperChanger's `#FF7700`,
slightly darkened to keep small white function glyphs distinct.
The function glyphs are independently drawn native vectors: a musical note and
album outline for HMC Duo, transparent overlapping glass panes and a reflection
spark for hc-lite, and an open wrench for miduo-compat. Each mark uses one large
recognizable function rather than a miniature device silhouette. Existing
upstream attribution and licences remain unchanged.

The adaptive foreground uses a 108 dp viewport and the common warm white
`#FFFDF9`. Round icons share its layers, and monochrome vectors retain alpha
cutouts for native themed icons. The original resources remain in their owning
project; no raster, screenshot, font or third-party asset is bundled.

The compatibility module remains headless. Its icon is displayed by Android and
LSPosed without adding an activity, launcher intent or device operation. HMC's
About image and Lite's legacy drawable use the same glyph as their launcher.

Host vector/XML and mask checks are separate from device launcher acceptance.
