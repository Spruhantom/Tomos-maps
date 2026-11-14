# Ferrostar navigation migration

## Decision

Ferrostar is the selected turn-by-turn navigation SDK for TomOS Maps. This is a navigation-layer change, not a replacement of the map renderer, data pipeline, postcode system or offline package format.

Ferrostar is BSD-licensed and provides an Android SDK with a navigation core, reusable Compose UI components and a MapLibre-specific UI module. TomOS will use the core and renderer-independent Compose components first. It will not adopt Ferrostar's MapLibre view module because the application already renders maps with MapLibre Compose.

Upstream references:
- https://github.com/stadiamaps/ferrostar
- https://stadiamaps.github.io/ferrostar/android-getting-started.html
- https://stadiamaps.github.io/ferrostar/route-providers.html

## Components retained

| Component | Decision |
|---|---|
| MapLibre Compose | Keep as the Android and Linux map renderer |
| Valhalla | Keep as the primary offline routing engine |
| GraphHopper | Keep as an optional alternative routing backend |
| UK postcode index and search | Keep as the preferred UK destination lookup |
| Photon | Optional place/address geocoder; do not replace local postcode search |
| MBTiles regional packages | Keep as the current installed map format |
| Protomaps / PMTiles | Retain as an optional distribution/packaging investigation |
| Signed MAP / ROUTING / SEARCH layers | Keep unchanged |
| Existing regional download manager | Keep unchanged |

## Platform plan

### Android

Integrate Ferrostar's Android `core` and renderer-independent `ui-compose` artifacts. Do not add `ui-maplibre` unless a specific component is needed and proven compatible with the existing MapLibre Compose runtime.

The existing Android Valhalla engine runs in-process through JNI and does not expose an HTTP endpoint. Therefore, do not configure Ferrostar to call a hosted Valhalla service or a public endpoint by default. Implement a Ferrostar `CustomRouteProvider` that delegates route calculation to the existing local Valhalla JNI runtime and adapts the resulting route into Ferrostar's route model. Preserve offline-only behaviour when a verified regional routing layer covers the journey.

The existing UK postcode resolver remains responsible for converting a full postcode to coordinates. Ferrostar receives the resolved destination waypoint; it does not become the postcode database.

Use Ferrostar's navigation state machine for route progress, step advancement, off-route detection and maneuver lifecycle after the adapter has passed route-parity tests. Migrate voice guidance to Ferrostar's speech observer only after confirming UK-English voice selection, lifecycle cleanup, stale GPS handling and foreground-service behaviour. Avoid running two independent progress trackers or speaking duplicate prompts during migration.

Use the platform-neutral Compose components only where they fit the current TomOS interface. Preserve the existing MapLibre map and route overlay.

### Linux desktop

Keep the existing Valhalla local-service runtime, GPS progress controller and eSpeak voice adapter operational while the Android migration proceeds.

Ferrostar's Rust core is portable, but the upstream high-level Android SDK is not a ready-made Linux desktop SDK. A Linux integration requires a maintained Rust/native bridge, packaging for supported Linux architectures, lifecycle and location adapters, and tests against the existing Valhalla service. Do not claim Ferrostar parity on Linux until those pieces compile and run in the desktop application.

### Routing backend selection

- Offline route covered by an installed verified package: use the local Valhalla graph.
- Optional online Valhalla: only when explicitly configured by the user and permitted by the selected provider's terms.
- Optional GraphHopper: separate configured backend; it must not silently replace offline Valhalla.
- No silent plaintext or untrusted endpoint fallback.

## Migration stages and acceptance gates

1. **Dependency and compatibility spike**
   - Pin a released Ferrostar version from Maven Central.
   - Confirm compatibility with the repository's Kotlin, Compose, AGP and Android SDK versions.
   - Add only the Android `core` and `ui-compose` artifacts.
   - Confirm the existing MapLibre Compose dependency graph remains conflict-free.

2. **Local Valhalla adapter**
   - Implement `CustomRouteProvider` over the existing in-process Valhalla JNI engine.
   - Convert Valhalla maneuvers, geometry, distance, duration and waypoints into Ferrostar route objects without dropping maneuver metadata.
   - Add deterministic adapter tests using recorded Valhalla responses.
   - Verify the adapter works with networking disabled and a matching installed routing package.

3. **Android navigation state**
   - Feed validated GPS updates into Ferrostar's location provider.
   - Use Ferrostar for step progression, rerouting/off-route state and maneuver selection.
   - Keep TomOS arrival, stale-fix and GPS recovery semantics explicit and tested.
   - Ensure one owner for each navigation state to avoid duplicate progress updates.

4. **Voice and UI**
   - Integrate UK-English speech with correct Android lifecycle handling.
   - Preserve foreground-only location and voice behaviour unless background navigation is separately approved.
   - Reuse renderer-independent Compose navigation components while retaining the existing MapLibre renderer and TomOS layout.
   - Test permission denial, TTS unavailable, pause/resume, stop and disposal.

5. **Linux native-core evaluation**
   - Evaluate the Ferrostar Rust core behind a Linux-native bridge.
   - Keep the current Linux route and speech path as the fallback until native integration passes tests.
   - Do not ship a partially wired or unmaintained FFI layer.

6. **Release validation**
   - Android and Linux builds and tests pass independently.
   - Offline route calculation succeeds with airplane mode enabled and a valid installed regional package.
   - Route geometry, distance, duration and maneuvers match the existing Valhalla result within defined tolerances.
   - GPS progress, off-route handling, stale-fix recovery, arrival and UK-English spoken instructions are exercised.
   - Physical Android navigation testing is completed safely before production claims.

## Current implementation status

This document records the approved architecture decision only. The application has not yet been migrated to Ferrostar: existing Android and Linux navigation code remains active, and no Ferrostar runtime integration or successful build is claimed by this change. The dependency spike and local Valhalla adapter are the next implementation tasks.

## Licence and attribution

Ferrostar is distributed under the BSD licence. Preserve its licence and notices in redistributed builds. Map data, tile services, routing services and geocoding data retain their own separate licences and terms; Ferrostar's software licence does not grant rights to those datasets or services.
