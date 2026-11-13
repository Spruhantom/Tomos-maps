# UK postcode search

TomOS Maps uses the Postcodes.io public API for UK postcode-prefix search and
postcode geocoding.

- Endpoint: `https://api.postcodes.io/postcodes?q={prefix}&limit={n}`
- Prefix matching is case-insensitive and returns matching postcode records.
- The client normalises case and whitespace, limits results to 20 and validates
  returned WGS84 coordinates before exposing them to the map UI.
- A postcode identifies a geographic postcode unit, not an individual property.
  Property-level address search requires a separately licensed address dataset.
- The API is a network service. Search requires connectivity unless a future
  offline postcode dataset is installed.
- Source: Postcodes.io API documentation
  (https://postcodes.io/docs/postcode/query/).

The shared client lives in
`composeApp/src/commonMain/kotlin/uk/co/tomosmaps/search/UkPostcodeSearch.kt`.
