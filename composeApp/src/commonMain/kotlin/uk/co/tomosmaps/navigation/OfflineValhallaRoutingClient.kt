package uk.co.tomosmaps.navigation

import uk.co.tomosmaps.offline.MapPackage

/**
 * Platform-owned offline Valhalla runtime. Android uses the native mobile
 * library; Linux owns a local Valhalla service process.
 */
interface OfflineValhallaRuntime {
    suspend fun start(routingGraphPath: String): Result<String>

    suspend fun calculateRoute(
        routingGraphPath: String,
        request: ValhallaRouteRequest,
        routingClient: ValhallaRoutingClient,
    ): ValhallaRouteResult {
        val endpoint = start(routingGraphPath).getOrElse {
            return ValhallaRouteResult.Failure(
                it.message?.takeIf(String::isNotBlank)
                    ?: "Unable to start the on-device offline routing engine."
            )
        }
        if (!isValidLoopbackEndpoint(endpoint)) {
            runCatching { stop() }
            return ValhallaRouteResult.Failure(
                "Offline routing refused a non-loopback or invalid engine endpoint."
            )
        }
        return routingClient.calculateRoute(endpoint, request)
    }

    suspend fun stop()

    private fun isValidLoopbackEndpoint(endpoint: String): Boolean {
        val match = LOOPBACK_ENDPOINT.matchEntire(endpoint) ?: return false
        val port = match.groupValues[1].toIntOrNull() ?: return false
        return port in 1..65_535
    }

    private companion object {
        val LOOPBACK_ENDPOINT = Regex("^http://(?:127\\.0\\.0\\.1|localhost):([0-9]{1,5})$")
    }
}

/** Routes using the local runtime and the installed region's graph. */
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
            return ValhallaRouteResult.Failure(
                "Download a map region containing this journey before using offline routing."
            )
        }
        return runtime.calculateRoute(
            installedRegion.routingDataPath,
            ValhallaRouteRequest(origin = origin, destination = destination, costing = costing),
            routingClient,
        )
    }
}

/** Creates the platform-owned local/native Valhalla runtime. */
expect fun createOfflineValhallaRuntime(
    storageDirectory: String,
    platformContext: Any? = null,
): OfflineValhallaRuntime
