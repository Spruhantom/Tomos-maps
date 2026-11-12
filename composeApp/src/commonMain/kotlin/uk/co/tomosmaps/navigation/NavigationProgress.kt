package uk.co.tomosmaps.navigation

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** A WGS84 coordinate in decimal degrees. */
data class RouteCoordinate(val latitude: Double, val longitude: Double) {
    init {
        require(latitude in -90.0..90.0)
        require(longitude in -180.0..180.0)
    }
}

/** A manoeuvre positioned by distance from the start of the route. */
data class RouteManeuver(
    val distanceFromStartMeters: Double,
    val instruction: String,
    val streetName: String? = null,
    val direction: ManeuverDirection = ManeuverDirection.CONTINUE,
) {
    init {
        require(distanceFromStartMeters >= 0.0)
        require(instruction.isNotBlank())
    }
}

enum class ManeuverDirection { CONTINUE, LEFT, RIGHT, SLIGHT_LEFT, SLIGHT_RIGHT, U_TURN, ARRIVE }

data class NavigationRoute(
    val coordinates: List<RouteCoordinate>,
    val maneuvers: List<RouteManeuver>,
) {
    init {
        require(coordinates.size >= 2) { "A route needs at least two coordinates." }
        require(maneuvers.zipWithNext().all { (a, b) ->
            a.distanceFromStartMeters <= b.distanceFromStartMeters
        }) { "Route manoeuvres must be ordered by distance." }
    }

    val totalDistanceMeters: Double
        get() = coordinates.zipWithNext().sumOf { (a, b) -> distanceMeters(a, b) }
}

data class NavigationProgress(
    val distanceTravelledMeters: Double,
    val distanceRemainingMeters: Double,
    val nextManeuver: RouteManeuver?,
    val distanceToManeuverMeters: Double?,
    val estimatedSecondsRemaining: Long,
)

class NavigationProgressTracker(private val route: NavigationRoute) {
    private var furthestCoordinateIndex = 0
    private var furthestDistanceMeters = 0.0

    fun update(currentLocation: RouteCoordinate, speedMetersPerSecond: Double?): NavigationProgress {
        val nearest = (furthestCoordinateIndex until route.coordinates.size).minByOrNull { index ->
            distanceMeters(currentLocation, route.coordinates[index])
        } ?: furthestCoordinateIndex

        // Never move backwards along the route when GPS briefly jitters.
        furthestCoordinateIndex = maxOf(furthestCoordinateIndex, nearest)
        val measuredDistance = route.coordinates.take(furthestCoordinateIndex)
            .zipWithNext()
            .sumOf { (a, b) -> distanceMeters(a, b) }
        furthestDistanceMeters = maxOf(furthestDistanceMeters, measuredDistance)

        val remaining = (route.totalDistanceMeters - furthestDistanceMeters).coerceAtLeast(0.0)
        val next = route.maneuvers.firstOrNull {
            it.distanceFromStartMeters > furthestDistanceMeters + 5.0
        }
        val seconds = if (speedMetersPerSecond != null && speedMetersPerSecond > 0.5) {
            (remaining / speedMetersPerSecond).toLong()
        } else 0L

        return NavigationProgress(
            distanceTravelledMeters = furthestDistanceMeters,
            distanceRemainingMeters = remaining,
            nextManeuver = next,
            distanceToManeuverMeters = next?.let {
                (it.distanceFromStartMeters - furthestDistanceMeters).coerceAtLeast(0.0)
            },
            estimatedSecondsRemaining = seconds,
        )
    }
}

private fun distanceMeters(a: RouteCoordinate, b: RouteCoordinate): Double {
    val earthRadiusMeters = 6_371_000.0
    val lat1 = Math.toRadians(a.latitude)
    val lat2 = Math.toRadians(b.latitude)
    val deltaLat = Math.toRadians(b.latitude - a.latitude)
    val deltaLon = Math.toRadians(b.longitude - a.longitude)
    val h = sin(deltaLat / 2) * sin(deltaLat / 2) +
        cos(lat1) * cos(lat2) * sin(deltaLon / 2) * sin(deltaLon / 2)
    return 2 * earthRadiusMeters * atan2(sqrt(h), sqrt(1 - h))
}
