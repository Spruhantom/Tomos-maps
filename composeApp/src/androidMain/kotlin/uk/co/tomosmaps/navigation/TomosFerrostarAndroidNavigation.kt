package uk.co.tomosmaps.navigation

import android.content.Context
import com.stadiamaps.ferrostar.core.CustomRouteProvider
import com.stadiamaps.ferrostar.core.FerrostarCore
import com.stadiamaps.ferrostar.core.http.OkHttpClientProvider.Companion.toOkHttpClientProvider
import com.stadiamaps.ferrostar.core.location.AndroidLocationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import uk.co.tomosmaps.offline.MapPackage
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import uniffi.ferrostar.BoundingBox
import uniffi.ferrostar.CourseFiltering
import uniffi.ferrostar.DrivingSide
import uniffi.ferrostar.GeographicCoordinate
import uniffi.ferrostar.ManeuverModifier
import uniffi.ferrostar.ManeuverType
import uniffi.ferrostar.NavigationControllerConfig
import uniffi.ferrostar.Route
import uniffi.ferrostar.RouteDeviationTracking
import uniffi.ferrostar.RouteStep
import uniffi.ferrostar.UserLocation
import uniffi.ferrostar.VisualInstruction
import uniffi.ferrostar.VisualInstructionContent
import uniffi.ferrostar.Waypoint
import uniffi.ferrostar.WaypointAdvanceMode
import uniffi.ferrostar.WaypointKind
import uniffi.ferrostar.stepAdvanceDistanceEntryAndExit
import uniffi.ferrostar.stepAdvanceDistanceToEndOfStep

/**
 * Ferrostar Android session backed by TomOS's installed regional Valhalla graph.
 * No network routing fallback is configured. MapLibre remains the map renderer.
 */
class TomosFerrostarAndroidNavigation(
    context: Context,
    routingClient: ValhallaRoutingClient,
    private val runtime: OfflineValhallaRuntime,
) : AutoCloseable {
    private val routeProvider = TomosOfflineValhallaRouteProvider(routingClient, runtime)
    private val httpClient = OkHttpClient.Builder()
        .callTimeout(15, TimeUnit.SECONDS)
        .build()
    private val core = FerrostarCore(
        customRouteProvider = routeProvider,
        httpClient = httpClient.toOkHttpClientProvider(),
        locationProvider = AndroidLocationProvider(context.applicationContext),
        navigationControllerConfig = NavigationControllerConfig(
            WaypointAdvanceMode.WaypointWithinRange(100.0),
            stepAdvanceDistanceEntryAndExit(30u, 5u, 32u),
            stepAdvanceDistanceToEndOfStep(10u, 32u),
            RouteDeviationTracking.StaticThreshold(15u, 50.0),
            CourseFiltering.SNAP_TO_ROUTE,
        ),
    )

    /** Ferrostar's observable navigation state for the Android UI. */
    val state get() = core.state

    /** Calculates and starts a route using only the supplied verified offline region. */
    suspend fun calculateAndStart(
        installedRegion: MapPackage,
        origin: RouteCoordinate,
        destination: RouteCoordinate,
    ): ValhallaRouteResult = withContext(Dispatchers.IO) {
        routeProvider.installedRegion = installedRegion
        val userLocation = UserLocation(
            coordinates = GeographicCoordinate(origin.latitude, origin.longitude),
            horizontalAccuracy = 20.0,
            courseOverGround = null,
            timestamp = Instant.now(),
            speed = null,
        )
        val waypoint = Waypoint(
            coordinate = GeographicCoordinate(destination.latitude, destination.longitude),
            kind = WaypointKind.BREAK,
            properties = null,
        )
        try {
            val route = core.getRoutes(userLocation, listOf(waypoint)).firstOrNull()
                ?: return@withContext ValhallaRouteResult.Failure("Ferrostar received no offline route.")
            core.startNavigation(route)
            routeProvider.lastResult
                ?: ValhallaRouteResult.Failure("The local Valhalla provider returned no route metadata.")
        } catch (exception: Exception) {
            ValhallaRouteResult.Failure(
                exception.message?.takeIf(String::isNotBlank)
                    ?: "Ferrostar could not start offline navigation."
            )
        }
    }

    fun stopNavigation() = core.stopNavigation()

    override fun close() {
        core.stopNavigation()
        httpClient.dispatcher.cancelAll()
        httpClient.connectionPool.evictAll()
    }
}

private class TomosOfflineValhallaRouteProvider(
    routingClient: ValhallaRoutingClient,
    runtime: OfflineValhallaRuntime,
) : CustomRouteProvider {
    private val offlineClient = OfflineValhallaRoutingClient(routingClient, runtime)
    var installedRegion: MapPackage? = null
    var lastResult: ValhallaRouteResult.Success? = null
        private set

    override suspend fun getRoutes(userLocation: UserLocation, waypoints: List<Waypoint>): List<Route> {
        require(waypoints.size == 1) { "TomOS currently supports one offline destination per route." }
        val region = requireNotNull(installedRegion) {
            "Select a verified offline region before calculating a route."
        }
        val destination = waypoints.single().coordinate
        return when (val result = offlineClient.calculateRoute(
            installedRegion = region,
            origin = RouteCoordinate(userLocation.coordinates.lat, userLocation.coordinates.lng),
            destination = RouteCoordinate(destination.lat, destination.lng),
            costing = ValhallaCosting.CAR,
        )) {
            is ValhallaRouteResult.Failure -> {
                lastResult = null
                throw IllegalStateException(result.reason)
            }
            is ValhallaRouteResult.Success -> {
                lastResult = result
                listOf(result.toFerrostarRoute(userLocation, waypoints))
            }
        }
    }
}

