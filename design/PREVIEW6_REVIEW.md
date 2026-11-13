# Preview 6 usability and behavior review

## Scope and baseline

The owner requested a full Lite review because navigation, wording, parameter
ranges and several effects were difficult to use. The concrete examples were
oversized slider ranges and ringer/DND controls remaining beside a thin volume
bar. The unified material-editor branch is now merged into main; Preview 5's
accepted HMC ownership, startup guards and performance caches remain intact.

## Findings and remedies

| Finding | Confirmed cause | Change |
| --- | --- | --- |
| Hard to fine-tune ordinary values | Sliders exposed the entire legal/import range; no one-step action | Separate small common editing ranges from persisted limits, add unit labels and +/- controls, retain exact full-range entry |
| Existing presets include extreme values | Upstream recipes deliberately use values outside the new editing windows | Keep all values and preset fixtures; require an explicit action to move into the slider window |
| Material configuration was scattered | Separate presets, scopes and per-feature geometry routes | One Material entry, top pinned sample, scrollable categories and all-material search |
| Many animation settings were hard to find | One long family with an arbitrary first-N subset | Task sections for press, enter/exit, flip/blur, curves, row appearance and groups; explain inactive dependencies |
| Wording promised more than the switch controls | Independent unification/Assistant/sidebar controls were conflated with the system tuning switch | Describe each actual owner; identify relative values and the HMC music/lockscreen boundary |
| Thin volume bar retained ringer/DND controls | Panel visibility consulted only explicit hide-ringer, not thin-bar mode | Auto-hide the ringer/DND area in the ordinary collapsed thin panel; preserve the full panel, Control Center and expansion entry |
| Notification damping above 1 had no effect | Every damping >= 1 used the critical-damping formula | Add the true overdamped response, keeping all underdamped behavior and endpoint semantics |
| Cleared glass could return after a settings refresh | A null OEM recipe bypassed source/ownership retirement | Treat null as the same native-clear event as an empty/zero recipe |
| Preview did not match disabled effects | Local interactions ignored notification/volume feature gates | Use actual press/row/media/group/heads-up/volume switches; show ordinary width when thin mode is off |
| Preview jumped or replayed obsolete values while tuning | An old local animator continued writing captured inputs after layout refresh | Retire playback before refreshing, reset transforms/blur, release parent touch interception; replay uses current parameters |
| Volume position changes looked ineffective in idle preview | Offsets were applied only by the animation frame callback | Apply gated offsets in the static layout as well |
| Large-font preview captions could clip | Initial normal mode skipped caption-height calculation | Measure caption line height on first configuration, including normal mode |
| Opening/rotating the App could wait on synchronization | Bind published the full preference catalog on the main thread; repository creation acquired the same IPC lock | Serialize binding/death with edits on the existing worker; remove the redundant Activity-time migration lock |
| A sync error sounded like a lost edit | Local commit success and remote publication failure shared one generic error | Report saved-locally/not-synced separately, retain normal retry behavior and log detailed causes outside UI copy |

## Compatibility and release identity

No stored preference key, validation range, default, built-in recipe value,
application ID, module scope or signing identity changes. Common slider windows
are presentation only. Existing out-of-window values remain visible and can be
edited with exact entry or single steps; they are never silently clamped.

This review does not add any deferred launcher, lockscreen or PIN ownership.
Native preview uses the existing normal-notification sample, explicitly labeled;
it does not claim a pixel-identical recipe for every OEM scene or verify GPU
output. Known wallpaper-versus-local-artwork composition limits are unchanged.

Deliver from a clean committed and pushed source tree. Embed its revision in
manifest metadata, use that revision as the release name and suffix the retained
APK filename with its short hash. Receipts remain in the owning repository;
raw logs remain in DevCache. No GitHub release is published.

## Validation and focused acceptance

The integrated review passed 118 JVM tests in 22 suites, Debug packaging,
R8-minified Release packaging and release vital lint before the version bump.
The final versioned build and artifact verification are recorded separately.
These host checks do not establish visual/device acceptance.

Only test this round's changes:

1. Open Material, keep the sample visible while scrolling, search a parameter,
   drag within its common range and use +/- or exact entry. An old extreme
   preset value must remain unchanged until explicitly edited.
2. Enable thin volume mode: ordinary side controls should disappear together;
   expanding the full panel restores ringer/DND controls. Disabling thin mode
   restores ordinary behavior unless explicit always-hide remains enabled.
3. Toggle one notification effect and compare its local preview. Disabled
   effects should stop; replay after numeric changes should use current values.

The user controls module enabling and scope reload. App installation alone does
not replace hooks already loaded inside an existing scope process. No phone GUI,
screenshot, process restart or whole-phone reboot is part of host validation.
