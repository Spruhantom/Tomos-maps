package uk.co.tomosmaps.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ManeuverAnnouncementTrackerTest {
    private fun progress(distance: Double, maneuverDistance: Double = 1_000.0) =
        NavigationProgress(
            distanceTravelledMeters = maneuverDistance - distance,
            distanceRemainingMeters = 2_000.0,
            nextManeuver = RouteManeuver(
                distanceFromStartMeters = maneuverDistance,
                instruction = "Turn right onto High Street",
                direction = ManeuverDirection.RIGHT,
            ),
            distanceToManeuverMeters = distance,
            estimatedSecondsRemaining = 0,
        )

    @Test
    fun announcesAdvanceNearAndNowStagesOnceEach() {
        val tracker = ManeuverAnnouncementTracker()
        assertEquals("In approximately 500 metres, Turn right onto High Street", tracker.nextAnnouncement(progress(500.0)))
        assertNull(tracker.nextAnnouncement(progress(420.0)))
        assertEquals("In approximately 150 metres, Turn right onto High Street", tracker.nextAnnouncement(progress(150.0)))
        assertNull(tracker.nextAnnouncement(progress(100.0)))
        assertEquals("Now, Turn right onto High Street", tracker.nextAnnouncement(progress(20.0)))
        assertNull(tracker.nextAnnouncement(progress(10.0)))
    }

    @Test
    fun resetsAnnouncementsForTheNextManeuver() {
        val tracker = ManeuverAnnouncementTracker()
        tracker.nextAnnouncement(progress(500.0))
        assertEquals(
            "In approximately 500 metres, Turn right onto High Street",
            tracker.nextAnnouncement(progress(500.0, maneuverDistance = 2_000.0)),
        )
    }

    @Test
    fun ignoresMissingOrInvalidProgress() {
        val tracker = ManeuverAnnouncementTracker()
        assertNull(tracker.nextAnnouncement(null))
    }

    @Test
    fun ignoresNonFiniteAndNegativeManeuverDistances() {
        val tracker = ManeuverAnnouncementTracker()
        assertNull(tracker.nextAnnouncement(progress(Double.NaN)))
        assertNull(tracker.nextAnnouncement(progress(Double.POSITIVE_INFINITY)))
        assertNull(tracker.nextAnnouncement(progress(-1.0)))
    }

    @Test
    fun ignoresProgressWithoutANextManeuverOrDistance() {
        val tracker = ManeuverAnnouncementTracker()
        val base = progress(500.0)
        assertNull(tracker.nextAnnouncement(base.copy(nextManeuver = null)))
        assertNull(tracker.nextAnnouncement(base.copy(distanceToManeuverMeters = null)))
    }

    @Test
    fun announcesImmediatelyWhenFirstFixIsAlreadyNearTheTurn() {
        val tracker = ManeuverAnnouncementTracker()
        assertEquals(
            "Now, Turn right onto High Street",
            tracker.nextAnnouncement(progress(20.0)),
        )
    }

    @Test
    fun changedInstructionAtSameDistanceStartsANewAnnouncementSequence() {
        val tracker = ManeuverAnnouncementTracker()
        tracker.nextAnnouncement(progress(500.0))
        val changed = progress(500.0).copy(
            nextManeuver = RouteManeuver(
                distanceFromStartMeters = 1_000.0,
                instruction = "Turn left onto Church Road",
                direction = ManeuverDirection.LEFT,
            )
        )
        assertEquals(
            "In approximately 500 metres, Turn left onto Church Road",
            tracker.nextAnnouncement(changed),
        )
    }

}
