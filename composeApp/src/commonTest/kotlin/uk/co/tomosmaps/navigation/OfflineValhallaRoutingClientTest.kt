package uk.co.tomosmaps.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import uk.co.tomosmaps.offline.MapPackage

class OfflineValhallaRoutingClientTest {
    private class FakeRuntime(private val endpoint: String) : OfflineValhallaRuntime {
        var startedWith: String? = null
        var stopped = false
        override suspend fun start(routingGraphPath: String): Result<String> {
            startedWith = routingGraphPath
            return Result.success(endpoint)
        }
        override suspend fun stop() { stopped = true }
    }

    private val packageInfo = MapPackage(
        id = "gb-liverpool",
        displayName = "Liverpool City Region",
        version = "1",
        mapDataPath = "regions/liverpool/map.mbtiles",
        routingDataPath = "regions/liverpool/valhalla",
        searchDataPath = "regions/liverpool/search.json",
        sizeBytes = 100,
        sha256 = "a".repeat(64),
    )

    @Test
    fun refusesToRouteWithoutInstalledRegion() {
        val runtime = FakeRuntime("http://127.0.0.1:8002")
        val client = OfflineValhallaRoutingClient(ValhallaRoutingClient(), runtime)
        val result = kotlinx.coroutines.runBlocking {
            client.calculateRoute(null, RouteCoordinate(53.4, -2.9), RouteCoordinate(53.5, -3.0))
        }
        assertTrue(result is ValhallaRouteResult.Failure)
        assertEquals(null, runtime.startedWith)
    }

    @Test
    fun startsEngineWithInstalledPackageGraphPath() {
        val runtime = FakeRuntime("https://remote.example.com")
        val client = OfflineValhallaRoutingClient(ValhallaRoutingClient(), runtime)
        val result = kotlinx.coroutines.runBlocking {
            client.calculateRoute(packageInfo, RouteCoordinate(53.4, -2.9), RouteCoordinate(53.5, -3.0))
        }
        assertTrue(result is ValhallaRouteResult.Failure)
        assertEquals(packageInfo.routingDataPath, runtime.startedWith)
        assertTrue(runtime.stopped)
        assertFalse(result is ValhallaRouteResult.Success)
    }
}
