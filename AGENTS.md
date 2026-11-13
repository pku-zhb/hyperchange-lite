# hc-lite

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
- No phone installation, settings mutation or process restart is authorized by source
  work. Never reboot the phone. Separate host verification from device acceptance.
- Keep phone identifiers, logs, snapshots and investigation artifacts outside the repo
  and synced Drive. Store reusable implementation notes in `design/` in English.
- Commit coherent milestones with imperative conventional subjects and push to this
  fork. Do not publish to upstream or create releases without explicit authorization.
- Keep UI prose in Chinese with simple labels. Recreate preview behavior using our own
  drawing/content and actual OEM glass APIs; distinguish fallback previews honestly.
- The settings App uses actual Miuix Compose components. Keep a clear feature home,
  vertically grouped settings and contextual previews; do not return to an endless
  horizontal group strip. Preserve the application ID and preference keys on upgrades.
  The owner confirmed the launcher icon works; retain its existing launcher/LSP entry.
- Global class-loading callbacks must keep their Java-only, callback-entry guard and
  defer adapter installation; see `design/STARTUP_SAFETY.md`. Preserve the upstream's
  critical startup ordering when pruning features instead of rewriting it casually.
  Keep raw ANR evidence out of commits. Do not leave LSPosed safe mode or reload scopes
  while startup acceptance remains pending without the owner's explicit instruction.
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
  installable APKs and short Chinese acceptance notes, not raw logs or phone dumps.
- End every development round with a concise Chinese report. For bug fixes, explain
  the symptom, confirmed cause (or explicitly labeled hypothesis), and remedy. For
  new or changed behavior, give 1-3 concrete test actions and expected results. Test
  only this round's changes unless a specific regression justifies repeating a check.
  If no app code changed, say so rather than generating a duplicate app version.
- Current phone authorization permits ordinary replacement installation after the
  versioned APK is archived. Install the retained APK with normal `adb install -r`;
  verify the installed package/version and report success or the exact blocker.
  Do not downgrade, uninstall, clear data or use a staged/reboot-dependent install.
  Do not restart any scope or other process, change device/module settings, reboot,
  operate the GUI, or capture screenshots. All other phone access remains read-only.
  Tell the user that existing scope processes may still run the previous hooks until
  they choose to reload them. Later explicit user instructions can change this boundary.
- If the owner takes over installation, copy the verified APK to `/sdcard/Download/`
  and stop installation retries for that delivery. This handoff is currently active
  for `0.1.0-preview1`; its ADB installation was rejected by the phone's installer.
  Verify the downloaded file hash without opening the file manager or installer UI.

New APK filenames use the short project name and ordinary X.Y.Z version, without preview labels or source IDs. Keep source identity inside the APK and local receipts. Preserve historical APKs and names.
