# TomOS Maps hosted data

This directory contains small, versioned metadata only. Large generated map assets must not be committed to Git.

- catalogue/uk.json: UK-wide catalogue published through GitHub Pages.
- Large vector/routing/search assets: attach versioned files to a GitHub Release and reference immutable release-download URLs from the catalogue.
- Every asset entry must include a SHA-256 digest, byte size, format, layer, coverage and licence/attribution details.

The UK map layer has a manual generation workflow in .github/workflows/build-uk-map-layer.yml. It uploads a review artifact by default. Publishing to a GitHub Release and registering the asset in the public catalogue requires explicitly selecting publish_release.

Routing and postcode/search remain separate stages. Do not list a package until a real, legally distributable asset has been built and validated. This data pipeline does not enable offline downloads in the app or imply that all UK datasets are already available.
