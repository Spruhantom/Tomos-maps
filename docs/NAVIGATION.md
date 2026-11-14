# Navigation

## Current live-navigation flow

The shared navigation code is in `composeApp/src/commonMain/kotlin/uk/co/tomosmaps/navigation/`. The current UI flow is:

1. The user opens the navigation preview and grants foreground location permission when requested.
2. The app reads the current GPS fix and the user enters a UK postcode; the routing service is selected internally by the app.
3. `UkPostcodeLookupClient` validates and normalises the postcode, looks it up through Postcodes.io and converts the returned WGS84 coordinates into the route destination. Invalid and unknown postcodes are shown as errors.
4. `ValhallaRoutingClient` sends the request to the app-configured routing service and adapts the response into shared route geometry and manoeuvres.
5. The geometry is drawn on the MapLibre map as a GeoJSON LineString.
6. On Android, the route is handed to Ferrostar. Ferrostar owns location matching, step progress, route deviation detection, automatic route recalculation and spoken-instruction triggers. The Android UI observes the Ferrostar StateFlow and redraws the route geometry when a new route is accepted.
7. On Linux, GPS fixes and available measured speed continue through `LiveNavigationController` and the shared manoeuvre announcement tracker.
8. The UI displays remaining distance and ETA from the active platform navigation engine. Android speech uses Ferrostar's `AndroidTtsObserver` with UK-English locale selection; Linux uses eSpeak NG/eSpeak. The voice-guidance control mutes the active platform engine.

The navigation UI does not expose routing-service configuration. Testing builds use the FOSSGIS public Valhalla demo host internally. It is subject to fair-use/rate limits and is not a production SLA-backed TomOS service; the Valhalla maintainers ask end-user apps to notify them before public release. A dedicated TomOS-hosted service should replace it before wider distribution.

## Shared progress engine

- Route matching scores candidate segments using lateral GPS distance and, when available, travel heading. Heading can be supplied by a platform location source or inferred from successive moving GPS fixes.
- Candidate matching is bounded to 200 m ahead of accepted progress so a repeated junction or loop cannot snap navigation far forward along the route.
- Heading is ignored for invalid bearings, slow movement and GPS movement too small to provide a stable direction. The physical off-route threshold remains based on lateral distance, not the heading-weighted score.


`NavigationProgress.kt` and `NavigationProgressTracker` provide route-progress calculation:

- `RouteCoordinate` stores validated WGS84 coordinates.
- `RouteGeometry` contains the route path used for progress matching and rendering.
- Successive GPS fixes are matched to route geometry.
- Progress is guarded against moving backwards due to short GPS jitter.
- Travelled and remaining distance are calculated from route geometry.
- ETA is calculated only when a valid positive movement speed is supplied. Missing or stationary speed does not produce a fabricated arrival estimate.
- The next manoeuvre is selected by its distance along the route.

`LiveNavigationController` owns the route session lifecycle. It starts from validated geometry, accepts location fixes and optional speed, publishes progress and the next manoeuvre, detects arrival within its configured threshold (15 metres by default), and clears state when stopped. Updates after stop or arrival are ignored.

## Valhalla request and response integration

- The request builder supports car, bicycle and pedestrian costing; the current UI requests car routing.
- Requests use the Valhalla JSON route API and request six-decimal encoded polyline geometry.
- The response adapter validates route summary values, decodes route geometry and maps manoeuvres into shared models.
- Successful route results expose geometry as well as the route summary.
- The Ktor client posts JSON to the app-configured endpoint, identifies TomOS through `X-Client-Id`, handles HTTP/service errors and parses the response.
- Client exceptions are caught by the UI and surfaced as a route error message.
- The current testing default is `https://valhalla1.openstreetmap.de`.
- The public demo host is a community resource with fair-use limits. Do not treat it as a production routing backend; notify its maintainers before public end-user distribution and migrate to a dedicated TomOS service for production.

## UK postcode destinations

