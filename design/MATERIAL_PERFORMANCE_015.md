# Material editing and backdrop follow-up

## Confirmed input paths

The owner reports more lag when dragging a slider across a larger interval. The
captured SystemUI log contains repeated `Invalid resource ID` errors for generated
small integer View IDs, with a peak near 3,000 messages/second. `GlassSurfaces`
previously asked `Resources.getResourceEntryName` for those IDs before rejecting
module-owned ancestors. The resource-package guard now rejects generated IDs
without a native lookup; a bounded, weak-Resources cache also keeps successful
and missing real resource names from repeating expensive lookups/exceptions.

This is a proven unnecessary hot path, not proof that every observed delay or
process disappearance has the same cause. A separate HMC pre-draw resource-name
lookup hotspot was observed in the sampled call graph and is fixed in HMC. The
owner explicitly deferred a separate investigation of the earlier SystemUI exit.

The slider already throttles user updates. Its serial settings queue now keeps
only the latest queued write for each key, while sealing batches around import,
export, reset, presets and module service binding. Coalescing cannot move an edit
past those barriers or discard another key. The final duplicate value skips
publication only after local state is initialized; an explicit fresh default
still claims local ownership rather than allowing an old remote value to win.
Failed synchronization remains retryable. Hook-side material settings now read
one immutable preference snapshot per refresh, rather than reading it repeatedly
for every numeric field.

App appearance imports/reset use application-owned persistence and a live
preference observer in the current Activity. A job started by a destroyed
Activity can update the newly created Activity after folding/rotation. Export
reads the saved appearance rather than a potentially older rendered snapshot.

## Volume dismissal

The OEM's `dismissVolumePanelAnimation` is an entry into an exit animation, not a
notification that the column is already hidden. The old hook restored native
width/position at that entry, exposing the original wide pose for a frame. The
animator now freezes its current pose through dismissal and restores only after
native hide/detach, a new show, or an explicit feature/expanded-mode handoff. No
new gesture interception or changes to OEM dismissal timing are added.

## Backdrop zero tint

Zero extra background scale is valid: it means no additional OEM zoom, not an
empty texture or a transparency control. The preference/backup key stays intact;
the UI now names it **extra zoom**. The schematic preview previously reversed
that direction and zoomed most at zero. It now follows the local native preview's
extra-zoom baseline and direction; it remains a schematic, not pixel equivalence.

The retained HyperLight `ShadeDimColorHook` recipe preserves a neutral mode-21
structural transform and substitutes only mode-106 tint/dimming. The old Lite
whole-background path instead scaled every blend entry's alpha, including that
structural entry. The whole layer now scales only mode 106, leaving mode 21 and
unknown transforms untouched. Batch colors, `setMiSelfBlur`, legacy clear/add
and native App shade preview share this policy. Card sampling keeps its separate
existing opacity behavior. Solid background alpha still follows its actual OEM
setter within the strictly expanded/unlocked shade scope; a heads-up-only window
never grants access to that channel.

The exact retained SystemUI APK
`074486d6856c8eda2764d8864d99365e178d4a4758836e3c44152862ddc8b7e1`
confirms `ShadeSolidBackgroundController.updateWindow` writes the solid color.
Its generated `ProvideShadeWindowBackgroundFactory` resolves `shade_background`,
which is already classified correctly. This excludes an initially suspected
missing root classification; no broad window-class exception was added.

The blend mismatch is confirmed in source. Whether it fully explains the owner's
white-background observation still requires native GPU acceptance. No percentage
range is presented as a guarantee of performance or visual transparency.

## Host checks and scope

New fixtures cover generated IDs/cache ownership, a single settings snapshot,
per-key queue coalescing and barriers, fresh-default edit ownership, dismissal
session state, structural/tint blend separation and fallback zoom endpoints.
The root delivery workflow runs the complete host suite and packaging checks.
No phone GUI, screenshots, settings mutations, installation or scope reload were
used to develop these fixes. OEM output and exit-animation appearance remain
owner-operated acceptance.
