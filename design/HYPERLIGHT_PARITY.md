# HyperLight behavior review

Evidence baseline: HyperLight 1.2.3 (version code 123), read-only local
`hyperlight/reference/jadx/sources`. Class/method anchors identify behavioral
evidence; no decompiled implementation or proprietary assets are included here.
Host arithmetic and lifecycle checks do not establish physical device appearance.

The material baseline comes from HyperChanger's OEM soft-glass recipe and relative
tuning. It is not a conversion of HyperLight material presets, and this fork does
not promise HyperLight-identical material appearance. Preview content is newly
drawn; actual OEM material is used where available and a fallback is identified.

## Notifications

| Borrowed behavior | Evidence anchor | hc-lite result and boundary |
| --- | --- | --- |
| Press direction, scale, depth and camera | `yp1.c/k`, `up1.a`, `t11.p` | Retains normalized point tilt, 320 ms press, 420 ms cancel/long release, 480 ms tap release, and the recovered spring parameters. Observes native dispatch and composes owned transforms instead of replacing existing touch listeners or resetting another owner's values. |
| Nested press arbitration | `up1.b/a` | Corrected descendant arbitration to use an active pointer, rather than animator progress. A child's down event suppresses its parent immediately; a released child's return animation does not block a new parent press. Native scroll/dismiss and multi-touch retain priority. |
| Damped interpolation | `ua2.getInterpolation` | Below damping 1, preserves the recovered response and overshoot. **Intentional improvement:** values above 1 use a real overdamped response; HyperLight maps that entire range to the critical response. This retains the earlier fix for the owner's ineffective parameter range. |
| Heads-up scale/flip/blur channels | `bc1.h`, `xb1.a`, `yb1.a`, `t11.n`, `kb1` case 19 | Preserves dedicated OEM type 11071 and independent durations/curves. Corrected flip/blur exit delays to retain HyperLight's 32 ms minimum; exit scale can still begin at 0 ms. Cancelling an overlay does not complete a newer native heads-up event. |
| Animation blur quantization and update rate | `bc1.a`, `rc1.b` | Corrected positive half-pixel ties to OEM positive-half-up rounding: requested radius 1 becomes 2, and 3 becomes 4. Restored heads-up's 16 ms positive-blur interval; zero and cleanup remain immediate, add/remove remains unthrottled, and only successful writes acquire ownership. **Safety guard:** animation blur requires a readable existing RenderEffect, chains with it, and restores only an owned value; otherwise only this blur channel is skipped. |
| Add/remove property application | `rc1.h`, `zk0.onAnimationUpdate` default, `t11.q` | Preserves FOLME_NOTIF_ADD_REMOVE scale and transition-alpha properties, spring, configured durations and heads-up exclusions. Corrected the missing `getInjector().scheduleUpdateProperties()` call after frames and cleanup. Resolves the updater before replacing native animation; unavailable contracts keep native behavior. |
| Add/remove completion | `kb1` cases 28/29, `b01.onAnimationEnd` default | Retains exactly-once completion through replacement, detach, cancellation and reentrant start callbacks. Native/HMC writes that replace an owned value survive cleanup. Callback ownership is deliberately stricter than the reference's unconditional reset. |
| Collapsed group spacing and height | `rr2.b0/w`, `kb1` cases 11–14 | Corrected spacing to one rounded integer physical-pixel step before multiplying by card index; the same step is used for row placement, overflow offset and height. Retains identity/order checks, independent measurement ownership and native hidden-child filtering. |
| Zero group spacing | `rr2.w`, `kb1` case 11 | **Intentional difference:** zero keeps hc-lite's scale stack with no vertical gap. HyperLight skips its stacking-state/height override when the rounded spacing is zero. The current setting remains a spacing control instead of becoming an implicit feature switch. |
| Collapsed group scale | `rr2.l`, `kb1` case 11 | **Intentional improvement:** hc-lite keeps multiplicative scale (`ratio^index`); HyperLight uses a linear decrement. The multiplicative rule preserves a positive third card at the allowed 0.5 limit and retains the previous repair for the owner's broken stack. |
| Group presentation and retirement | `rr2.O/l/j/u`, `kb1` cases 15–18 | Retains three-card presentation, bottom-center pivots, card order, roundness source and summary suppression. Expansion, reparenting, reordering, replacement, native filtering and scene changes release owned geometry/appearance/measurements. Uses guarded separate ownership rather than reproducing unconditional OEM field resets. Exact OEM depth composition and content/background pivots require device acceptance; this review does not claim visual equivalence for them. |

