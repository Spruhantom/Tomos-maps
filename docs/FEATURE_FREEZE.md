# Feature freeze and stabilisation plan

**Status: FEATURE FROZEN**

Effective 12 November 2026, TomOS Maps is in a feature freeze. The priority is to produce a reliable, installable application rather than expand the feature set.

## Rules during the freeze

- Do not add new user-facing features, travel modes, integrations, providers, or visual redesigns.
- Do not expand the navigation roadmap or add optional capabilities.
- Existing work may be changed only when required to compile, launch, run, protect user data, or behave correctly.
- Bug fixes, dependency compatibility, build configuration, crash fixes, accessibility defects that block use, and essential documentation are in scope.
- Refactoring is allowed only when it directly reduces a demonstrated build/runtime defect or makes the app testable.
- Do not claim a feature works until it has been exercised on the relevant platform.
- Do not claim a build passes without a successful build result.

## Stabilisation order

1. Establish reproducible Android and Linux builds from a clean checkout.
2. Run common tests and fix compilation/test failures.
3. Verify first launch, map rendering, permissions and location handling on Android.
4. Verify first launch, map rendering and location behaviour on Linux desktop.
5. Fix crashes, lifecycle issues, permission handling and persistent-storage failures.
6. Verify offline-region manager behaviour and clearly communicate provider restrictions.
7. Verify navigation preview behaviour; keep demonstration data clearly identified as demonstration data.
8. Produce installable test artifacts and document known limitations.

## Release gates

A platform is not considered working until:
- its clean build succeeds;
- the application launches on a real device or representative runtime;
- the primary map screen renders and remains interactive;
- permission denial and granted states are handled safely;
- no critical crash or data-loss issue remains in the smoke-test path;
- the test result and artifact are recorded.

Android and Linux are tracked independently. A pass on one platform does not imply a pass on the other.

## Current scope and limitations

This freeze does not imply that the application is already production-ready. The route request builder and Valhalla response adapter are foundations only; a live routing transport is not yet integrated. The navigation preview still contains demonstration trip values. Offline map downloads remain restricted to providers whose terms explicitly allow them.

No new features should be started until the stabilisation gates above are met and the feature freeze is explicitly lifted.
