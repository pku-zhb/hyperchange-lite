# Private reference presets

Files ending in `.local.json` are ignored by Git. They may contain an owner's
saved interaction preferences and can be imported through the app's preset menu.
They are never applied automatically or included in a build.

The local saved-interactions preset covers only supported notification and volume
settings. It leaves material tuning to the HyperChanger interface, skips unrelated
and deferred features, and validates every mapped value against the Lite catalog.
Numeric values use Lite's declared increments; OEM volume position offsets retain
their original integer-pixel interpretation. A preset import is not device acceptance.
