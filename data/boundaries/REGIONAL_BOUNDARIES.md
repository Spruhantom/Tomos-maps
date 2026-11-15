# Regional clipping boundaries

These polygon files are derived from Geofabrik's published OSM extract clipping polygons:
https://github.com/geofabrik/polygons_download.geofabrik.de/tree/master/polygons/europe/united-kingdom/england

- `greater-london.poly`: Greater London clipping polygon.
- `south-east-england.poly`: union of the Berkshire, Buckinghamshire, East Sussex, Hampshire, Isle of Wight, Kent, Oxfordshire, Surrey and West Sussex clipping polygons. It is a practical regional package footprint, not a claim about a legally authoritative administrative boundary.

The South East footprint explicitly includes the West Sussex polygon, which contains Storrington (approximately 50.93° N, 0.454° W). A regression test checks this point against the committed polygon.

OpenStreetMap-derived packages require ODbL attribution and share-alike review. These boundaries are for clipping/build coverage only; they do not themselves grant redistribution rights for any source data.