Music, lyrics, capsules, lockscreen retention and clock/AOD geometry remain owned
by HMC Duo. Deferred desktop effects and other excluded features are not enabled
by this comparison.

## Volume controls

The volume review owns implementation and verification of these contracts. Its
shared state/placement helpers and adapter use were read during this review;
device appearance and final build/test results belong to release acceptance.

| Borrowed behavior | Evidence anchor | Source contract and hc-lite boundary |
| --- | --- | --- |
| First-show and repeated-key width | `dp2.j/k`, `bp2.d` cases 7/10, `id2.e` case 24 | The first show resets the native width and starts the pop without shrinking. Later visible non-touch key updates shrink the matching slider. This differs from the earlier hc-lite unconditional narrow show/refresh and is corrected through a session interaction model. |
| Touch width and native input | `dp2`, `ap2`, `va2`, `zf0`, `i01` | Retains touch-wide and release/cancel-narrow transitions, recovered spring/pop/drag behavior and native slider input. Existing OEM touch listeners remain chained; the reference's listener replacement is intentionally not reproduced. |
| Long-press expansion | `id2.d` case 19, `s6.run` volume branch | HyperLight uses a fixed 300 ms hold, a 0.92 whole-panel press scale, and less than 10 dp cumulative vertical movement before invoking the motion expansion callback. **Existing fork differences:** hc-lite uses the system long-press timeout, cancels on two-axis touch-slop displacement or another pointer, and omits that whole-panel scale. A successful hold sends native slider cancellation before invoking the existing expand listener and consuming the remaining gesture. These preserve the current drag arbitration; no exact timing/feedback parity is claimed. |
| Ringer footer and explicit expansion entry | `bp2.d` case 6, `dp2.j`, `p52` case 10 | hc-lite's explicit always-hide option takes priority in the collapsed side panel; otherwise automatic hiding follows the actual narrow phase, so the first wide show retains native footer controls. Expanded and Control Center panels restore native visibility, and the explicit expand button is hidden only when long-press expansion is enabled. HyperLight's decompiled footer branch contains an empty conditional followed by unconditional visibility writes, so its exact switch gating is not inferred from that branch alone; `dp2.j` does establish narrow/always-hide-dependent pop companions. This round preserves the stated fork boundaries rather than claiming identical footer policy. |
| Physical edge position | `id2.d` cases 20/21, `p52` case 10, `ir` case 10 | HyperLight adjusts native physical-pixel margins around an OEM-dependent baseline; its zero offset is not an absolute edge-flush promise. hc-lite's explicit visible-track edge gap, including zero meaning flush, is an owner-requested behavior extension rather than source parity. Edge placement interpolates with width: the narrow track reaches the chosen gap while the wide/held track retains its native position. |
| Timer and volume-column corners | `p52` case 0 | Retains signature-guarded `TimerItemRes.getRadius(Context, boolean)` and the three-argument volume-column equivalent, with density conversion. Existing hc-lite defaults are 40 dp; the reference defaults are 20 dp. These are historical fork defaults without a confirmed owner decision to match HyperLight; this round does not silently change them or claim default appearance parity. |
| Width restoration | `dp2.k`, `bp2.d` case 10 | The current adapter captures slider/progress/background widths separately before ownership, restores each dimension, and recaptures after reinitialization. This preserves the native layout when a session ends, the feature disables, or the display configuration changes. |
| Volume parameter defaults | `p52` case 10, `dp2`, `ap2` | **Historical differences, alignment undecided:** narrow width 9 versus 10, height 150 versus 160, key nudge 6.5 versus 10, pop scale 0.2 versus 0.5, and pop shift 5.5 versus 10 (hc-lite versus HyperLight). The recovered formula review found no further discrepancy; existing defaults are retained without treating them as confirmed visual parity. |
| Spring stability and cancellation | `va2`, `zf0` | **Safety differences:** the existing solver uses a stricter substep interval (`1/max` instead of `1.6/max`) and at most 32 substeps for stalls. Cancellation does not inject release momentum from a lost pointer. Both preserve the fork's existing stability/input protections. |

## Host verification boundary

This review adds deterministic checks for integer group spacing, heads-up phase
delays, blur quantization and positive-blur throttling with a fake clock. Existing
tests retain positive group scales, real overdamping, native-value ownership and
exactly-once completion coverage. The volume review adds interaction/placement
and independent-width restoration checks. This document records source evidence
and implementation decisions; passing checks and APK identity are reported by the
release workflow, rather than inferred here.
