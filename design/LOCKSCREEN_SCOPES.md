# Lockscreen material and animation scopes

The existing `notification_lockscreen_effects` preference now names notification
material permission only. Its saved values and legacy material migration remain
compatible. `notification_lockscreen_animations` is a separate default-false
opt-in; neither an old full export nor a material-only partial preset enables or
resets it implicitly.

Previously press tilt and row animations reused material scene permission, which
also exempted media/heads-up roles from the ordinary lockscreen gate. Heads-up
sessions explicitly ignored scene generations. Material and animation permission
could therefore disagree with the user's intended HMC ownership boundary.

Tilt, heads-up overlays and row add/remove now all require a confirmed unlocked
scene, or a confirmed settled non-dozing keyguard with the animation opt-in.
Unknown state, active controller transitions and AOD always retain native
animations. All roles carry animation-specific scene/role epochs; leaving the
scene or disabling the opt-in restores owned active transforms and blur. Material
permission changes alone do not invalidate an otherwise valid animation epoch.

Focused policy/migration tests cover the default-off split, settled keyguard,
unknown/AOD/transition denial, legacy imports and partial presets. Production
source is checked on the host. This iteration does not connect to or operate a
phone; visual/native completion behavior remains owner acceptance.
