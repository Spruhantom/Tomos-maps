package uk.co.tomosmaps.navigation

import uk.co.tomosmaps.offline.MapPackage

/**
 * Platform-owned Valhalla runtime. Implementations start a local engine using
 * the exact graph directory supplied by the installed regional package.
 * Returned endpoints must be loopback HTTP endpoints only.
 */
interface OfflineValhallaRuntime {
    suspend fun start(routingGraphPath: String): Result<String>
    suspend fun stop()
}

/**
 * Routes against a Valhalla service running locally on this device and backed
 * by the installed region's graph tiles. It never falls back to a remote host.
 */
class OfflineValhallaRoutingClient(
    private val routingClient: ValhallaRoutingClient,
    private val runtime: OfflineValhallaRuntime,
) {
    suspend fun calculateRoute(
        installedRegion: MapPackage?,
        origin: RouteCoordinate,
        destination: RouteCoordinate,
        costing: ValhallaCosting = ValhallaCosting.CAR,
    ): ValhallaRouteResult {
        if (installedRegion == null) {
            return ValhallaRouteResult.Failure("Download a map region containing this journey before using offline routing.")
        }
        val endpoint = runtime.start(installedRegion.routingDataPath).getOrElse {
            return ValhallaRouteResult.Failure(
                it.message?.takeIf(String::isNotBlank)
                    ?: "Unable to start the on-device offline routing engine."
            )
        }
        val isLoopback = endpoint.startsWith("http://127.0.0.1:") ||
            endpoint.startsWith("http://localhost:")
        if (!isLoopback) {
            runtime.stop()
            return ValhallaRouteResult.Failure("Offline routing refused a non-loopback engine endpoint.")
        }
        return routingClient.calculateRoute(
            endpoint,
            ValhallaRouteRequest(origin = origin, destination = destination, costing = costing),
        )
    }
}
