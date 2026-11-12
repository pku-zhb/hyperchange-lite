# Plugin discovery and startup recovery

## Preview 1 regression

The owner's startup report showed SystemUI waiting inside Lite's global
`ClassLoader.loadClass` callback while initializing
`kotlin.text.StringsKt__StringsJVMKt`. The callback repeated in the blocked stack
before `ActivityThread.handleBindApplication` completed. LSPosed subsequently
entered its automatic safe mode after repeated startup failures.

The Lite rewrite weakened the upstream discovery guard. HyperChanger's
`handleLoadedClass` sets its reentrancy flag before class matching and feature
dispatch. Preview 1 instead called Kotlin `startsWith` before setting its flag,
protecting adapter installation but leaving discovery itself unprotected. This is
a regression introduced by the Lite extraction, not an unchanged upstream path.

The trace establishes recursive class loading and an initialization wait. It does
not establish a complete two-thread deadlock cycle or show Duo drawing code on the
blocked path. Another module could still influence initialization timing.

Raw device reports and identifiers remain in the local diagnostics directory;
they are not distributed with the source or preview archive.

## Repair

- Both loadClass overloads share one preconstructed Java Hooker.
- The thread-local guard starts before the original call and remains active through
  matching and scheduling, then clears in `finally`.
- Nested callbacks only call the original operation once. They do not match names,
  queue adapters or clear the outer guard.
- Java `String.startsWith` replaces Kotlin extension calls on this path. The hook
  is registered directly without a Kotlin Function1 wrapper.
- Adapter installation runs through an asynchronous main-thread queue. The global
  callback does not synchronously initialize Kotlin adapters under class-loading
  or class-initialization locks.
- Identity-based pending/installed/failed states deduplicate each loader. Rejected
  scheduling clears pending state; failed installation is terminal for that loader
  to avoid a startup retry/logging storm.
- Discovery failure retains the successful original result. Original class-loading
  failures propagate unchanged. R8 keep rules preserve the Java callback boundary.

Eight host regressions cover nested overloads, first-use helper initialization,
original exceptions, scheduler throws/rejection, inline scheduler rejection,
concurrent threads, failed-adapter storms and unrelated/bootstrap classes. The
compiled Java callback was also checked for absence of Kotlin references.

These tests exercise the callback logic, not the actual ART/LSPosed initialization
cycle. Device startup acceptance is still required.

## Recovery and acceptance boundary

The owner has prohibited agent-controlled scope restarts, phone reboot, GUI control
and screenshots. The incident handoff additionally requires a reviewable fix plan
before leaving LSPosed safe mode. Preparing or archiving this candidate does not
authorize either action. Keep the recovered phone state until the owner decides
when to run a controlled scope-only test.

The next owner-approved trial should proceed in this order:

1. Establish successful SystemUI startup with Lite, checking startup completion,
   module-loading logs and absence of the recursive class-initialization wait.
2. Open Control Center and volume once, then recreate/reopen their views. Asynchronous
   discovery can occur after a plugin's first constructor or radius assignment;
   first-open behavior must be checked, not assumed from compilation.
3. Verify coexistence with the owner's Duo configuration and disable/restore behavior.

Do not claim that an installed APK is loaded into existing scopes, or that the
automatic LSPosed recovery is a repair applied by this project.
