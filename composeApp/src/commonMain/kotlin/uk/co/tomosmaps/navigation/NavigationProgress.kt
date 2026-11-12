package uk.co.tomosmaps.navigation

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI
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

data class RouteGeometry(
    val coordinates: List<RouteCoordinate>,
    val maneuvers: List<RouteManeuver>,
) {
    init {
        require(coordinates.size >= 2) { "A route needs at least two coordinates." }
        require(maneuvers.zipWithNext().all { (a, b) ->
            a.distanceFromStartMeters <= b.distanceFromStartMeters
        }) { "Route manoeuvres must be ordered by distance." }
        require(maneuvers.all { it.distanceFromStartMeters <= totalDistanceMeters + 1.0 }) {
            "Route manoeuvres cannot be positioned beyond the route geometry."
        }
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

class NavigationProgressTracker(private val route: RouteGeometry) {
    private val cumulativeDistances = buildList {
        add(0.0)
        route.coordinates.zipWithNext().forEach { (a, b) ->
            add(last() + distanceMeters(a, b))
        }
    }
    private var furthestSegmentIndex = 0
    private var furthestDistanceMeters = 0.0

    fun update(currentLocation: RouteCoordinate, speedMetersPerSecond: Double?): NavigationProgress {
        val candidate = (furthestSegmentIndex until route.coordinates.lastIndex)
            .map { index -> projectOntoSegment(currentLocation, index) }
            .minByOrNull { it.distanceFromRouteMeters }

        // Ignore fixes well away from the route instead of falsely advancing progress.
        if (candidate != null && candidate.distanceFromRouteMeters <= MAX_ROUTE_SNAP_DISTANCE_METERS) {
            furthestSegmentIndex = maxOf(furthestSegmentIndex, candidate.segmentIndex)
            furthestDistanceMeters = maxOf(furthestDistanceMeters, candidate.distanceAlongRouteMeters)
        }

        val remaining = (route.totalDistanceMeters - furthestDistanceMeters).coerceAtLeast(0.0)
        val next = route.maneuvers.firstOrNull {
            it.distanceFromStartMeters >= furthestDistanceMeters - MANEUVER_REACHED_TOLERANCE_METERS
        }
        val seconds = if (speedMetersPerSecond != null && speedMetersPerSecond.isFinite() && speedMetersPerSecond > 0.5) {
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

    private fun projectOntoSegment(location: RouteCoordinate, segmentIndex: Int): SegmentProjection {
        val start = route.coordinates[segmentIndex]
        val end = route.coordinates[segmentIndex + 1]
        val referenceLatitude = (start.latitude + end.latitude + location.latitude) / 3.0
        val metersPerDegreeLatitude = 111_132.0
        val metersPerDegreeLongitude = 111_320.0 * kotlin.math.cos(referenceLatitude * PI / 180.0)

        val startX = (start.longitude - location.longitude) * metersPerDegreeLongitude
        val startY = (start.latitude - location.latitude) * metersPerDegreeLatitude
        val endX = (end.longitude - location.longitude) * metersPerDegreeLongitude
        val endY = (end.latitude - location.latitude) * metersPerDegreeLatitude
        val dx = endX - startX
        val dy = endY - startY
        val lengthSquared = dx * dx + dy * dy
        val fraction = if (lengthSquared == 0.0) 0.0 else
            (-(startX * dx + startY * dy) / lengthSquared).coerceIn(0.0, 1.0)
        val projectedX = startX + fraction * dx
        val projectedY = startY + fraction * dy
        val distanceFromRoute = sqrt(projectedX * projectedX + projectedY * projectedY)
        val segmentLength = cumulativeDistances[segmentIndex + 1] - cumulativeDistances[segmentIndex]

        return SegmentProjection(
            segmentIndex = segmentIndex,
            distanceAlongRouteMeters = cumulativeDistances[segmentIndex] + segmentLength * fraction,
            distanceFromRouteMeters = distanceFromRoute,
        )
    }

    private data class SegmentProjection(
        val segmentIndex: Int,
        val distanceAlongRouteMeters: Double,
        val distanceFromRouteMeters: Double,
    )

    private companion object {
        const val MAX_ROUTE_SNAP_DISTANCE_METERS = 75.0
        const val MANEUVER_REACHED_TOLERANCE_METERS = 5.0
    }
}

private fun distanceMeters(a: RouteCoordinate, b: RouteCoordinate): Double {
    val earthRadiusMeters = 6_371_000.0
    val lat1 = a.latitude * PI / 180.0
    val lat2 = b.latitude * PI / 180.0
    val deltaLat = (b.latitude - a.latitude) * PI / 180.0
    val deltaLon = (b.longitude - a.longitude) * PI / 180.0
    val h = (sin(deltaLat / 2) * sin(deltaLat / 2) +
        cos(lat1) * cos(lat2) * sin(deltaLon / 2) * sin(deltaLon / 2)).coerceIn(0.0, 1.0)
    return 2 * earthRadiusMeters * atan2(sqrt(h), sqrt(1 - h))
}
