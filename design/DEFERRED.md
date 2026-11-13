# Deferred ownership and compatibility

These are deliberate exclusions, not switches with missing implementations.
The owner will decide their destination before either project implements them.

| Candidate | Why defer | Suggested owner / next decision |
| --- | --- | --- |
| Keyguard unlock cover, wallpaper fade and unlock animation | Shares the cover and keyguard-going-away lifecycle with Duo | HMC Duo candidate; choose owner before adding hooks |
| PIN glass keypad, keypad layout and whole-bouncer blur removal | Alters lockscreen/bouncer composition; keep its ownership decision together | Compare Duo integration versus a separate Lite lockscreen section |
| Per-scene brightness and AOD material overrides | Duo already owns clock/AOD presentation and material forwarding | Decide which module owns scene transitions; do not install competing hooks |
| Lock-screen notification visibility and retention | Already implemented in Duo | Keep in Duo; no duplicate Lite controls |
| Music artwork, lyrics, vertical capsules and shortcut geometry | Already owned by Duo | Keep in Duo |
| Default-theme glass guards | Duo already provides the required theme guard | Keep one owner; Lite currently assumes the OEM/Duo path permits native glass |
| Desktop glass and native Dock | User explicitly deferred launcher compatibility | Separate future milestone; verify target ARM64 signatures before reusing native code |

Gesture-line changes, the separately rendered liquid-glass engine, camera/Leica,
rear display, app navigation bars, OCR, system-setting extensions and the iOS
notification-center replacement are outside this fork's current scope.

## Device acceptance still required

No installation, setting mutation or process restart is part of this source task.
Host tests do not establish visual parity. The next authorized phone trial should
check material sampling, recycled notification rows, heads-up enter/exit, grouped
notification expansion, native swipe/dismiss/scroll, media controls, volume long
press and expansion, and disable/restore behavior. Desktop is not in that trial.

Independent shade RGB override is deferred; the retained HyperChanger interface
controls native blend opacity, blur and scale. Existing material values are not
automatically imported from HyperLight.

## Temporarily suspended in 0.1.4

The owner reported a suspected notification-center interaction with MiDuo Noti
and requested notification grouping be hidden without further repair work.
Collapsed group geometry and fold-history interception are no longer installed;
their controls and previews are absent, regardless of values in an old backup.
Preference keys remain available only for import/export continuity. Group-only
source and tests are retained in the ignored local development archive, outside
the active/public source tree. The conflict has not been isolated on the device;
no additional debugging or maintenance is scheduled until explicitly requested.
