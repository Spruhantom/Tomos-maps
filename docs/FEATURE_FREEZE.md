# Feature freeze and stabilisation plan

**Status: FEATURE FROZEN**

Effective 12 November 2026, TomOS Maps is in stabilisation mode. The priority is a reliable, installable application and verification of the already-approved live-navigation work, not expansion of the feature set.

## Rules during the freeze

- Do not add unrelated user-facing features, travel modes, integrations or providers. A limited map-style adjustment was explicitly approved on 14 November 2026: retain MapLibre and use an independent light road-map style rather than Google Maps content.
- The user has explicitly approved this additional scoped work: bounded/licensed offline map downloads, UK postcode destination lookup, foreground spoken turn-by-turn guidance, and the already-approved live-navigation path (route drawing, measured GPS speed/ETA, stale or lost GPS handling, routing-error handling and physical Android validation).
- Keep work within these named items. Do not add unrelated navigation features, travel modes, integrations, providers or visual redesigns.
- Bug fixes, dependency compatibility, build configuration, crash fixes, permission/lifecycle fixes, data integrity and essential documentation are in scope.
- Refactoring is allowed only when it directly fixes a demonstrated defect or makes the existing app testable.
- Do not claim a feature works until it has been exercised on the relevant platform.
- Do not claim a build passes without a successful workflow result.

## Stabilisation order

1. Keep clean Android and Linux builds green.
2. Run shared tests and fix compilation/test failures.
3. Verify first launch, map rendering, permissions and location handling on Android.
4. Verify first launch, map rendering and location behaviour on Linux desktop.
5. Verify route calculation, route geometry overlay, GPS progress and manoeuvre display.
6. Verify stale-fix warning, recovery after fresh GPS updates, routing errors and arrival handling.
7. Verify offline-region policy restrictions and provider attribution.
8. Produce installable test artifacts and document known limitations.

## Release gates

A platform is not considered runtime-verified until:
- its clean build succeeds;
- the application launches on a real device or representative runtime;
- the primary map screen renders and remains interactive;
- permission denial and granted states are handled safely;
- no critical crash or data-loss issue remains in the smoke-test path;
- the test result and artifact are recorded.

Live navigation additionally requires a physical Android journey test in a controlled, safe setting. CI and unit tests do not substitute for this. A pass on one platform does not imply a pass on the other.

## Current scope and limitations

The current code includes a configured Valhalla route request client, a route geometry adapter and map overlay, a live GPS progress controller, measured-speed input, stale-fix handling and visible routing errors. These are implementation capabilities, not evidence of real-world reliability. Physical Android route testing remains outstanding. ETA depends on a valid measured speed and should not be treated as a guaranteed arrival time.

The OpenFreeMap Dark style remains the development map source. Bounded city-centre offline downloads are enabled through a request pipeline that enforces the provider's four-parallel-request, 3,000-tile / 50 MB and User-Agent requirements. UK postcode lookup and foreground spoken turn prompts are implemented but depend on online services/platform speech engines. Offline route calculation, offline postcode search and background voice guidance are not complete. TomOS Maps is not a production navigation application.

## CI verification

The GitHub Actions workflow runs on pushes to `main` and pull requests targeting `main`. It runs shared tests, the Linux desktop distribution build and the Android debug build, then publishes the Android testing APK when the workflow succeeds. CI verifies build/test automation only; it does not verify physical GPS behaviour, route alignment, turn timing or real-world safety.
