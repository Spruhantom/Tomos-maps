# Navigation

## Current live-navigation flow

The shared navigation code is in `composeApp/src/commonMain/kotlin/uk/co/tomosmaps/navigation/`. The current UI flow is:

1. The user opens the navigation preview and grants foreground location permission when requested.
2. The app reads the current GPS fix and the user enters a Valhalla endpoint and a UK postcode.
3. `UkPostcodeLookupClient` validates and normalises the postcode, looks it up through Postcodes.io and converts the returned WGS84 coordinates into the route destination. Invalid and unknown postcodes are shown as errors.
4. `ValhallaRoutingClient` sends the request to the configured endpoint and adapts the response into shared route geometry and manoeuvres.
5. The geometry is drawn on the MapLibre map as a GeoJSON LineString.
6. GPS fixes and available measured speed are passed to `LiveNavigationController`, which publishes route progress and the next manoeuvre.
7. The UI displays remaining distance and an ETA only when a valid positive speed is available. The voice-guidance control can be switched off independently.

The endpoint is user-configured. Remote endpoints must use HTTPS; HTTP is accepted only for localhost development. TomOS does not provide a hosted Valhalla service.

## Shared progress engine

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
- The Ktor client posts JSON to the configured endpoint, handles HTTP/service errors and parses the response.
- Client exceptions are caught by the UI and surfaced as a route error message.
- Remote endpoints require HTTPS. Plain HTTP is permitted only for localhost development.

A public routing host is not bundled or assumed. Operators must configure a Valhalla deployment and comply with its usage policy.

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

Automated common tests cover route progress, GPS jitter, speed-based ETA, missing speed, controller lifecycle, route response adaptation, routing-client errors, UK postcode validation/lookup and spoken manoeuvre de-duplication. Offline quota tests cover one active pack, stale completion guards, the 3,000-tile limit and the 50 MB limit. GitHub Actions has successfully run the shared tests, Linux desktop distribution build and Android debug build for commit `317755384fb2e00fd92c6751a5441ccf4c1b765b`; the Android testing APK was published as testing build 157.

These checks establish build and automated-test status only. A physical Android journey test remains outstanding. It must validate:
- foreground permission and a stable outdoor GPS fix;
- route request success and visible route-line alignment;
- measured-speed progress and sensible ETA updates;
- stale-fix warning when location updates stop;
- recovery when fresh fixes resume;
- arrival detection and clean session stop;
- safe behaviour on routing errors and unavailable network.

Do not use the app as the sole navigation aid while this verification is outstanding. Never handle or inspect the phone while driving; have a passenger operate it or stop safely.

## Known limitations

- The current UI requests car routes; other costing modes are available in shared models but are not exposed in this flow.
- Off-route detection and automatic rerouting are not yet implemented.
- Background voice guidance and background location tracking are not implemented. Foreground spoken manoeuvre guidance is implemented but still requires physical-device validation.
- Offline route calculation is not implemented; a reachable configured Valhalla endpoint is currently required to calculate a route.
- GPS/route alignment, ETA quality and spoken prompt timing have not yet been validated on a physical Android journey.
- Postcode geocoding depends on the Postcodes.io online API; postcode search is not available offline.
