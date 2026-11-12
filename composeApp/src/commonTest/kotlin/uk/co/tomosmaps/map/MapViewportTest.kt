package uk.co.tomosmaps.map

import kotlin.test.Test
import kotlin.test.assertFailsWith
import uk.co.tomosmaps.navigation.GeoPoint

class MapViewportTest {
    @Test
    fun acceptsValidCamera() {
        MapCamera(center = GeoPoint(51.5072, -0.1276), zoom = 14.0)
    }

    @Test
    fun rejectsInvalidZoomAndPitch() {
        assertFailsWith<IllegalArgumentException> {
            MapCamera(center = GeoPoint(51.5072, -0.1276), zoom = 25.0)
        }
        assertFailsWith<IllegalArgumentException> {
            MapCamera(center = GeoPoint(51.5072, -0.1276), pitchDegrees = 90.0)
        }
    }

    @Test
    fun requiresAtLeastTwoRoutePoints() {
        assertFailsWith<IllegalArgumentException> {
            RouteOverlay(listOf(GeoPoint(51.5072, -0.1276)))
        }
    }

    @Test
    fun rejectsInsecureOnlineStyles() {
        assertFailsWith<IllegalArgumentException> {
            MapStyleSource.Online("http://example.com/style.json")
        }
    }

    @Test
    fun rejectsOfflinePathTraversal() {
        assertFailsWith<IllegalArgumentException> {
            MapStyleSource.Offline("../../style.json")
        }
    }
}
