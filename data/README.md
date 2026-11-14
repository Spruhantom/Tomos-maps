# TomOS Maps hosted data

This directory contains small, versioned metadata only. Large generated map assets must not be committed to Git.

- `catalogue/uk.json`: UK-wide catalogue, published through GitHub Pages.
- Large vector/routing/search assets: attach versioned files to a GitHub Release in this repository and reference their immutable release-download URLs from the catalogue.
- Every asset entry must include a SHA-256 digest, byte size, format, layer, coverage and licence/attribution details.

The initial catalogue intentionally contains no downloadable packages. This repository change does not enable offline downloads in the app or imply that UK map, routing or search datasets have already been generated.
