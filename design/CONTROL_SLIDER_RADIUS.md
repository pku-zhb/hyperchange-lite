# Control-center slider corners

## Confirmed native contracts

The retained control-center plugin exposes independent `setOutlineRadius(float)`
and `setProgressRadius(float)` methods on ToggleSliderViewHolder and
BrightnessPanelSliderDelegate. Their native view-outline providers read different
fields for the background and the filled progress. They serve different purposes:
the enclosing track has adjustable corners, while the moving progress boundary
retains the OEM's flat-edge clipping.

ToggleSliderViewHolder.updateSize explicitly calls both setters. Its separate
updateResources replaces both progress/background drawables without a radius
setter. Material/configuration changes can therefore also replace the drawable
under a previously customized field. Brightness has a corresponding material
background/size lifecycle. Exact primary method evidence is retained outside Git
under the owning development cache, not redistributed as decompiled source.

## Repair and limits

- Version 0.1.7 incorrectly supplied the track override to both setter channels
  and the secondary volume progress getter, rounding the moving fill boundary.
  Only the track outline/container now consumes the configured slider radius.
  Progress setters/getters and the fill drawable retain their native values.
- Background and fill still retain independent latest-native baselines. Resource
  and theme replacement may replay each native input, but replay does not grant
  fill customization or change its drawable's corner radius.
- Exact constructor, resource, size, material and show callbacks reapply their
  current native input through the existing setter adapter. A reentry guard
  prevents lifecycle recursion. No drawing/touch hook or per-frame tree scan is
  added. Disabled settings do not acquire new lifecycle ownership.
- The existing current-property baseline restores each channel independently;
  a verified updateSize resource write becomes its new native baseline even if
  the px value equals the last override (24dp to sw340dp's 48dp is a real case).
  Ordinary self-replays cannot acquire that permission. Secondary volume
  getter routing now resolves its actual secondary container and progress radius.
- Preference refreshes are coalesced and limited to radius/volume edits, avoiding
  unrelated material-slider writes repeatedly replaying native radius calls.

Focused state regressions check flat fill-boundary ownership, independent track/fill restoration, resource
replay without baseline recapture, and native density/qualifier changes including
same-value native inputs. Full
Android compilation/lint and native visual acceptance are separate checks. This
iteration performs no phone access, installation or scope restart. The source
fix does not claim that an unobserved third-party direct native write is intercepted.
