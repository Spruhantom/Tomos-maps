# Offline download manager rebuild

**Status: in development; feature remains disabled.**

On 15 November 2026 the user authorised the download-manager rebuild after the England routing candidate passed its five-route CI build and independent artifact verification.

## Verified routing candidate

- Builder commit: `b69d726efcc335c629a0faa9052e13d9c4032e89`.
- Workflow: [England routing build #43719482594](https://github.com/Spruhantom/Tomos-maps/actions/runs/43719482594).
- Five route smoke tests passed: Liverpool–Manchester, London–Birmingham, Bristol–Exeter, Newcastle–York and Nottingham–Sheffield.
- Five routing parts were downloaded and checked independently. Each part's size and SHA-256 matched the manifest.
- Reassembled `tiles.tar`: 2,240,839,680 bytes; SHA-256 `36b34c198edf3946ac526384bad6b5f41e617a242a7c191961d9d978f4153bf0`.
- All 14 metadata asset entries, including the four bundled third-party notices, passed size and SHA-256 verification.
- The build's release/publish steps were skipped. This candidate is not publicly released.

## Existing implementation discovered

The repository already contains substantial foundations: area planning and tile estimates, MapLibre offline-region storage, per-layer package manifests, signed catalogue verification, resumable transfer clients, per-layer installers, progress/pause/resume/remove APIs and offline search/routing adapters. These are not yet a complete usable feature: the app entry point does not expose the manager, catalogue URLs are blank, the UI and regional-download configuration are disabled, and the current England routing candidate is not a complete map-plus-search package.

## Rebuild scope

1. Select an area from the map or a named preset; start with Liverpool City Region.
2. Select map, roads/routing and UK postcode/search layers independently.
3. Show separate download and installed-storage estimates, plus combined totals. Never invent estimates for unpublished layers.
4. Stream downloads with progress/speed, pause, resume, retry and delete; preserve partial files safely across process restarts.
5. Verify signed catalogue, manifest identity, archive length, per-file hashes and final archive hash before atomic installation.
6. Keep installed map, routing and search layers independently removable; never delete a working installed version before its replacement verifies.
7. Integrate offline map rendering, postcode search and routing only when matching verified coverage is installed.
8. Validate Android and Linux separately, including airplane-mode tests and interrupted-download recovery.

## Safety and publication gates

- `DOWNLOAD_MANAGER_UI_ENABLED` and `REGIONAL_DOWNLOADS_ENABLED` remain false until the complete workflow is ready.
- Do not bulk-download from the public OpenStreetMap standard tile server. Map tiles must come from a source/build pipeline whose terms explicitly permit the intended offline use and redistribution.
- The England Valhalla graph is routing data only; it is not a basemap or postcode/search index. Do not present it as a complete offline region.
- Complete human review of ODbL treatment, attribution and every bundled component licence before publishing any OSM-derived graph or map package.
- Keep the catalogue empty and publisher URLs unset until real map, routing and search assets exist and the signed catalogue is deployed.
- Do not claim offline readiness until Android and Linux have been exercised with networking disabled.

## Initial implementation step

A separate development branch wires the existing `OfflineRegionManager` into the app shell behind `DOWNLOAD_MANAGER_UI_ENABLED=false`. This preserves online-only production behaviour while allowing the manager integration to be compiled and reviewed. Subsequent work should close the missing data/catalogue and reliability gaps before the gate is enabled.

## QMapShack engineering reference

QMapShack (Maproom/qmapshack) was reviewed as an implementation reference for its BRouter tile-selection workflow and disk cache. The useful behaviours are per-tile local/remote/outdated state, visible size and progress, selecting outdated data, explicit deletion, resumable downloads and bounded cache storage.

TomOS does **not** copy QMapShack source files. Its repository currently has no root-level project licence declaration, while QMapShack is GPL-3.0-or-later. Until TomOS's own licensing and compatibility are explicitly settled, reusing its implementation would create avoidable licence uncertainty. The TomOS implementation remains independently written in Kotlin and retains the existing signed MAP / ROUTING / SEARCH package model.

### Downloader hardening inspired by the review

- Treat HTTP range metadata as untrusted: a resumed response must declare the exact requested start byte, final archive byte and signed total archive size before any bytes are appended.
- Reject malformed, truncated or mismatched Content-Range responses rather than trusting a prefix match.
- If a process stopped after the complete archive was written but before verification/install, verify and install the full-sized partial archive directly instead of requesting an invalid range starting at EOF.
- Keep Android and Linux desktop transfer implementations in parity and exercise the recovery path with a deliberately incorrect range-total response followed by a valid retry.

This is a reliability improvement to the transfer engine, not permission to publish packages or enable offline downloads. The feature flags and release gates remain unchanged.
### Version-aware package management

The manager now compares each installed layer's recorded version with the currently signed catalogue descriptor. A newer signed version is shown as an update, and the user can download it without first deleting the working installed layer. Transfer progress takes precedence in the combined summary while an update is active. The existing installer remains responsible for verified installation/replacement; the manager does not expose deletion of an update's partial file through the layer-removal action because that action also removes the installed layer.

