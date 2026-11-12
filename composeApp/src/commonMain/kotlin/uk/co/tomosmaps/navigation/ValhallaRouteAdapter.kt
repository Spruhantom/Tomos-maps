package uk.co.tomosmaps.navigation

/** Transport-neutral fields returned by Valhalla's /route endpoint. */
data class ValhallaRouteResponse(val trip: ValhallaTrip?, val error: String? = null)
data class ValhallaTrip(
    val status: Int,
    val statusMessage: String,
    val summary: ValhallaSummary?,
    val legs: List<ValhallaLeg>,
)
data class ValhallaSummary(val lengthKilometers: Double, val timeSeconds: Double)
data class ValhallaLeg(val shape: String, val maneuvers: List<ValhallaManeuver>)
data class ValhallaManeuver(
    val type: Int,
    val instruction: String,
    val streetNames: List<String> = emptyList(),
    val beginShapeIndex: Int,
)

sealed interface ValhallaRouteResult {
    data class Success(val route: NavigationRoute, val durationSeconds: Long) : ValhallaRouteResult
    data class Failure(val reason: String) : ValhallaRouteResult
}

object ValhallaRouteAdapter {
    fun adapt(response: ValhallaRouteResponse): ValhallaRouteResult {
        response.error?.let { return ValhallaRouteResult.Failure(it) }
        val trip = response.trip ?: return ValhallaRouteResult.Failure("Valhalla returned no trip.")
        if (trip.status != 0) {
            return ValhallaRouteResult.Failure(
                trip.statusMessage.ifBlank { "Valhalla route request failed (" + trip.status + ")." }
            )
        }
        if (trip.legs.isEmpty()) return ValhallaRouteResult.Failure("Valhalla returned no route legs.")

        return try {
            val coordinates = mutableListOf<RouteCoordinate>()
            val maneuvers = mutableListOf<RouteManeuver>()
            var distanceBeforeLeg = 0.0

            trip.legs.forEach { leg ->
                val legCoordinates = decodePolyline6(leg.shape)
                if (legCoordinates.size < 2) {
                    return ValhallaRouteResult.Failure("A Valhalla route leg has insufficient geometry.")
                }
                val legDistances = cumulativeDistances(legCoordinates)
                coordinates.addAll(legCoordinates.drop(if (coordinates.isEmpty()) 0 else 1))

                leg.maneuvers.forEach { maneuver ->
                    val shapeIndex = maneuver.beginShapeIndex.coerceIn(0, legCoordinates.lastIndex)
                    maneuvers += RouteManeuver(
                        distanceFromStartMeters = distanceBeforeLeg + legDistances[shapeIndex],
                        instruction = maneuver.instruction,
                        streetName = maneuver.streetNames.firstOrNull(),
                        direction = directionFor(maneuver.type),
                    )
                }
                distanceBeforeLeg += legDistances.last()
            }

            val route = NavigationRoute(coordinates, maneuvers.sortedBy { it.distanceFromStartMeters })
            ValhallaRouteResult.Success(route, trip.summary?.timeSeconds?.toLong() ?: 0L)
        } catch (exception: IllegalArgumentException) {
            ValhallaRouteResult.Failure(exception.message ?: "Invalid Valhalla route geometry.")
        }
    }

    /** Decode Valhalla's default six-decimal precision encoded polyline. */
    fun decodePolyline6(encoded: String): List<RouteCoordinate> {
        if (encoded.isEmpty()) return emptyList()
        val points = mutableListOf<RouteCoordinate>()
        var index = 0
        var latitude = 0L
        var longitude = 0L
        while (index < encoded.length) {
            val lat = decodeValue(encoded, index)
            index = lat.nextIndex
            val lon = decodeValue(encoded, index)
            index = lon.nextIndex
            latitude += lat.delta
            longitude += lon.delta
            points += RouteCoordinate(latitude / 1_000_000.0, longitude / 1_000_000.0)
        }
        return points
    }

    private data class DecodedValue(val delta: Long, val nextIndex: Int)

    private fun decodeValue(encoded: String, start: Int): DecodedValue {
        var index = start
        var result = 0L
        var shift = 0
        var chunk: Int
        do {
            require(index < encoded.length) { "Truncated encoded polyline." }
            chunk = encoded[index++].code - 63
            require(chunk in 0..63) { "Invalid encoded polyline character." }
            result = result or ((chunk and 0x1f).toLong() shl shift)
            shift += 5
            require(shift <= 60) { "Encoded polyline value is too large." }
        } while (chunk >= 0x20)
        val delta = if ((result and 1L) != 0L) -(result shr 1) - 1L else result shr 1
        return DecodedValue(delta, index)
    }

    private fun cumulativeDistances(points: List<RouteCoordinate>): List<Double> {
        val result = mutableListOf(0.0)
        points.zipWithNext().forEach { (a, b) -> result += result.last() + distanceMeters(a, b) }
        return result
    }

    private fun distanceMeters(a: RouteCoordinate, b: RouteCoordinate): Double {
        val radius = 6_371_000.0
        val lat1 = a.latitude * PI / 180.0
        val lat2 = b.latitude * PI / 180.0
        val deltaLat = (b.latitude - a.latitude) * PI / 180.0
        val deltaLon = (b.longitude - a.longitude) * PI / 180.0
        val h = sin(deltaLat / 2) * sin(deltaLat / 2) +
            cos(lat1) * cos(lat2) * sin(deltaLon / 2) * sin(deltaLon / 2)
        return 2 * radius * atan2(sqrt(h), sqrt(1 - h))
    }

    private fun directionFor(type: Int): ManeuverDirection = when (type) {
        2, 15 -> ManeuverDirection.SLIGHT_RIGHT
        3, 4, 16, 17 -> ManeuverDirection.RIGHT
        5, 6 -> ManeuverDirection.U_TURN
        7, 8, 18, 19 -> ManeuverDirection.LEFT
        9, 20 -> ManeuverDirection.SLIGHT_LEFT
        11 -> ManeuverDirection.ARRIVE
        else -> ManeuverDirection.CONTINUE
    }
}
