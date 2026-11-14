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
        require(totalDistanceMeters.isFinite() && totalDistanceMeters > 0.0) {
            "Route geometry must have a finite, positive length."
        }
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
    /** Per-fix distance to the closest route segment, used for off-route detection. */
    val distanceFromRouteMeters: Double = 0.0,
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
    private var previousGpsLocation: RouteCoordinate? = null

    fun update(
        currentLocation: RouteCoordinate,
        speedMetersPerSecond: Double?,
        courseOverGroundDegrees: Double? = null,
    ): NavigationProgress {
        // Course is useful at junctions where multiple route segments can be equally
        // close to the GPS fix. Ignore invalid bearings rather than letting bad sensor
        // data influence route matching.
        val suppliedCourse = courseOverGroundDegrees?.takeIf { it.isFinite() }?.let {
            ((it % 360.0) + 360.0) % 360.0
        }
        val inferredCourse = previousGpsLocation?.let { previous ->
            val movement = distanceMeters(previous, currentLocation)
            if (movement >= MIN_HEADING_MOVEMENT_METERS &&
                (speedMetersPerSecond ?: 0.0) >= MIN_HEADING_SPEED_METERS_PER_SECOND
            ) bearingDegrees(previous, currentLocation) else null
        }
        val course = suppliedCourse ?: inferredCourse
        val maximumMatchDistance = furthestDistanceMeters + MAX_FORWARD_MATCH_DISTANCE_METERS
        val candidate = (furthestSegmentIndex until route.coordinates.lastIndex)
            .map { index -> projectOntoSegment(currentLocation, index, course) }
            .filter { it.distanceAlongRouteMeters <= maximumMatchDistance }
            .minByOrNull { it.matchScore }
        previousGpsLocation = currentLocation

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
            distanceFromRouteMeters = candidate?.distanceFromRouteMeters ?: Double.POSITIVE_INFINITY,
        )
    }

    private fun projectOntoSegment(
        location: RouteCoordinate,
        segmentIndex: Int,
        courseOverGroundDegrees: Double?,
    ): SegmentProjection {
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
        val heading = bearingDegrees(start, end)
        val headingDifference = courseOverGroundDegrees?.let { angularDifference(it, heading) } ?: 0.0

        return SegmentProjection(
            segmentIndex = segmentIndex,
            distanceAlongRouteMeters = cumulativeDistances[segmentIndex] + segmentLength * fraction,
            distanceFromRouteMeters = distanceFromRoute,
            // A 90-degree mismatch adds 31.5m to the score. This only breaks
            // ambiguous snaps; the hard 75m distance gate still uses true lateral
            // distance, not this weighted score.
            matchScore = distanceFromRoute + headingDifference * HEADING_MISMATCH_WEIGHT,
        )
    }

    private data class SegmentProjection(
        val segmentIndex: Int,
        val distanceAlongRouteMeters: Double,
        val distanceFromRouteMeters: Double,
        val matchScore: Double,
    )

    private companion object {
        const val MAX_ROUTE_SNAP_DISTANCE_METERS = 75.0
        const val MANEUVER_REACHED_TOLERANCE_METERS = 5.0
        const val HEADING_MISMATCH_WEIGHT = 0.35
        const val MAX_FORWARD_MATCH_DISTANCE_METERS = 200.0
        const val MIN_HEADING_MOVEMENT_METERS = 5.0
        const val MIN_HEADING_SPEED_METERS_PER_SECOND = 2.0
    }
}

private fun angularDifference(first: Double, second: Double): Double {
    val difference = kotlin.math.abs(first - second) % 360.0
    return minOf(difference, 360.0 - difference)
}

/** Initial bearing in degrees clockwise from true north. */
private fun bearingDegrees(start: RouteCoordinate, end: RouteCoordinate): Double {
    val lat1 = start.latitude * PI / 180.0
    val lat2 = end.latitude * PI / 180.0
    val deltaLongitude = (end.longitude - start.longitude) * PI / 180.0
    val y = sin(deltaLongitude) * cos(lat2)
    val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(deltaLongitude)
    return ((atan2(y, x) * 180.0 / PI) + 360.0) % 360.0
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
