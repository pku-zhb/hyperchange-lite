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
