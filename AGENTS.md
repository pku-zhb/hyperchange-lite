# HyperChange Lite

This repository is the owner of HyperChange Lite, an intentionally reduced fork of
ColdP/HyperChanger. Its package is `com.zhuhuibin.hyperchange.lite`.

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
- Before delivering each APK version, run `tools/archive-preview.py`. Retain the signed
  versioned APK under `releases/local/` and copy it to the owner's existing
  `~/Harbor Drive/02 Personal Files/Miduo/` archive. Verify equal SHA-256 hashes and
  never overwrite a different build under an existing version. Increase versionName
  and versionCode for each delivered app change; preserve the signing identity.
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
