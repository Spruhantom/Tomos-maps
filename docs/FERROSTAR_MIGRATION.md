# Ferrostar Android and Linux navigation integration

## Android integration

TomOS now includes Ferrostar Core 0.59.0 and a custom Android `CustomRouteProvider`.

- `MainActivity` owns the Ferrostar session and local Valhalla client for the activity lifecycle.
- The shared postcode destination flow delegates installed-region route calculation to Ferrostar on Android.
- The custom provider calls the existing in-process Valhalla JNI runtime using the selected verified regional package. It has no network routing fallback.
- Valhalla route geometry, distance, duration, manoeuvre instructions and UK left-hand driving side are adapted into Ferrostar route/step records.
- Ferrostar's navigation state is exposed as a StateFlow; MapLibre Compose remains the map renderer.
- The Ferrostar MapLibre view module and Google Play Services location module are deliberately not added.

The Android navigation panel now observes Ferrostar's StateFlow through a platform-neutral TomOS snapshot. Ferrostar owns Android step progress, route deviation/recalculation state, route geometry updates and spoken instruction timing. Valhalla route steps include both visual and spoken instructions; the AndroidTtsObserver speaks them using the UK-English locale where available. The legacy shared progress and TTS path is retained for Linux only and is not run alongside Ferrostar on Android.

Online routes calculated by the configured Valhalla endpoint are handed to Ferrostar as route objects. The custom route provider retains the selected installed-region path when one is used, and uses the same configured online Valhalla endpoint for subsequent automatic reroutes on an online route. No alternate provider or silent routing fallback is introduced. When Ferrostar recalculates, the shared map line follows the new route geometry and the panel reports the reroute state.

The caller must have foreground location permission before starting navigation. The session is stopped when navigation ends and closed with the activity lifecycle. Background navigation and a foreground service are not enabled.

## Linux navigation

Linux deliberately retains its native local `valhalla_service` process, shared TomOS route geometry/progress controller and eSpeak NG/eSpeak speech adapter. Ferrostar's published Android SDK is not a native Linux desktop SDK, so no Android artifact is added to the desktop source set.

The shared navigation screen now re-checks GPS measurement age every second while navigation is active. A stalled location provider stops speech after the existing ten-second freshness threshold rather than relying on a one-shot timer.

Linux navigation now reports GPS distance from the route and applies a debounced deviation monitor: three consecutive fixes beyond 75 metres trigger a reroute attempt, and two good fixes clear the off-route state. A single noisy GPS fix cannot trigger a reroute. When a matching installed regional graph and destination are available, the shared desktop path recalculates locally through the existing Valhalla service, replaces the route and resets maneuver announcements. If no suitable offline graph exists, it warns rather than silently switching to an online router. Android disables this shared reroute path because Ferrostar owns Android route-deviation handling. Process crash/restart and physical GPS recovery integration tests remain outstanding.

## Licensing and upstream API

- Ferrostar Android Core 0.59.0 — BSD 3-Clause.
- OkHttp — Apache 2.0.
- Preserve upstream notices. Map data, routing datasets, tile providers and geocoding services have separate licences and attribution.

References:
- https://stadiamaps.github.io/ferrostar/android-getting-started.html
- https://stadiamaps.github.io/ferrostar/route-providers.html
- https://github.com/stadiamaps/ferrostar
