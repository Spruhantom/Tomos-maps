# UK map-data hosting

## Selected architecture

TomOS Maps uses a hybrid GitHub hosting layout:

1. **GitHub Pages** serves the small public catalogue at `/catalogue/uk.json`.
2. **GitHub Releases** stores large, versioned data assets (for example PMTiles vector maps, Valhalla routing extracts and postcode/search indexes).
3. The catalogue is the index of truth. Each asset entry must provide a stable release URL, SHA-256 checksum, exact byte size, format, layer, geographic coverage, dataset version, source and licence.
4. Clients must verify the checksum and expected size before accepting an asset. Release assets are immutable by version; publish a new release rather than replacing an existing asset.

## UK coverage and layers

The catalogue is UK-wide (`GB`) from the start. Packages can be split into sensible geographic regions and independently versioned layers:

- `map`: vector map rendering data (PMTiles or another explicitly supported format).
- `routing`: routing graph extract, with the routing engine and graph format declared.
- `search`: postcode/address index, with source and update date declared.

No package is listed until a real, legally distributable asset has been built and uploaded. The empty initial package list is deliberate; it avoids advertising non-existent downloads.

## Publishing

The `map-data-pages.yml` workflow validates the catalogue on pushes and pull requests. On pushes to `main`, it deploys the static `data/` directory to GitHub Pages. In repository Settings → Pages, select **GitHub Actions** as the publishing source if it is not already enabled.

To publish data, generate the regional/layer assets using a licensed data source and a reproducible build pipeline, calculate SHA-256 and byte size, upload the assets to a versioned GitHub Release, then add matching entries to `data/catalogue/uk.json`. Never bulk-download from the public OpenStreetMap standard tile service.

## App integration boundary

This is hosting/catalogue infrastructure only. It does not re-enable map downloads, offline search or offline routing in the Android/Linux app. Those remain disabled until explicitly scoped and separately implemented. Existing online OpenStreetMap rendering and configured online routing remain unchanged.

## Operational limits and attribution

Keep metadata on Pages and large binary assets in Releases, not Git history. Preserve OpenStreetMap attribution and comply with ODbL and each source dataset's terms. A GitHub-hosted asset is not automatically licensed for redistribution.
