package uk.co.tomosmaps.navigation

import android.content.Context
import android.speech.tts.TextToSpeech
import com.stadiamaps.ferrostar.core.CustomRouteProvider
import com.stadiamaps.ferrostar.core.FerrostarCore
import com.stadiamaps.ferrostar.core.AndroidTtsObserver
import com.stadiamaps.ferrostar.core.AndroidTtsStatusListener
import com.stadiamaps.ferrostar.core.NavigationState as FerrostarCoreState
import com.stadiamaps.ferrostar.core.extensions.preferredUserLocation
import com.stadiamaps.ferrostar.core.extensions.progress
import com.stadiamaps.ferrostar.core.extensions.remainingSteps
import com.stadiamaps.ferrostar.core.extensions.visualInstruction
import com.stadiamaps.ferrostar.core.http.OkHttpClientProvider.Companion.toOkHttpClientProvider
import com.stadiamaps.ferrostar.core.location.AndroidLocationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import uk.co.tomosmaps.offline.MapPackage
import java.time.Instant
import java.util.Locale
import java.util.UUID
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
import uniffi.ferrostar.SpokenInstruction
import uniffi.ferrostar.UserLocation
import uniffi.ferrostar.VisualInstruction
import uniffi.ferrostar.VisualInstructionContent
import uniffi.ferrostar.Waypoint
import uniffi.ferrostar.WaypointAdvanceMode
import uniffi.ferrostar.WaypointKind
import uniffi.ferrostar.stepAdvanceDistanceEntryAndExit
import uniffi.ferrostar.stepAdvanceDistanceToEndOfStep

/**
 * Ferrostar Android session backed by TomOS Valhalla routes.
 * Online journeys reroute through the configured HTTPS endpoint; installed-region
 * journeys continue to use their verified local graph. MapLibre remains the map renderer.
 */
