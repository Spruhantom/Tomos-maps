package uk.co.tomosmaps.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VoiceGuidancePlannerTest {
    private val maneuver = RouteManeuver(1000.0, "Turn left", direction = ManeuverDirection.LEFT)

    private fun progress(distance: Double) = NavigationProgress(
        distanceTravelledMeters = 1000.0 - distance,
        distanceRemainingMeters = 1000.0,
        nextManeuver = maneuver,
        distanceToManeuverMeters = distance,
        estimatedSecondsRemaining = 60,
    )

    @Test
    fun announcesThresholdsOnceAsApproachContinues() {
        val planner = VoiceGuidancePlanner()
        assertEquals("In 800 metres, Turn left.", planner.nextAnnouncement(progress(800.0)))
        assertNull(planner.nextAnnouncement(progress(700.0)))
        assertEquals("In 250 metres, Turn left.", planner.nextAnnouncement(progress(250.0)))
        assertEquals("In 80 metres, Turn left.", planner.nextAnnouncement(progress(80.0)))
        assertNull(planner.nextAnnouncement(progress(20.0)))
    }

    @Test
    fun announcesArrivalOnlyOnce() {
        val planner = VoiceGuidancePlanner()
        assertEquals("You have arrived at your destination.", planner.nextAnnouncement(null, arrived = true))
        assertNull(planner.nextAnnouncement(null, arrived = true))
    }

    @Test
    fun resetStartsANewGuidanceSession() {
        val planner = VoiceGuidancePlanner()
        assertEquals("In 800 metres, Turn left.", planner.nextAnnouncement(progress(800.0)))
        planner.reset()
        assertEquals("In 800 metres, Turn left.", planner.nextAnnouncement(progress(800.0)))
    }
}
