# AOD and native-island performance

Preview 5 addresses the CPU hot path observed with HMC Preview 16 / Lite Preview 3.
It retains Preview 4 material presets and their existing settings semantics.
The UI editor redesign is isolated on a separate worktree/branch.

## Evidence and changes

A read-only, user-operated reproduction captured 7,151 SystemUI CPU samples over
90 seconds at 199 Hz. All Android animation scales were 1.0. The caller graph
connects AOD blend setters through Lite role classification to repeated
`Class.getMethod` and exception stack construction. Inclusive sample shares were
13.29% for `GlassSurfaces.headsUp` and 15.09% for `Throwable.fillInStackTrace`.
These overlapping CPU-cycle shares are not additive, frame times or a speedup.
The verified ROM has `isHeadsUpState()` and lacks `isHeadsUp()`; the previous loop
retried the missing alias whenever the primary getter returned false.

- Resolve heads-up and focus accessors once per runtime Class, including missing
  methods. A supported primary getter is authoritative even when false. Read
  the role and injector instance on every use; never retain their boolean value
  across operations.
- Cache notification Field/Method accessors by Class and signature, including
  inherited missing results. Keep field values live and preserve lookup order,
  private accessibility and invocation/access-error behavior.
- Capture material facts and notification permission once during a setter's
  pure preparation. Invalidate local facts on parent or scene/role revision
  changes; release all local memo state in finally before native execution.
  Re-read current notification state after the native call, including role
  changes that do not publish a generation signal. Queued tasks get fresh reads.
- Construct internal setter ownership only after a successful native call rather
  than both before and after it. Keep the external MAML path's prior contract.

HMC Preview 17 independently reduces a complete owned AOD palette's five extra
legacy replays to one and removes global hierarchy discovery from unrelated
material setters. The fixed candidate pair is HMC Preview 17 / Lite Preview 5.
Neither side changes animation duration scales or depends on hook registration
order. Existing Java-only class-loading protection remains intact.

## Host validation and device boundary

All 96 JVM tests passed across 18 suites, with no failures, errors or skips.
Debug, R8 Release and Release vital lint completed successfully. New coverage
checks positive/missing accessor caches, inheritance/overloads/concurrency, live
role changes, nested/failed local preparation and revision invalidation.

The signed preview is archived and delivered separately. No installation,
scope reload, settings mutation, screenshots or GUI automation establishes a
post-fix device pass in this iteration. Keep those claims pending until the owner
uses the exact fixed pair. Only recheck AOD entry/wake, native-island smoothness
and correct material/role handoff; unrelated behavior and UI need no repeat.