private fun ValhallaRouteResult.Success.toFerrostarRoute(
    userLocation: UserLocation,
    destinationWaypoints: List<Waypoint>,
): Route {
    val points = geometry.coordinates.map { GeographicCoordinate(it.latitude, it.longitude) }
    val distance = geometry.totalDistanceMeters
    require(distance.isFinite() && distance > 0.0) { "Valhalla returned invalid route geometry." }
    val cumulative = mutableListOf(0.0)
    geometry.coordinates.zipWithNext().forEach { (a, b) ->
        cumulative += cumulative.last() + haversineMeters(a, b)
    }
    val boundaries = (listOf(0.0) + geometry.maneuvers.map {
        it.distanceFromStartMeters.coerceIn(0.0, distance)
    }).distinct().sorted().filter { it < distance }.ifEmpty { listOf(0.0) }

    val steps = boundaries.mapIndexed { index, start ->
        val end = boundaries.getOrNull(index + 1) ?: distance
        val startIndex = cumulative.indexOfLast { it <= start }.coerceIn(0, points.lastIndex - 1)
        val endIndex = cumulative.indexOfFirst { it >= end }
            .let { if (it < 0) points.lastIndex else it }
            .coerceIn(startIndex + 1, points.lastIndex)
        val maneuver = geometry.maneuvers.minByOrNull { abs(it.distanceFromStartMeters - start) }
            ?.takeIf { abs(it.distanceFromStartMeters - start) <= 20.0 }
        val visual = maneuver?.let {
            listOf(VisualInstruction(
                primaryContent = VisualInstructionContent(
                    text = it.instruction,
                    maneuverType = it.direction.toFerrostarType(),
                    maneuverModifier = it.direction.toFerrostarModifier(),
                    roundaboutExitDegrees = null,
                    laneInfo = null,
                    exitNumbers = emptyList(),
                ),
                secondaryContent = null,
                subContent = null,
                triggerDistanceBeforeManeuver = 30.0,
            ))
        }.orEmpty()
        RouteStep(
            geometry = points.subList(startIndex, endIndex + 1),
            distance = (end - start).coerceAtLeast(0.0),
            duration = durationSeconds.toDouble() * ((end - start).coerceAtLeast(0.0) / distance),
            roadName = maneuver?.streetName,
            exits = emptyList(),
            instruction = maneuver?.instruction ?: "Continue",
            visualInstructions = visual,
            spokenInstructions = emptyList(),
            annotations = null,
            incidents = emptyList(),
            drivingSide = DrivingSide.LEFT,
            roundaboutExitNumber = null,
        )
    }
    val south = points.minOf { it.lat }
    val north = points.maxOf { it.lat }
    val west = points.minOf { it.lng }
    val east = points.maxOf { it.lng }
    return Route(
        geometry = points,
        bbox = BoundingBox(GeographicCoordinate(south, west), GeographicCoordinate(north, east)),
        distance = distance,
        waypoints = listOf(Waypoint(userLocation.coordinates, WaypointKind.BREAK, null)) + destinationWaypoints,
        steps = steps,
    )
}

private fun ManeuverDirection.toFerrostarType(): ManeuverType = when (this) {
    ManeuverDirection.ARRIVE -> ManeuverType.ARRIVE
    ManeuverDirection.CONTINUE -> ManeuverType.CONTINUE
    else -> ManeuverType.TURN
}

private fun ManeuverDirection.toFerrostarModifier(): ManeuverModifier? = when (this) {
    ManeuverDirection.LEFT -> ManeuverModifier.LEFT
    ManeuverDirection.RIGHT -> ManeuverModifier.RIGHT
    ManeuverDirection.SLIGHT_LEFT -> ManeuverModifier.SLIGHT_LEFT
    ManeuverDirection.SLIGHT_RIGHT -> ManeuverModifier.SLIGHT_RIGHT
    ManeuverDirection.U_TURN -> ManeuverModifier.U_TURN
    ManeuverDirection.CONTINUE -> ManeuverModifier.STRAIGHT
    ManeuverDirection.ARRIVE -> null
}

private fun haversineMeters(a: RouteCoordinate, b: RouteCoordinate): Double {
    val radius = 6_371_000.0
    val lat1 = Math.toRadians(a.latitude)
    val lat2 = Math.toRadians(b.latitude)
    val dLat = Math.toRadians(b.latitude - a.latitude)
    val dLon = Math.toRadians(b.longitude - a.longitude)
    val h = (kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
        kotlin.math.cos(lat1) * kotlin.math.cos(lat2) *
        kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)).coerceIn(0.0, 1.0)
    return 2.0 * radius * kotlin.math.atan2(kotlin.math.sqrt(h), kotlin.math.sqrt(1.0 - h))
}
