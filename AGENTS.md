# hc-lite

## Latest visual investigation workflow

The owner authorizes screenshots and recordings for independent visual review.
A fresh reviewer without inherited conversation history should capture and inspect
the relevant behavior; the coordinating root receives text findings and should
not view the images/video. Root may grant that reviewer an exclusive device lease,
overriding the older root-only operation convention below for the review's duration.
Other agents must not operate the phone concurrently. Never reboot or bypass
authentication; restore temporary settings/playback and preserve floating windows.
Watch complete transitions first and use slow motion/selected frames where useful;
exhaustive frame-by-frame inspection is unnecessary. Verify capture fidelity before
calling missing AOD/glass layers a rendering defect. Keep raw evidence in DevCache,
not synced Drive/Git, and integrate readable findings into the existing rolling
document. Distinguish observed visuals, source findings and causal hypotheses.
When research before implementation is requested, present the report before app
changes. A live sampler or successful tests alone do not prove readable text.

This repository is the owner of hc-lite, an intentionally reduced fork of
ColdP/HyperChanger. Its package is `com.zhuhuibin.hyperchange.lite`.
The fork is `pku-zhb/hc-lite`; its owning checkout is
`/Volumes/Harbor/lab/android/hc-lite`, with default build output in
`/Volumes/Harbor/DevCache/hc-lite`. Keep the existing `hyperchange_lite`
preference group and `hyperchange-lite` JSON format for upgrade compatibility.

- Keep only SystemUI/Control Center materials, notification behavior, volume controls,
  Personal Assistant widgets and SecurityCenter sidebar material. Desktop work is deferred.
- HMC Duo owns music, lyrics, capsules, lockscreen notification retention and clock/AOD
  geometry. Record ownership candidates in `design/DEFERRED.md`; do not implement them
  in either project until the owner chooses the destination.
- Preserve source attribution and licenses. HyperLight decompilation is behavioral
  evidence; do not redistribute its APKs, decompiled trees or proprietary assets.
- Keep settings in small feature-specific files; every exposed setting must have a
  consumer, validation, reset and export/import coverage.
- Use `tools/build.sh` for builds. All caches and outputs belong on mounted Harbor
  DevCache. Never create the volume mount locally.
- Source work alone does not establish device acceptance. Follow the standing
  phone authorization below, and never reboot the phone.
- Keep phone identifiers, logs, snapshots and investigation artifacts outside the repo
  and synced Drive. Store reusable implementation notes in `design/` in English.
- Commit coherent milestones with imperative conventional subjects and push to this
  fork. Do not publish to upstream or create releases without explicit authorization.
- Keep UI prose in Chinese with simple labels. Recreate preview behavior using our own
  drawing/content and actual OEM glass APIs; distinguish fallback previews honestly.
- The settings App uses actual Miuix Compose components. Keep a clear feature home,
  vertically grouped controls and contextual previews. The owner now requests a
  horizontally scrollable, single-tap project tab row under the pinned preview in
  every second-level editor. Preserve the application ID and migrate renamed
  preference keys for upgrades and old preset imports.
  Keep Home/Settings/About root navigation; scopes, materials and corners are separate
  Home destinations. Import/export, theme and language belong in Settings. Editors
  use compact controls, short necessary summaries and purpose-based groups. Hide
  inactive child parameters and collapse advanced optics/curves by default. Keep
  prerequisites beside their controls; mirror existing masters without changing
  catalog ownership or saved keys. Static notes must not resemble controls.
  Control-center corner samples change only after actual edits, never from scroll
  focus. Keep Chinese/English resources complete for both Compose and native previews.
  The owner requested new unified launcher art for hc-lite and hmc-duo; preserve the
  existing launcher/LSP entry and adaptive/round/monochrome resource contracts.
  Card materials and the actual pull-down backdrop have separate destinations.
  Background previews must show a full panel over App content, not a glass card.
  Preview hints are short user actions; native diagnostic status stays accessible
  without occupying the visual editor. Preview wallpaper controls remain local.
- Global class-loading callbacks must keep their Java-only, callback-entry guard and
  defer adapter installation; see `design/STARTUP_SAFETY.md`. Preserve the upstream's
  critical startup ordering when pruning features instead of rewriting it casually.
  Keep raw ANR evidence out of commits. Preserve startup guards during scope
  reloads and follow the standing phone authorization below.
