package uk.co.tomosmaps.navigation

import uk.co.tomosmaps.offline.MapPackage

/**
 * Routes against a Valhalla service running locally on this device and backed
 * by the installed region's graph tiles. It never falls back to a remote host.
 *
 * The native/local Valhalla runtime must be started by the platform integration
 * and configured with the graph directory from the installed package.
 */
class OfflineValhallaRoutingClient(
    private val routingClient: ValhallaRoutingClient,
    private val localEndpoint: String = "http://127.0.0.1:8002",
) {
    init {
        require(localEndpoint.startsWith("http://127.0.0.1:") ||
            localEndpoint.startsWith("http://localhost:")) {
            "Offline routing must use a loopback Valhalla endpoint."
        }
    }

    suspend fun calculateRoute(
        installedRegion: MapPackage?,
        origin: RouteCoordinate,
        destination: RouteCoordinate,
        costing: ValhallaCosting = ValhallaCosting.CAR,
    ): ValhallaRouteResult {
        if (installedRegion == null) {
            return ValhallaRouteResult.Failure("Download a map region containing this journey before using offline routing.")
        }
        if (installedRegion.routingDataPath.isBlank()) {
            return ValhallaRouteResult.Failure("The selected region has no installed routing graph.")
        }
        return routingClient.calculateRoute(
            localEndpoint,
            ValhallaRouteRequest(origin = origin, destination = destination, costing = costing),
        )
    }
}
