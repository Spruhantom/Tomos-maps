package uk.co.tomosmaps.navigation

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ValhallaRoutingClientTest {
    @Test
    fun rejectsUntrustedEndpointBeforeMakingRequest() = runBlocking {
        var requestMade = false
        val client = ValhallaRoutingClient(
            HttpClient(MockEngine {
                requestMade = true
                error("No request should be sent for an invalid endpoint.")
            })
        )

        val result = client.calculateRoute(
            "http://routing.example.com",
            ValhallaRouteRequest(
                RouteCoordinate(51.5, -0.1),
                RouteCoordinate(51.6, -0.2),
            ),
        )

        assertIs<ValhallaRouteResult.Failure>(result)
        assertEquals(false, requestMade)
        client.close()
    }

    @Test
    fun reportsHttpErrorFromConfiguredRoutingService() = runBlocking {
        val client = ValhallaRoutingClient(
            HttpClient(MockEngine {
                respond(
                    content = """{"error":"No suitable edges near location"}""",
                    status = HttpStatusCode.BadRequest,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            })
        )

        val result = client.calculateRoute(
            "https://routing.example.com",
            ValhallaRouteRequest(
                RouteCoordinate(51.5, -0.1),
                RouteCoordinate(51.6, -0.2),
            ),
        )

        assertEquals(
            "No suitable edges near location",
            assertIs<ValhallaRouteResult.Failure>(result).reason,
        )
        client.close()
    }
}
