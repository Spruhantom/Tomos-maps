package uk.co.tomosmaps.offline

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OfflineAreaPlannerBoundaryTest {
    @Test
    fun rejectsNonFiniteAndOutOfRangeCoordinates() {
        assertFailsWith<IllegalArgumentException> {
            OfflineAreaPlanner.plan(Double.NaN, 0.0, OfflineAreaSize.CITY)
        }
        assertFailsWith<IllegalArgumentException> {
            OfflineAreaPlanner.plan(0.0, Double.POSITIVE_INFINITY, OfflineAreaSize.CITY)
        }
        assertFailsWith<IllegalArgumentException> {
            OfflineAreaPlanner.plan(0.0, 180.1, OfflineAreaSize.CITY)
        }
    }

    @Test
    fun clampsAreaBoundsAtSupportedWorldEdges() {
        val north = OfflineAreaPlanner.plan(85.0, 180.0, OfflineAreaSize.SMALL)
        assertTrue(north.bounds.north <= 85.0)
        assertTrue(north.bounds.east <= 180.0)
        assertTrue(north.bounds.west < north.bounds.east)
        assertTrue(north.bounds.south < north.bounds.north)

        val south = OfflineAreaPlanner.plan(-85.0, -180.0, OfflineAreaSize.SMALL)
        assertTrue(south.bounds.south >= -85.0)
        assertTrue(south.bounds.west >= -180.0)
        assertTrue(south.bounds.west < south.bounds.east)
        assertTrue(south.bounds.south < south.bounds.north)
    }

    @Test
    fun oversizedHighLatitudeAreaIsNotMarkedDownloadable() {
        val plan = OfflineAreaPlanner.plan(84.9, 0.0, OfflineAreaSize.LARGE)
        assertFalse(plan.withinProviderLimits)
        assertFalse(plan.layers.first { it.layer == OfflineMapLayer.MAP }.available)
        assertFalse(plan.layers.first { it.layer == OfflineMapLayer.ROUTING }.available)
        assertFalse(plan.layers.first { it.layer == OfflineMapLayer.SEARCH }.available)
    }
}
