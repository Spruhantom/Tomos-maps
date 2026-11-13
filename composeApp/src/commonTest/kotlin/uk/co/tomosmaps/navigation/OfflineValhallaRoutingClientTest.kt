package uk.co.tomosmaps.navigation

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.runBlocking
import uk.co.tomosmaps.offline.MapPackage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class OfflineValhallaRoutingClientTest {
    @Test
    fun rejectsEndpointWithRemoteHostHiddenInUserInfo() = runBlocking {
        var requestMade = false
        var stopCalled = false
        val runtime = object : OfflineValhallaRuntime {
            override suspend fun start(routingGraphPath: String): Result<String> =
                Result.success("http://localhost:1234@remote.example")

            override suspend fun stop() {
                stopCalled = true
            }
        }
        val routing = ValhallaRoutingClient(
            HttpClient(MockEngine {
                requestMade = true
                error("Offline routing must never contact a remote endpoint.")
            })
        )
        val client = OfflineValhallaRoutingClient(routing, runtime)

        val result = client.calculateRoute(
            installedRegion = testPackage(),
            origin = RouteCoordinate(53.4, -2.98),
            destination = RouteCoordinate(53.5, -2.25),
        )

        assertIs<ValhallaRouteResult.Failure>(result)
        assertEquals(false, requestMade)
        assertEquals(true, stopCalled)
        routing.close()
    }

    @Test
    fun rejectsOutOfRangeLoopbackPort() = runBlocking {
        var requestMade = false
        val runtime = object : OfflineValhallaRuntime {
            override suspend fun start(routingGraphPath: String): Result<String> =
                Result.success("http://127.0.0.1:99999")

            override suspend fun stop() = Unit
        }
        val routing = ValhallaRoutingClient(
            HttpClient(MockEngine {
                requestMade = true
                error("Invalid port must be rejected before making a request.")
            })
        )
        val client = OfflineValhallaRoutingClient(routing, runtime)

        val result = client.calculateRoute(
            installedRegion = testPackage(),
            origin = RouteCoordinate(53.4, -2.98),
            destination = RouteCoordinate(53.5, -2.25),
        )

        assertIs<ValhallaRouteResult.Failure>(result)
        assertEquals(false, requestMade)
        routing.close()
    }

    private fun testPackage() = MapPackage(
        id = "gb-liverpool",
        displayName = "Liverpool City Region",
        version = "1",
        mapDataPath = "maps/liverpool",
        routingDataPath = "routing/liverpool",
        searchDataPath = "search/liverpool.json",
        sizeBytes = 1,
        sha256 = "0".repeat(64),
    )
}
