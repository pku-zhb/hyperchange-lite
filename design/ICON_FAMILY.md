# HC launcher icon family

The hc-lite mark derives from HyperChanger's orange three-device launcher motif:
`ColdP/HyperChanger`, `app/src/main/res/drawable-nodpi/hc_home_launcher.png`.
The upstream project is licensed under AGPL-3.0; its attribution and project
license remain in place. The launcher is independently redrawn as native vector
geometry, retaining the original solid orange `#FF7700` against `#FFFDF9`.

The shared family uses a rounded phone silhouette. Lite retains the two outer
panels and adds two fine adjustment tracks. HMC Duo uses a play control and a
separate vertical capsule. The marks contain no letters or clock text.

- `drawable/hc_launcher_foreground.xml` owns the transparent 108 dp adaptive layer.
- `drawable/hc_launcher_monochrome.xml` preserves controls as transparent cutouts.
- `drawable/ic_launcher.xml` provides a standalone rendering of the same mark.
- Both launcher and round-icon resources use the same layers. Android 13 and
  newer select the monochrome-enabled adaptive resource.

The complete foreground geometry fits inside the centered 66 dp circular safe
zone. Host previews are rendered from the Android path data at 48 px and larger,
including circular and superellipse masks. These checks validate the retained
geometry and alpha mask; launcher-specific appearance still depends on the
device's own mask and themed-icon colors.
