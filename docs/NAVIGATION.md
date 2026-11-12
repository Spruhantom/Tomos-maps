# Navigation

## Navigation progress engine

The shared navigation module in `composeApp/src/commonMain/kotlin/uk/co/tomosmaps/navigation/NavigationProgress.kt` provides the route-progress calculation layer used by Android and desktop clients.

- `RouteCoordinate` stores validated WGS84 coordinates.
- `NavigationRoute` stores route geometry and ordered manoeuvres.
- `NavigationProgressTracker` matches successive GPS fixes to route vertices, prevents progress moving backwards due to short GPS jitter, and calculates travelled and remaining distance.
- ETA is calculated only when a positive movement speed is supplied. A missing or stationary speed returns zero rather than inventing an arrival time.
- Manoeuvres are selected by their distance along the route and remain visible at the turn point.

The common tests cover forward progress, GPS jitter, speed-based ETA and missing speed.

## Valhalla route integration foundation

The common navigation module now includes a transport-neutral Valhalla adapter:

- Valhalla route request builder supports car, bicycle and pedestrian costing.
- Remote routing endpoints must use HTTPS; plain HTTP is accepted only for localhost development.
- Request payloads ask for Valhalla's six-decimal encoded polyline format.
- Response models convert route-leg geometry and manoeuvre indexes into shared WGS84 coordinates and manoeuvres.
- Successful responses now expose the validated route geometry alongside the display-friendly route summary.
- The polyline decoder and manoeuvre mapping have common unit tests.

The adapter deliberately does not make network calls or choose a public routing host. A deployment URL and platform-compatible HTTP/JSON transport still need to be configured before real route requests can be made.

## Current integration boundary

The navigation screen follows the device's current location and requests location permission when needed. It now displays the latest measured latitude/longitude when a fix is available and no longer displays fabricated turn instructions, route distance, duration or arrival time. The UI still does not calculate a route or feed GPS fixes into `LiveNavigationController`. `LiveNavigationController` now owns the platform-neutral route session lifecycle: it starts from validated route geometry, accepts real GPS fixes and optional measured speed, publishes route progress and the next manoeuvre, marks arrival within a configurable 15 metre threshold, and clears state when stopped. It does not invent speed or ETA values. The controller is covered by common tests. The UI preview is still not connected to a calculated route, and its turn banner and trip figures remain explicitly labelled sample guidance.

Next integration work:
1. Add a platform-compatible HTTP/JSON transport for a configured Valhalla deployment.
2. Add a user-entered destination and create a route request from the current GPS fix.
3. Connect the platform GPS stream and measured speed to `LiveNavigationController`.
4. Render its calculated next manoeuvre, remaining distance and ETA in the navigation panel.
5. Add off-route detection and rerouting only after route-provider behaviour and limits are configured.
