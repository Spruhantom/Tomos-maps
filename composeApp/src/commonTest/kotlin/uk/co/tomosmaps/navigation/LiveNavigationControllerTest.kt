package uk.co.tomosmaps.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LiveNavigationControllerTest {
    private fun geometry() = RouteGeometry(
        coordinates = listOf(
            RouteCoordinate(51.5000, -0.1200),
            RouteCoordinate(51.5010, -0.1200),
            RouteCoordinate(51.5020, -0.1200),
        ),
        maneuvers = listOf(
            RouteManeuver(0.0, "Continue"),
            RouteManeuver(111.0, "Turn right onto High Street", "High Street", ManeuverDirection.RIGHT),
            RouteManeuver(222.0, "Arrive", direction = ManeuverDirection.ARRIVE),
        ),
    )

    @Test
    fun startsWithActiveStateAndNoInventedProgress() {
        val controller = LiveNavigationController()
        val state = controller.start(geometry())
        assertEquals(NavigationStatus.ACTIVE, state.status)
        assertNull(state.progress)
        assertNull(state.currentLocation)
    }

    @Test
    fun gpsFixesUpdateDistanceAndNextInstruction() {
        val controller = LiveNavigationController()
        controller.start(geometry())

        val state = controller.updateLocation(RouteCoordinate(51.5010, -0.1200), 5.0)

        assertEquals(NavigationStatus.ACTIVE, state.status)
        val progress = assertNotNull(state.progress)
        assertTrue(progress.distanceTravelledMeters > 100.0)
        assertTrue(progress.distanceRemainingMeters > 100.0)
        assertEquals("Turn right onto High Street", progress.nextManeuver?.instruction)
        assertEquals(RouteCoordinate(51.5010, -0.1200), state.currentLocation)
    }

    @Test
    fun routeCompletionMovesSessionToArrived() {
        val controller = LiveNavigationController()
        controller.start(geometry())

        val state = controller.updateLocation(RouteCoordinate(51.5020, -0.1200), 3.0)

        assertEquals(NavigationStatus.ARRIVED, state.status)
        assertTrue(state.progress!!.distanceRemainingMeters <= 15.0)
    }

    @Test
    fun stopClearsRouteProgressAndIgnoresLaterGpsFixes() {
        val controller = LiveNavigationController()
        controller.start(geometry())
        controller.updateLocation(RouteCoordinate(51.5010, -0.1200), 5.0)

        val stopped = controller.stop()
        val ignored = controller.updateLocation(RouteCoordinate(51.5020, -0.1200), 5.0)

        assertEquals(NavigationStatus.IDLE, stopped.status)
        assertNull(stopped.progress)
        assertEquals(stopped, ignored)
    }

    @Test
    fun fixesCannotRestartAnArrivedSession() {
        val controller = LiveNavigationController()
        controller.start(geometry())
        controller.updateLocation(RouteCoordinate(51.5020, -0.1200), 5.0)

        val later = controller.updateLocation(RouteCoordinate(51.5010, -0.1200), 5.0)

        assertEquals(NavigationStatus.ARRIVED, later.status)
    }

    @Test
    fun rejectsInvalidArrivalThreshold() {
        assertTrue(runCatching { LiveNavigationController(0.0) }.isFailure)
        assertTrue(runCatching { LiveNavigationController(Double.NaN) }.isFailure)
    }
}
