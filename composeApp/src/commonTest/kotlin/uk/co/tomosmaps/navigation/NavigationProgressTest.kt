package uk.co.tomosmaps.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NavigationProgressTest {
    private fun route() = RouteGeometry(
        coordinates = listOf(
            RouteCoordinate(51.5000, -0.1200),
            RouteCoordinate(51.5010, -0.1200),
            RouteCoordinate(51.5020, -0.1200),
        ),
        maneuvers = listOf(
            RouteManeuver(0.0, "Continue", direction = ManeuverDirection.CONTINUE),
            RouteManeuver(111.0, "Turn right", "High Street", ManeuverDirection.RIGHT),
        ),
    )

    @Test
    fun progressDecreasesAsGpsMovesAlongRoute() {
        val tracker = NavigationProgressTracker(route())
        val start = tracker.update(RouteCoordinate(51.5000, -0.1200), 10.0)
        val middle = tracker.update(RouteCoordinate(51.5010, -0.1200), 10.0)

        assertTrue(middle.distanceTravelledMeters > start.distanceTravelledMeters)
        assertTrue(middle.distanceRemainingMeters < start.distanceRemainingMeters)
        assertNotNull(middle.nextManeuver)
        assertEquals("Turn right", middle.nextManeuver.instruction)
    }

    @Test
    fun gpsJitterDoesNotMoveProgressBackwards() {
        val tracker = NavigationProgressTracker(route())
        val ahead = tracker.update(RouteCoordinate(51.5010, -0.1200), 8.0)
        val jitter = tracker.update(RouteCoordinate(51.5002, -0.1200), 8.0)

        assertEquals(ahead.distanceTravelledMeters, jitter.distanceTravelledMeters)
    }

    @Test
    fun etaUsesPositiveGpsSpeed() {
        val tracker = NavigationProgressTracker(route())
        val progress = tracker.update(RouteCoordinate(51.5000, -0.1200), 5.0)

        assertTrue(progress.estimatedSecondsRemaining > 0)
    }

    @Test
    fun missingOrStationarySpeedDoesNotInventEta() {
        val tracker = NavigationProgressTracker(route())
        val progress = tracker.update(RouteCoordinate(51.5000, -0.1200), null)

        assertEquals(0L, progress.estimatedSecondsRemaining)
    }
    @Test
    fun nonFiniteGpsSpeedDoesNotProduceAnEta() {
        val tracker = NavigationProgressTracker(route())
        val progress = tracker.update(RouteCoordinate(51.5000, -0.1200), Double.NaN)
        assertEquals(0L, progress.estimatedSecondsRemaining)
    }

    @Test
    fun rejectsManeuversBeyondRouteGeometry() {
        val failure = runCatching {
            RouteGeometry(
                coordinates = listOf(
                    RouteCoordinate(51.5000, -0.1200),
                    RouteCoordinate(51.5010, -0.1200),
                ),
                maneuvers = listOf(
                    RouteManeuver(1_000.0, "Turn right"),
                ),
            )
        }
        assertTrue(failure.isFailure)
    }

}
