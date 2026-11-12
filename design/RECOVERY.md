# Behavioral reconstruction

HyperChanger base: `ColdP/HyperChanger@5d89372aaa1a6192e098be4baf93effff301cb07`.
HyperLight evidence version: 1.2.3 / code 123. Existing local decompiler references
and recovered arithmetic were read without altering the reference project.
No HyperLight APK, decompiled tree, native library, icon, wallpaper or proprietary
preview asset is distributed with this fork.

| Feature | Evidence anchor | Implementation boundary |
| --- | --- | --- |
| Soft glass | HyperChanger `SoftGlassParameters.kt`, material hooks | Retain OEM recipe plus relative tuning; no HyperLight preset conversion |
| Notification tilt | HyperLight `t11`, `yp1`, `ry`, `up1` | Observe native touches; cancel when scroll/dismiss wins |
| Heads-up animation | HyperLight `bc1` | Use OEM type 11071 property channel; keep base row transforms separate |
| Add/remove animation | HyperLight `rc1` | Use OEM FOLME_NOTIF_ADD_REMOVE properties; completion exactly once |
| Group stack | HyperLight `rr2` | Adjust child view states, retaining native group expansion and hidden children |
| Narrow volume motion | HyperLight `dp2`, `ap2`, `va2`, `zf0` | Reconstructed damped springs, width/icon transition, signed-power pop and rubber-band drag/release; preserve native slider input |
| Timer corner | HyperLight `p52` case 0 | `TimerItemRes.getRadius(Context, boolean)` target, guarded by signature |
| Volume position | HyperLight `p52` case 10, `id2` cases 20/21 | Adjust OEM right/top margins in integer physical pixels, not density-scaled translations |
| Interactive preview | HyperLight `z01`, `y21`, `kc1`, `nc1`, `pe2` | New preview content; actual OEM material where available and labeled fallback otherwise |

The implementation preserves mathematical behavior where established. Runtime
target discovery fails closed when OEM classes or properties are unavailable.
Near-identical device appearance is a goal, not a claim from the host build.

Notification animation blur is applied only when the existing OEM RenderEffect
can be read and restored. Otherwise that blur channel is skipped and logged once;
scale, flip and other available channels continue. A local preview may therefore
show animation blur that an unsupported ROM cannot safely reproduce on system rows.
Missing OEM Folme properties keep the original add/remove animation untouched.
