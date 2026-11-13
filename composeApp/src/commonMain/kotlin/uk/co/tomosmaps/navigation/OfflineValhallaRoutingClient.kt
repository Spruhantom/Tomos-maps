package uk.co.tomosmaps.navigation

import uk.co.tomosmaps.offline.MapPackage

/**
 * Platform-owned Valhalla runtime. Implementations start a local engine using
 * the exact graph directory supplied by the installed regional package.
 * Returned endpoints must be HTTP loopback endpoints with an explicit valid port.
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
        if (!isValidLoopbackEndpoint(endpoint)) {
            runCatching { runtime.stop() }
            return ValhallaRouteResult.Failure("Offline routing refused a non-loopback or invalid engine endpoint.")
        }
        return routingClient.calculateRoute(
            endpoint,
            ValhallaRouteRequest(origin = origin, destination = destination, costing = costing),
        )
    }

    private fun isValidLoopbackEndpoint(endpoint: String): Boolean {
        // Match the entire authority: startsWith() would accept user-info tricks
        // such as http://localhost:1234@remote.example and unsafe port values.
        val match = LOOPBACK_ENDPOINT.matchEntire(endpoint) ?: return false
        val port = match.groupValues[1].toIntOrNull() ?: return false
        return port in 1..65_535
    }

    private companion object {
        val LOOPBACK_ENDPOINT = Regex("^http://(?:127\\.0\\.0\\.1|localhost):([0-9]{1,5})$")
    }
}


/** Creates the platform-owned local Valhalla process manager. */
expect fun createOfflineValhallaRuntime(storageDirectory: String): OfflineValhallaRuntime