class TomosFerrostarAndroidNavigation(
    context: Context,
    routingClient: ValhallaRoutingClient,
    private val runtime: OfflineValhallaRuntime,
) : AutoCloseable {
    private val routeProvider = TomosOfflineValhallaRouteProvider(routingClient, runtime)
    private val stateScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val speechObserver = AndroidTtsObserver(
        context.applicationContext,
        statusObserver = object : AndroidTtsStatusListener {
            override fun onTtsInitialized(tts: TextToSpeech?, status: Int) {
                if (status == TextToSpeech.SUCCESS) {
                    val languageStatus = tts?.setLanguage(Locale.UK)
                    if (languageStatus == TextToSpeech.LANG_MISSING_DATA ||
                        languageStatus == TextToSpeech.LANG_NOT_SUPPORTED
                    ) {
                        tts?.setLanguage(Locale.getDefault())
                    }
                }
            }

            override fun onTtsShutdownAndRelease() = Unit

            override fun onTtsSpeakError(utteranceId: String, status: Int) {
                android.util.Log.w("TomOSMaps", "Ferrostar speech failed for $utteranceId: $status")
            }
        },
    )
    private val httpClient = OkHttpClient.Builder()
        .callTimeout(15, TimeUnit.SECONDS)
        .build()
    private val core = FerrostarCore(
        customRouteProvider = routeProvider,
        httpClient = httpClient.toOkHttpClientProvider(),
        locationProvider = AndroidLocationProvider(context.applicationContext),
        navigationControllerConfig = NavigationControllerConfig(
            WaypointAdvanceMode.WaypointWithinRange(20.0),
            stepAdvanceDistanceEntryAndExit(30u, 5u, 32u),
            stepAdvanceDistanceToEndOfStep(10u, 32u),
            RouteDeviationTracking.StaticThreshold(15u, 50.0),
            CourseFiltering.SNAP_TO_ROUTE,
        ),
    )

    /** Ferrostar's observable navigation state projected into the shared TomOS UI model. */
    val state: StateFlow<FerrostarNavigationSnapshot> = core.state
        .map { it.toTomosSnapshot() }
        .stateIn(
            scope = stateScope,
            started = SharingStarted.Eagerly,
            initialValue = FerrostarNavigationSnapshot(),
        )

    init {
        core.spokenInstructionObserver = speechObserver
        speechObserver.start()
    }

    /** Mute/unmute Ferrostar-triggered UK-English turn instructions. */
    fun setVoiceGuidanceEnabled(enabled: Boolean) {
        speechObserver.setMuted(!enabled)
    }

    /** Starts Ferrostar from an online Valhalla route already calculated by the shared flow. */
    fun startOnlineNavigation(
        result: ValhallaRouteResult.Success,
        endpoint: String,
        origin: RouteCoordinate,
        destination: RouteCoordinate,
    ) {
        routeProvider.installedRegion = null
        routeProvider.onlineEndpoint = endpoint
        val userLocation = origin.toFerrostarUserLocation()
        val waypoint = destination.toFerrostarWaypoint()
        core.startNavigation(result.toFerrostarRoute(userLocation, listOf(waypoint)))
    }

    /** Calculates and starts a route using only the supplied verified offline region. */
    suspend fun calculateAndStart(
        installedRegion: MapPackage,
        origin: RouteCoordinate,
        destination: RouteCoordinate,
    ): ValhallaRouteResult = withContext(Dispatchers.IO) {
        routeProvider.installedRegion = installedRegion
        routeProvider.onlineEndpoint = null
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

    fun stopNavigation() {
        core.stopNavigation()
        routeProvider.installedRegion = null
        routeProvider.onlineEndpoint = null
    }

    override fun close() {
        core.stopNavigation()
        speechObserver.stopAndClearQueue()
        speechObserver.shutdown()
        stateScope.cancel()
        httpClient.dispatcher.cancelAll()
        httpClient.connectionPool.evictAll()
    }
}

private class TomosOfflineValhallaRouteProvider(
    private val routingClient: ValhallaRoutingClient,
    runtime: OfflineValhallaRuntime,
) : CustomRouteProvider {
    private val offlineClient = OfflineValhallaRoutingClient(routingClient, runtime)
    var installedRegion: MapPackage? = null
    var onlineEndpoint: String? = null
    var lastResult: ValhallaRouteResult.Success? = null
        private set

    override suspend fun getRoutes(userLocation: UserLocation, waypoints: List<Waypoint>): List<Route> {
        require(waypoints.size == 1) { "TomOS currently supports one offline destination per route." }
        val destination = waypoints.single().coordinate
        val origin = RouteCoordinate(userLocation.coordinates.lat, userLocation.coordinates.lng)
        val destinationCoordinate = RouteCoordinate(destination.lat, destination.lng)
        val region = installedRegion
        val result = if (region != null) {
            offlineClient.calculateRoute(
                installedRegion = region,
                origin = origin,
                destination = destinationCoordinate,
                costing = ValhallaCosting.CAR,
            )
        } else {
            val endpoint = requireNotNull(onlineEndpoint) {
                "No online Valhalla endpoint is configured for rerouting."
            }
            routingClient.calculateRoute(
                endpoint,
                ValhallaRouteRequest(
                    origin = origin,
                    destination = destinationCoordinate,
                    costing = ValhallaCosting.CAR,
                ),
            )
        }
        return when (result) {
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
        // The step's text describes the manoeuvre at its start. Visual and spoken
        // prompts, however, belong to the manoeuvre at the END of this step:
        // Ferrostar evaluates their trigger distances against distance-to-step-end.
        val stepManeuver = geometry.maneuvers.minByOrNull {
            abs(it.distanceFromStartMeters - start)
        }?.takeIf { abs(it.distanceFromStartMeters - start) <= 20.0 }
        val upcomingManeuver = geometry.maneuvers.minByOrNull {
            abs(it.distanceFromStartMeters - end)
        }?.takeIf { abs(it.distanceFromStartMeters - end) <= 20.0 }
        val visual = upcomingManeuver?.let { maneuver ->
            listOf(VisualInstruction(
                primaryContent = VisualInstructionContent(
                    text = maneuver.instruction,
                    maneuverType = maneuver.direction.toFerrostarType(),
                    maneuverModifier = maneuver.direction.toFerrostarModifier(),
                    roundaboutExitDegrees = null,
                    laneInfo = null,
                    exitNumbers = emptyList(),
                ),
                secondaryContent = null,
                subContent = null,
                triggerDistanceBeforeManeuver = 500.0,
            ))
        }.orEmpty()
        val spoken = upcomingManeuver?.let { maneuver ->
            val prompts = when (maneuver.direction) {
                ManeuverDirection.ARRIVE -> listOf(
                    500.0 to "In 500 metres, you will arrive at your destination.",
                    150.0 to "In 150 metres, you will arrive at your destination.",
                    25.0 to "You have arrived at your destination.",
                )
                else -> listOf(
                    500.0 to "In 500 metres, ${maneuver.instruction}",
                    150.0 to "In 150 metres, ${maneuver.instruction}",
                    25.0 to "Now, ${maneuver.instruction}",
                )
            }
            prompts.map { (distanceBefore, text) ->
                SpokenInstruction(
                    text = text,
                    ssml = null,
                    triggerDistanceBeforeManeuver = distanceBefore,
                    utteranceId = UUID.randomUUID(),
                )
            }
        }.orEmpty()
        RouteStep(
            geometry = points.subList(startIndex, endIndex + 1),
            distance = (end - start).coerceAtLeast(0.0),
            duration = durationSeconds.toDouble() * ((end - start).coerceAtLeast(0.0) / distance),
            roadName = stepManeuver?.streetName,
            exits = emptyList(),
            instruction = stepManeuver?.instruction ?: "Continue",
            visualInstructions = visual,
            spokenInstructions = spoken,
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

private fun RouteCoordinate.toFerrostarUserLocation() = UserLocation(
    coordinates = GeographicCoordinate(latitude, longitude),
    horizontalAccuracy = 20.0,
    courseOverGround = null,
    timestamp = Instant.now(),
    speed = null,
)

private fun RouteCoordinate.toFerrostarWaypoint() = Waypoint(
    coordinate = GeographicCoordinate(latitude, longitude),
    kind = WaypointKind.BREAK,
    properties = null,
)

private fun FerrostarCoreState.toTomosSnapshot(): FerrostarNavigationSnapshot {
    val trip = tripState
    val status = when (trip) {
        is uniffi.ferrostar.TripState.Navigating ->
            if (isCalculatingNewRoute) NavigationStatus.REROUTING else NavigationStatus.ACTIVE
        is uniffi.ferrostar.TripState.Complete -> NavigationStatus.ARRIVED
        is uniffi.ferrostar.TripState.Idle -> NavigationStatus.IDLE
    }
    val progress = trip.progress()?.let { tripProgress ->
        val instruction = trip.visualInstruction()?.primaryContent?.text
            ?.takeIf { it.isNotBlank() }
            ?: trip.remainingSteps()?.firstOrNull()?.instruction?.takeIf { it.isNotBlank() }
            ?: "Continue along the route"
        val totalDistance = routeGeometry.zipWithNext().sumOf { (start, end) ->
            haversineMeters(
                RouteCoordinate(start.lat, start.lng),
                RouteCoordinate(end.lat, end.lng),
            )
        }
        NavigationProgress(
            distanceTravelledMeters = (totalDistance - tripProgress.distanceRemaining).coerceAtLeast(0.0),
            distanceRemainingMeters = tripProgress.distanceRemaining.coerceAtLeast(0.0),
            nextManeuver = RouteManeuver(0.0, instruction),
            distanceToManeuverMeters = tripProgress.distanceToNextManeuver.coerceAtLeast(0.0),
            estimatedSecondsRemaining = tripProgress.durationRemaining.coerceAtLeast(0.0).toLong(),
        )
    }
    val location = trip.preferredUserLocation()?.coordinates?.let {
        RouteCoordinate(it.lat, it.lng)
    }
    return FerrostarNavigationSnapshot(
        navigation = LiveNavigationSnapshot(
            status = status,
            progress = progress,
            currentLocation = location,
        ),
        routeCoordinates = routeGeometry.map { RouteCoordinate(it.lat, it.lng) },
        isCalculatingNewRoute = isCalculatingNewRoute,
    )
}