The navigation screen accepts complete UK postcodes instead of requiring users to enter coordinates. `UkPostcodeLookupClient` normalises case and spacing, validates the full postcode shape and requests `https://api.postcodes.io/postcodes/{postcode}`. Postcodes.io requires no API key. A successful response supplies the postcode's latitude and longitude for the Valhalla route request. Invalid input is rejected before a network request; unknown postcodes and service/network errors are surfaced in the UI.

Postcode lookup requires an internet connection. It is destination geocoding only: it does not provide address autocomplete, house-number precision beyond the postcode centroid, or offline search.

## Spoken turn-by-turn guidance

`ManeuverAnnouncementTracker` de-duplicates announcements for each route manoeuvre and produces staged prompts as measured route progress approaches it (approximately 500 m, 150 m and 25 m). It uses the actual manoeuvre text returned by Valhalla. Prompts are paused when the GPS fix is stale and can be disabled from the navigation panel.

- Android uses the platform `TextToSpeech` service and selects the UK-English locale when available.
- Linux desktop invokes `espeak-ng`, falling back to `espeak`; one of these executables must be installed.
- Speech is foreground-only. Background audio/navigation and voice settings persistence are not implemented.
- This is prototype guidance and must be physically tested for timing, volume, route transitions and TTS availability before real-world reliance.

## GPS freshness and speed

The app forwards the platform measurement's speed when available, converted to metres per second. If speed is missing or invalid, route progress can still update but the UI must not invent an ETA.

A GPS measurement older than ten seconds is treated as stale. Guidance updates are paused and the UI displays a stale-signal warning; the app waits for a fresh fix before resuming progress. This timeout is a conservative application rule, not a guarantee of GPS quality. Poor accuracy, jumps and platform-specific location behaviour still require device testing.

## Current verification

The latest merged navigation integration is PR #4, merged into main on 2026-11-14 at commit e593d86b79af97991a00f4f67dabbf4eba536275. The main-branch GitHub Actions run completed successfully:

- Android debug APK build — passed.
- Shared core tests and Linux desktop distribution/AppImage packaging — passed.
- Regional package builder tests — passed.
- GitHub testing-release publication — passed.

The resulting [TomOS Maps 0.2.0 testing release 538](https://github.com/Spruhantom/Tomos-maps/releases/tag/testing-0.2.0-build-538) contains the [Android debug APK](https://github.com/Spruhantom/Tomos-maps/releases/download/testing-0.2.0-build-538/androidApp-debug.apk) and [Linux x86_64 AppImage](https://github.com/Spruhantom/Tomos-maps/releases/download/testing-0.2.0-build-538/Tomos-Maps-Linux-x86_64.AppImage).

Automated checks establish that the code compiles, shared tests pass and release assets are produced. They do not validate real-world navigation. A physical Android journey test remains outstanding. It must validate:

- foreground permission and a stable outdoor GPS fix;
- route request success and visible route-line alignment;
- Ferrostar step progress, measured-speed ETA and sensible updates;
- stale-fix speech muting and recovery when fresh fixes resume;
- off-route detection, automatic rerouting and route-line replacement;
- spoken prompt timing, TTS availability and arrival detection;
- safe behaviour on routing errors and unavailable network.

Do not use the app as the sole navigation aid while this verification is outstanding. Never handle or inspect the phone while driving; have a passenger operate it or stop safely.

Build 538 is a prerelease and the Android asset is a debug APK, not a signed production Play Store package.

## Known limitations

- The current UI requests car routes; other costing modes are available in shared models but are not exposed in this flow.
- Android off-route detection and automatic route recalculation are handled by Ferrostar. Online journeys recalculate through the configured Valhalla endpoint; journeys using an installed regional graph recalculate through that graph. Linux's shared controller does not yet provide online automatic rerouting.
- Background voice guidance and background location tracking are not implemented. Foreground spoken manoeuvre guidance is implemented on both platforms but still requires physical-device validation.
- Online route calculation requires internet access to the internally selected public Valhalla testing service. Previously installed regional graphs remain supported for local route calculation; the app no longer provides map or regional package downloads.
- GPS/route alignment, ETA quality and spoken prompt timing have not yet been validated on a physical Android journey.
- Postcode geocoding depends on the Postcodes.io online API; postcode search is not available offline.
