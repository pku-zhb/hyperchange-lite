# Extraction plan

Baseline: ColdP/HyperChanger commit 5d89372aaa1a6192e098be4baf93effff301cb07.
This is an authorized semantic reduction, not a behavior-preserving file migration.

One module entry dispatches SystemUI and its plugin ClassLoader, Personal Assistant,
and SecurityCenter. There is no framework, launcher, camera, wallpaper, application
navigation, built-in HMC, or native-binary entry. Shared preferences are the only
settings transport. Native MiGlass recipes remain owned by the OEM; the fork retains
HyperChanger's tuning formulas and samples only intended surfaces. Clock glyphs and
Duo layers must never be selected by broad notification call-stack matching.

Feature ownership: GlassHooks / glass package owns material baselines and refreshing;
NotificationEffects owns recovered notification interaction; ControlVolumeHooks owns
radii and volume controls; SettingsRepository owns validation and local/remote sync;
MainActivity owns settings and local previews. Each runtime owns its install guards
and lifecycle cleanup. No feature restarts packages.

Risks to verify: plugin loader timing, target ROM method signatures, gesture priority,
view recycling, baseline restoration, and SystemUI/AOD transitions. Compilation alone
is not device acceptance.