- Before delivering each APK version, run `tools/archive-apk.py`. Retain the signed
  versioned APK under `releases/local/` and copy it to the owner's existing
  `~/Harbor Drive/02 Personal Files/Miduo/` archive. Verify equal SHA-256 hashes and
  never overwrite a different build under an existing version. Increase versionName
  and versionCode for each delivered app change; preserve the signing identity.
- Use ordinary release versions and the filename `hc-lite-<version>.apk`. Keep
  source hashes in embedded metadata and receipts, with `release_name` equal to
  the full source commit. Preserve historical artifact names and receipts.
  Use `(major * 10000 + minor * 100 + patch) * 1000` for stable versionCode values.
- Keep machine receipts and checksums in the project. The personal archive contains
  installable APKs only, not acceptance notes, raw logs or phone dumps.
- End every development round with a concise Chinese report. For bug fixes, explain
  the symptom, confirmed cause (or explicitly labeled hypothesis), and remedy. For
  new or changed behavior, give 1-3 concrete test actions and expected results. Test
  only this round's changes unless a specific regression justifies repeating a check.
  If no app code changed, say so rather than generating a duplicate app version.
- The owner grants standing phone authorization to the coordinating root agent:
  replace-install already archived APKs with standard `adb install -r`, reload
  related scopes, save and restore necessary test parameters, and perform normal
  background tests or a small amount of GUI interaction when needed. Verify the
  installed package/version after installation. Do not downgrade, uninstall,
  clear data, use staged/reboot-dependent installation, or reboot the phone.
  Avoid screenshots wherever possible; the main conversation should avoid viewing
  GUI screenshots. Ask the owner only for required personal authentication or
  repeated visual acceptance that cannot be established from background state.
  This authorization persists until the owner explicitly revokes it; do not ask
  again before each ordinary authorized installation, scope reload or test.
  Subagents coordinate all device operations through the root agent rather than
  accessing the phone concurrently.

New APK filenames use the short project name and ordinary X.Y.Z version, without preview labels or source IDs. Keep source identity inside the APK and local receipts. Preserve historical APKs and names.

## Personal APK archive retention

Keep at most the latest five versions per module, including the current version,
in `~/Harbor Drive/02 Personal Files/Miduo/`. Group historical and short-name
aliases as one module; sort by semantic version, not filename text or mtime.
Retire matching old version-specific test notes with their APKs. Keep third-party
inputs and the repository's `releases/local/` packages/receipts outside this cap.
Use Obsidian's local vault trash, never permanent deletion. The CLI delete command
currently selects system trash; for explicit local trash use the official CLI
`eval` entry with `app.vault.trash(file, false)`. Verify source absence and retained
file hashes; on interruption inspect the actual result before retrying.

## Independent material layers

Card glass/sampling and full-screen shade background have separate values,
enable state and refresh requests. Card presets/undo or partial imports must not
modify the full-screen layer. Preserve the strict real-expanded-panel gate from
0.1.3; a heads-up-visible window never authorizes whole-screen tuning.
Ordinary lockscreen material and lockscreen animation permissions are separate.
The animation opt-in defaults off and requires a settled awake screen; material
permission alone never authorizes animation on the lockscreen. Preserve current
native AOD selection and fresh ownership-aware cleanup.
Notification grouping/fold-history behavior is suspended at the owner's request.
Keep its keys only for backup compatibility; do not expose controls, install hooks
or resume compatibility work without a new request. Retired group code lives only
in the ignored local archive, with the suspected MiDuo Noti interaction recorded.

## Latest owner delivery preferences

Archive APKs locally and copy to the reachable phone Download directory. Do not
send APK attachments through cc-connect. Do not create Markdown notes in the
personal Miduo archive; keep acceptance work in the single rolling document below.
Use the standing phone authorization above for installation, related scope
reloads and tests; an explicit owner revocation changes that boundary.

## Single rolling acceptance document

Maintain all future acceptance work in the one existing document:
`~/Harbor Drive/00 Temp/2026-11-14 - hc-lite 0.1.7 与 hmc-duo 0.2.6 本轮验收.md`.
Update that same file in place, keeping the current pending checks, confirmed
results and installation state together. Keep using this exact path across future
versions; do not create per-version acceptance files or parallel checklists.
Do not recreate older acceptance documents that the owner has deleted. Do not
write Markdown into the personal Miduo APK archive. This documentation rule adds
no device privileges beyond the standing phone authorization above.
