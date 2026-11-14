# Custom offline map areas

The Offline maps manager supports an area centred on the current map view (or
coordinates entered by the user). The initial presets are Small (1 km radius),
City (3 km radius, selected by default) and Large (5 km radius). Each selection
creates a rectangular WGS84 bounding box and requests a MapLibre tile pyramid
from zoom 10 through zoom 16.

## Layer selection and estimates

- **Map background** is downloadable for a custom area. The estimator counts
  the Web Mercator tile pyramid at every requested zoom and displays a
  conservative 8–24 KiB per tile range. This is a planning estimate, not a
  provider quote: actual bytes depend on the local density, style resources,
  compression and cache responses.
- **Roads and routing** can currently be obtained only from a published,
  signed regional package. The app does not yet generate custom-area Valhalla
  graphs or estimate their size independently.
- **UK postcodes and places** can currently be obtained only from a published,
  signed regional package. Custom-area postcode/place index generation is not
  yet available.

The UI keeps these layer limitations explicit. Selecting routing or search
does not silently add them to a map-only tile download; use the Regional data
manager for the existing combined regional package.

## Provider limits and download management

The current OpenFreeMap preview integration enforces a maximum of 3,000 tile
requests or 50 MiB per offline pack, with at most four simultaneous resource
requests. The planner estimates the tile pyramid before enabling a download;
the resource pipeline remains the final authority and can stop a pack if
actual provider requests exceed its limits.

Custom map areas are stored in MapLibre's native offline database. Existing
pause, resume, progress, refresh and delete operations remain available. Online
map browsing remains the default when no installed offline style is selected.

The initial implementation uses a coordinate-centred rectangle rather than
a draggable map polygon. A later UI enhancement can add direct drag-to-select
bounds without changing the shared area-planning model.
## Per-layer package sizes

Published regional package manifests declare the size of every file. The app
now groups those verified manifest sizes into three independent totals:

- **Map:** MBTiles plus MapLibre glyph resources.
- **Roads & routing:** the Valhalla graph plus routing support databases.
- **Postcodes & search:** the local postcode/place search index.

These figures are exact manifest-declared uncompressed bytes for the published
regional package, and are shown separately from the archive download estimate.
They are not presented as a custom-city estimate: a custom area does not yet
have its own routing-graph or search-index build pipeline. The city-area screen
continues to estimate map tiles and explicitly directs users to the signed
regional package manager for roads/routing and search. This avoids inventing
size figures or implying that unavailable custom layers were downloaded.
