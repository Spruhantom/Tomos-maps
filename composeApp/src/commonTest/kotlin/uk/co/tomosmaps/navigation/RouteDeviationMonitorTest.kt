package uk.co.tomosmaps.navigation

import kotlin.test.Test
import kotlin.test.assertEquals

class RouteDeviationMonitorTest {
    @Test
    fun requiresThreeConsecutiveOffRouteFixes() {
        val monitor = RouteDeviationMonitor()
        assertEquals(RouteDeviationState.SUSPECTED, monitor.update(100.0))
        assertEquals(RouteDeviationState.SUSPECTED, monitor.update(110.0))
        assertEquals(RouteDeviationState.OFF_ROUTE, monitor.update(95.0))
    }

    @Test
    fun requiresTwoGoodFixesToRecover() {
        val monitor = RouteDeviationMonitor()
        repeat(3) { monitor.update(100.0) }
        assertEquals(RouteDeviationState.OFF_ROUTE, monitor.update(20.0))
        assertEquals(RouteDeviationState.ON_ROUTE, monitor.update(10.0))
    }

    @Test
    fun invalidOrMissingFixDoesNotChangeState() {
        val monitor = RouteDeviationMonitor()
        repeat(3) { monitor.update(100.0) }
        assertEquals(RouteDeviationState.OFF_ROUTE, monitor.update(null))
        assertEquals(RouteDeviationState.OFF_ROUTE, monitor.update(Double.NaN))
    }

    @Test
    fun resetClearsDeviationHistory() {
        val monitor = RouteDeviationMonitor()
        repeat(3) { monitor.update(100.0) }
        monitor.reset()
        assertEquals(RouteDeviationState.ON_ROUTE, monitor.update(10.0))
        assertEquals(RouteDeviationState.SUSPECTED, monitor.update(100.0))
    }
}
