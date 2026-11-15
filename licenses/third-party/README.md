# England routing data third-party notices

This directory preserves the upstream licence texts for the software and supporting data used by the England Valhalla routing build. It is a provenance and notice bundle, not a legal opinion or a determination that every generated file has only one applicable licence.

## Component inventory

| Component | Upstream project | Notice / licence | Treatment in the build |
|---|---|---|---|
| OpenStreetMap England extract | OpenStreetMap / Geofabrik | ODbL 1.0; attribution to © OpenStreetMap contributors | The routing graph is generated from this database. Keep source URL, extract checksums and ODbL notice with any distributed graph. |
| Valhalla routing engine | Valhalla | MIT; see `VALHALLA-MIT.txt` | Software licence; does not replace the source-data licence. |
| Timezone boundary builder code | evansiroky/timezone-boundary-builder | MIT; see `TIMEZONE-BOUNDARY-BUILDER-MIT.txt` | Builder software only. |
| Timezone boundary output polygons | evansiroky/timezone-boundary-builder | ODbL 1.0; see `TIMEZONE-BOUNDARY-DATA-ODBL.txt` | Generated boundary data is a separate data component. |
| IANA Time Zone Database | IANA / tzdb | Public domain unless an individual file states otherwise; see `IANA-TZDB-LICENSE.txt` | Preserve the upstream notice and inspect any exceptions in files actually included. |

## Required attribution and licence links

- OpenStreetMap copyright and attribution: https://www.openstreetmap.org/copyright
- Open Database License v1.0 full text: https://opendatacommons.org/licenses/odbl/1-0/
- Geofabrik technical and licence information: https://download.geofabrik.de/technical.html
- Valhalla licence source: https://github.com/valhalla/valhalla/blob/master/COPYING
- Timezone boundary builder code licence: https://github.com/evansiroky/timezone-boundary-builder/blob/master/CODE_LICENSE
- Timezone boundary data licence: https://github.com/evansiroky/timezone-boundary-builder/blob/master/DATA_LICENSE
- IANA tzdb licence source: https://github.com/eggert/tz/blob/main/LICENSE

## Release gate

Before distributing a generated graph, review the exact build's source and image provenance, retain all notices with the graph, confirm the database/derivative-database classification and ODbL obligations, and inspect IANA tzdb file-level exceptions. This engineering inventory does not settle whether the Valhalla graph or combination of graph/admin/timezone databases is a derivative database. Obtain qualified legal review before publication.

The ODbL text is linked rather than duplicated here; the ODbL notice permits inclusion of its URI with the database and relevant documentation. Do not remove the attribution or licence links when packaging the review bundle.


## Offline basemap and postcode additions

| Component | Upstream licence / terms | Release treatment |
|---|---|---|
| OpenStreetMap / Geofabrik extracts and derived vector tiles | ODbL 1.0; see the OSM copyright page and ODbL URI below | Preserve OSM attribution, source provenance and the ODbL notice. Review whether the packaged tiles/graph are derivative databases and meet share-alike obligations. |
| OS Code-Point Open (Great Britain) | Open Government Licence v3.0 | The required attribution includes OS, Royal Mail and National Statistics statements. The Liverpool builder inserts all three with the current year. |
| ONS postcode products containing BT postcodes | ONS postcode-product terms plus separate Land & Property Services (LPS) terms for Northern Ireland | Do not redistribute BT postcode records on the strength of GB/OGL evidence alone. ONS states that commercial use requires a separate LPS licence and that its NI end-user licence is for internal business use. Obtain written permission explicitly covering the intended app distribution before including BT records. The UK index builder now requires separate NI permission evidence. |
| Noto Sans Regular glyph PBFs from OpenMapTiles fonts | Original Noto Sans font: SIL Open Font Licence 1.1 | The glyph download script fetches and validates the upstream OFL text and copyright notice; the glyph package includes OFL.txt. Do not sell font files separately. |

Useful official terms:
- OS OpenData terms and attribution: https://docs.os.uk/os-downloads/resources/site-resources/terms-and-conditions
- OS Code-Point Open attribution: https://www.ordnancesurvey.co.uk/products/product-support
- ONS postcode-product licensing (including the BT/LPS distinction): https://www.ons.gov.uk/methodology/geography/licences
- LPS Central Postcode Directory licensing: https://www.finance-ni.gov.uk/articles/digital-3-application-supply-and-licence-central-postcode-directory-cpd-terms-and-conditions
- Noto Sans OFL source: https://github.com/google/fonts/blob/main/ofl/notosans/OFL.txt

The current Liverpool regional package uses the GB-only Code-Point Open extract clipped to Merseyside; it does not require BT records. A future UK-wide index must not be advertised as distributable until NI rights are confirmed separately.
