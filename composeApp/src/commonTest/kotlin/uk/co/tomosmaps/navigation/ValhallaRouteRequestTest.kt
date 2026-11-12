package uk.co.tomosmaps.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ValhallaRouteRequestTest {
    @Test
    fun buildsConfiguredHttpsRouteEndpoint() {
        assertEquals(
            "https://routing.example.org/route",
            ValhallaRouteRequestBuilder.routeUrl("https://routing.example.org/"),
        )
    }

    @Test
    fun refusesInsecureRemoteHttpEndpoint() {
        assertFailsWith<IllegalArgumentException> {
            ValhallaRouteRequestBuilder.routeUrl("http://routing.example.org")
        }
    }

    @Test
    fun buildsCarRoutePayloadWithPolyline6() {
        val body = ValhallaRouteRequestBuilder.jsonBody(
            ValhallaRouteRequest(
                origin = RouteCoordinate(51.5, -0.12),
                destination = RouteCoordinate(51.51, -0.11),
            )
        )
        assertTrue(body.contains("\"costing\":\"auto\""))
        assertTrue(body.contains("\"shape_format\":\"polyline6\""))
        assertTrue(body.contains("\"lat\":51.5"))
        assertTrue(body.contains("\"lon\":-0.12"))
    }

    @Test
    fun supportsWalkingAndCyclingCosting() {
        assertTrue(
            ValhallaRouteRequestBuilder.jsonBody(
                ValhallaRouteRequest(
                    RouteCoordinate(51.5, -0.12),
                    RouteCoordinate(51.51, -0.11),
                    ValhallaCosting.PEDESTRIAN,
                )
            ).contains("\"costing\":\"pedestrian\"")
        )
        assertTrue(
            ValhallaRouteRequestBuilder.jsonBody(
                ValhallaRouteRequest(
                    RouteCoordinate(51.5, -0.12),
                    RouteCoordinate(51.51, -0.11),
                    ValhallaCosting.BICYCLE,
                )
            ).contains("\"costing\":\"bicycle\"")
        )
    }
}
