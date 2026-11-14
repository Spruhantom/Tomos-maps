# Ferrostar Android and Linux navigation integration

## Android integration

TomOS now includes Ferrostar Core 0.59.0 and a custom Android `CustomRouteProvider`.

- `MainActivity` owns the Ferrostar session and local Valhalla client for the activity lifecycle.
- The shared postcode destination flow delegates installed-region route calculation to Ferrostar on Android.
- The custom provider calls the existing in-process Valhalla JNI runtime using the selected verified regional package. It has no network routing fallback.
- Valhalla route geometry, distance, duration, manoeuvre instructions and UK left-hand driving side are adapted into Ferrostar route/step records.
- Ferrostar's navigation state is exposed as a StateFlow; MapLibre Compose remains the map renderer.
- The Ferrostar MapLibre view module and Google Play Services location module are deliberately not added.

The current TomOS navigation panel still uses its existing shared progress and UK-English speech presentation while Ferrostar owns an Android navigation session. The next hand-off is to observe Ferrostar state in the panel and transfer Android step progress and spoken instructions to Ferrostar. Until then, the legacy progress/speech path is retained; Ferrostar route steps currently carry visual instructions but no Ferrostar speech queue, so there is no duplicate voice output.

The caller must have foreground location permission before starting navigation. The session is stopped when navigation ends and closed with the activity lifecycle. Background navigation and a foreground service are not enabled.

## Linux navigation

Linux deliberately retains its native local `valhalla_service` process, shared TomOS route geometry/progress controller and eSpeak NG/eSpeak speech adapter. Ferrostar's published Android SDK is not a native Linux desktop SDK, so no Android artifact is added to the desktop source set.

The shared navigation screen now re-checks GPS measurement age every second while navigation is active. A stalled location provider stops speech after the existing ten-second freshness threshold rather than relying on a one-shot timer.

Linux navigation now reports GPS distance from the route and applies a debounced deviation monitor: three consecutive fixes beyond 75 metres raise an off-route warning, and two good fixes clear it. A single noisy GPS fix cannot trigger the warning. Automatic rerouting is deliberately not enabled yet; the next Linux step is to recalculate through the existing local Valhalla service only after the user is safely able to respond, then add process crash/restart and GPS recovery integration tests.

## Licensing and upstream API

- Ferrostar Android Core 0.59.0 — BSD 3-Clause.
- OkHttp — Apache 2.0.
- Preserve upstream notices. Map data, routing datasets, tile providers and geocoding services have separate licences and attribution.

References:
- https://stadiamaps.github.io/ferrostar/android-getting-started.html
- https://stadiamaps.github.io/ferrostar/route-providers.html
- https://github.com/stadiamaps/ferrostar
