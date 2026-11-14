# Merseyside clipping polygon

`merseyside.poly` is the Geofabrik clipping polygon used to produce the Merseyside regional extract. It is deliberately used as a clipping boundary, not represented as a legal administrative boundary.

Source:
- https://github.com/geofabrik/polygons_download.geofabrik.de/blob/master/polygons/europe/united-kingdom/england/merseyside.poly
- Geofabrik documents that its `.poly` files describe the clipping boundaries used for extracts and can be used with Osmium: https://download.geofabrik.de/technical.html

The build pipeline downloads the North West England OpenStreetMap PBF from OpenStreetMap France, verifies the provider-published MD5 and PBF structure, then uses this polygon with `osmium extract --strategy=smart` to create a Merseyside-scoped PBF. The polygon file is committed so a fallback build does not depend on fetching boundary geometry at build time.

OpenStreetMap data is licensed under the Open Database License 1.0 (ODbL). Attribution and share-alike requirements apply to distributed derivative databases:
https://www.openstreetmap.org/copyright
