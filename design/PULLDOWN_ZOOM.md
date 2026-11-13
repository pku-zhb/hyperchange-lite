# Pull-down backdrop zoom: source investigation

Status: pending a verified wallpaper or background-surface consumer. No control,
preview transform or runtime hook is installed by this investigation. In particular,
`glass_shade_scale` remains a retired sampling-ratio compatibility key; it is not
an App, wallpaper or full-window zoom control.

## Verified current-ROM contracts

The retained SystemUI APK is SHA-256
`074486d6856c8eda2764d8864d99365e178d4a4758836e3c44152862ddc8b7e1`.
Exact methods were inspected with REA's Android method provider, and DEX field/method
references were independently checked without running the APK.

`NotificationShadeDepthController.computeBlurAndZoomOut()` returns a blur radius
and a 0..1 zoom value. Its name alone does not identify the zoom's eventual target.
Two native paths consume that value:

1. `applyZoomOutForFrame.doFrame()` calls `onZoomOutChanged()`. That method writes
   `KeyguardInteractor.repository.zoomOut`. `KeyguardInteractor` exposes the same
   flow; `KeyguardRootViewModel` merges it into `scaleFromZoomOut`. The collector in
   `KeyguardRootViewBinder$bind$2$1$6$1.emit()` calls `setScaleX()` and `setScaleY()`
   on its **keyguard root ViewGroup**. It does not call WallpaperManager or change
   the background App's surface. Altering the depth value here would tune the
   wrong layer and could change lockscreen content.
2. With `isBlurredWallpaperOnShadeSupported`, `scheduleUpdate()` converts zoom via
   `zoomOutAsScale()` and writes `WindowRootViewBlurRepository.scaleRequestedByShade`.
   The conversion is `1 - zoom * 0.05` when Home is focused, otherwise
   `1 - zoom * 0.025`. The repository flows into `WindowRootViewModel._blurScale`.
   In this shipped APK the constructor creates the final scale `transformLatest`
   flow and discards it: there is no retained field or collector applying it to a
   window, wallpaper or SurfaceControl. A support flag and a flow's name do not
   establish a working surface transform.

The SystemUI and Control Center plugin DEX method-reference inventories contain no
direct `setWallpaperZoomOut`, `setBackgroundBlurScale` or `setBlurScale` calls. This
is a bounded direct-reference result, not proof about arbitrary reflective or
external service behavior. The retained `KeyguardPanelViewController.setWallpaperZoom`
callers belong to lockscreen/full-AOD geometry and are outside the requested
unlocked pull-down scope. The external WMComponent's optional AppZoomOut controller
has no verified shade connection here and is not repurposed for task transforms.

## Required implementation boundary

The intended feature is independent pull-down background zoom intensity, with
100% preserving the native request, not scaling notification cards. If a wallpaper
consumer is found, expose it honestly as wallpaper-only rather than promising
foreground-App zoom. Keep the existing real-expanded, unlocked, same-window scope;
heads-up-only windows, the lockscreen and AOD must not acquire this transform.

Disable/close must request a fresh native calculation from its current owner and
current window. Never replay a cached zoom float across a fold, root replacement,
new native write or scene change. A new setting must be excluded from old-export
completeness and legacy-scope inference, and its default must preserve native
behavior. A preview must transform only the demonstrated background layer.

No such setting is exposed yet: adding a slider around the keyguard-root flow or
scaling a preview/card would falsely imply the requested runtime capability.
No phone access, installation, scope reload or device acceptance was performed.
