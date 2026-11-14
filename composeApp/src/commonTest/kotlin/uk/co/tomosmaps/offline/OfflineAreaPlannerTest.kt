package uk.co.tomosmaps.offline

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OfflineAreaPlannerTest {
    @Test
    fun cityPresetCreatesValidBoundsAroundLiverpool() {
        val plan = OfflineAreaPlanner.plan(53.4084, -2.9916, OfflineAreaSize.CITY)
        assertTrue(plan.bounds.west < -2.9916)
        assertTrue(plan.bounds.east > -2.9916)
        assertTrue(plan.bounds.south < 53.4084)
        assertTrue(plan.bounds.north > 53.4084)
        assertTrue(plan.tileCount > 0)
    }

    @Test
    fun largerAreaRequiresMoreTiles() {
        val small = OfflineAreaPlanner.plan(53.4084, -2.9916, OfflineAreaSize.SMALL)
        val city = OfflineAreaPlanner.plan(53.4084, -2.9916, OfflineAreaSize.CITY)
        val large = OfflineAreaPlanner.plan(53.4084, -2.9916, OfflineAreaSize.LARGE)
        assertTrue(small.tileCount < city.tileCount)
        assertTrue(city.tileCount < large.tileCount)
    }

    @Test
    fun mapHasAnEstimateButRoutingAndSearchRequireRegionalPackages() {
        val plan = OfflineAreaPlanner.plan(53.4084, -2.9916, OfflineAreaSize.CITY)
        val map = plan.layers.first { it.layer == OfflineMapLayer.MAP }
        val routing = plan.layers.first { it.layer == OfflineMapLayer.ROUTING }
        val search = plan.layers.first { it.layer == OfflineMapLayer.SEARCH }
        assertTrue(map.available)
        assertTrue(requireNotNull(map.estimatedBytesMinimum) > 0)
        assertTrue(requireNotNull(map.estimatedBytesMaximum) > map.estimatedBytesMinimum)
        assertFalse(routing.available)
        assertFalse(search.available)
    }

    @Test
    fun rejectsInvalidMapCentres() {
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            OfflineAreaPlanner.plan(91.0, 0.0, OfflineAreaSize.CITY)
        }
    }

    @Test
    fun tileCountUsesEveryZoomLevelInRange() {
        val bounds = OfflineAreaBounds(-3.0, 53.3, -2.9, 53.4)
        val oneZoom = OfflineAreaPlanner.countTilePyramid(bounds, 12, 12)
        val multipleZooms = OfflineAreaPlanner.countTilePyramid(bounds, 10, 14)
        assertTrue(multipleZooms > oneZoom)
        assertEquals(oneZoom, OfflineAreaPlanner.countTilePyramid(bounds, 12, 12))
    }
}
