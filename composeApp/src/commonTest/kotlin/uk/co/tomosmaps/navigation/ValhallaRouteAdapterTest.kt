package uk.co.tomosmaps.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ValhallaRouteAdapterTest {
    @Test
    fun decodesSixDecimalPolyline() {
        val expected = listOf(
            RouteCoordinate(51.5, -0.1),
            RouteCoordinate(51.501, -0.1),
            RouteCoordinate(51.502, -0.1),
        )
        assertEquals(expected, ValhallaRouteAdapter.decodePolyline6(encodePolyline6(expected)))
    }

    @Test
    fun adaptsRouteGeometryAndTurnInstructions() {
        val points = listOf(
            RouteCoordinate(51.5, -0.1),
            RouteCoordinate(51.501, -0.1),
            RouteCoordinate(51.502, -0.1),
        )
        val response = ValhallaRouteResponse(
            trip = ValhallaTrip(
                status = 0,
                statusMessage = "Found route between points",
                summary = ValhallaSummary(lengthKilometers = 0.22, timeSeconds = 95.0),
                legs = listOf(
                    ValhallaLeg(
                        shape = encodePolyline6(points),
                        maneuvers = listOf(
                            ValhallaManeuver(1, "Continue", beginShapeIndex = 0),
                            ValhallaManeuver(
                                10, "Turn right onto High Street",
                                streetNames = listOf("High Street"),
                                beginShapeIndex = 1,
                            ),
                            ValhallaManeuver(4, "You have arrived", beginShapeIndex = 2),
                        ),
                    )
                ),
            )
        )

        val result = assertIs<ValhallaRouteResult.Success>(ValhallaRouteAdapter.adapt(response))
        assertEquals(points, result.route.coordinates)
        assertEquals(95L, result.durationSeconds)
        assertEquals("High Street", result.route.maneuvers[1].streetName)
        assertEquals(ManeuverDirection.RIGHT, result.route.maneuvers[1].direction)
        assertEquals(ManeuverDirection.ARRIVE, result.route.maneuvers[2].direction)
        assertTrue(result.route.totalDistanceMeters > 200.0)
    }

    @Test
    fun returnsReadableFailureForValhallaError() {
        val result = ValhallaRouteAdapter.adapt(
            ValhallaRouteResponse(
                trip = ValhallaTrip(442, "No suitable edges near location", null, emptyList())
            )
        )
        assertEquals(
            "No suitable edges near location",
            assertIs<ValhallaRouteResult.Failure>(result).reason,
        )
    }

    @Test
    fun rejectsTruncatedPolyline() {
        val result = runCatching { ValhallaRouteAdapter.decodePolyline6("_") }
        assertTrue(result.isFailure)
    }

    @Test
    fun rejectsDisconnectedRouteLegsInsteadOfSilentlyJoiningThem() {
        val firstLeg = listOf(RouteCoordinate(51.5, -0.1), RouteCoordinate(51.501, -0.1))
        val secondLeg = listOf(RouteCoordinate(51.502, -0.1), RouteCoordinate(51.503, -0.1))
        val result = ValhallaRouteAdapter.adapt(
            ValhallaRouteResponse(
                trip = ValhallaTrip(
                    status = 0,
                    statusMessage = "OK",
                    summary = null,
                    legs = listOf(
                        ValhallaLeg(encodePolyline6(firstLeg), emptyList()),
                        ValhallaLeg(encodePolyline6(secondLeg), emptyList()),
                    ),
                )
            )
        )
        assertEquals(
            "Valhalla route legs do not connect; refusing to create misleading route geometry.",
            assertIs<ValhallaRouteResult.Failure>(result).reason,
        )
    }

    @Test
    fun rejectsManeuverOutsideLegGeometry() {
        val points = listOf(RouteCoordinate(51.5, -0.1), RouteCoordinate(51.501, -0.1))
        val result = ValhallaRouteAdapter.adapt(
            ValhallaRouteResponse(
                trip = ValhallaTrip(
                    status = 0,
                    statusMessage = "OK",
                    summary = null,
                    legs = listOf(
                        ValhallaLeg(
                            encodePolyline6(points),
                            listOf(ValhallaManeuver(1, "Continue", beginShapeIndex = 9)),
                        )
                    ),
                )
            )
        )
        assertEquals(
            "A Valhalla manoeuvre references an invalid route geometry index.",
            assertIs<ValhallaRouteResult.Failure>(result).reason,
        )
    }

    @Test
    fun rejectsInvalidRouteDuration() {
        val points = listOf(RouteCoordinate(51.5, -0.1), RouteCoordinate(51.501, -0.1))
        val result = ValhallaRouteAdapter.adapt(
            ValhallaRouteResponse(
                trip = ValhallaTrip(
                    status = 0,
                    statusMessage = "OK",
                    summary = ValhallaSummary(0.1, Double.NaN),
                    legs = listOf(ValhallaLeg(encodePolyline6(points), emptyList())),
                )
            )
        )
        assertEquals(
            "Valhalla returned an invalid route duration.",
            assertIs<ValhallaRouteResult.Failure>(result).reason,
        )
    }

    private fun encodePolyline6(points: List<RouteCoordinate>): String {
        var previousLatitude = 0L
        var previousLongitude = 0L
        val output = StringBuilder()
        points.forEach { point ->
            val latitude = (point.latitude * 1_000_000.0).toLong()
            val longitude = (point.longitude * 1_000_000.0).toLong()
            appendValue(output, latitude - previousLatitude)
            appendValue(output, longitude - previousLongitude)
            previousLatitude = latitude
            previousLongitude = longitude
        }
        return output.toString()
    }

    private fun appendValue(output: StringBuilder, value: Long) {
        var encoded = if (value < 0) (value shl 1).inv() else value shl 1
        while (encoded >= 0x20) {
            output.append(((0x20 or (encoded and 0x1f)) + 63).toInt().toChar())
            encoded = encoded shr 5
        }
        output.append((encoded + 63).toInt().toChar())
    }
}
