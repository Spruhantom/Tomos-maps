package uk.co.tomosmaps.navigation

import androidx.compose.runtime.Composable

/** Platform speech output; Android uses the device TTS engine and Linux uses eSpeak. */
interface VoiceGuidance {
    fun announce(instruction: String)
    fun stop()
    fun close()
}

@Composable
expect fun rememberPlatformVoiceGuidance(): VoiceGuidance

/**
 * De-duplicates turn announcements and emits staged prompts as the driver
 * approaches the next manoeuvre. The tracker consumes actual route progress;
 * it never estimates position or invents manoeuvres.
 */
class ManeuverAnnouncementTracker {
    private var activeManeuverKey: String? = null
    private val announcedStages = mutableSetOf<Int>()

    fun reset() {
        activeManeuverKey = null
        announcedStages.clear()
    }

    fun nextAnnouncement(progress: NavigationProgress?): String? {
        val maneuver = progress?.nextManeuver ?: return null
        val distance = progress.distanceToManeuverMeters ?: return null
        if (!distance.isFinite() || distance < 0.0) return null

        val key = "${maneuver.distanceFromStartMeters}|${maneuver.instruction}"
        if (key != activeManeuverKey) {
            activeManeuverKey = key
            announcedStages.clear()
        }

        val stage = when {
            distance <= NOW_METERS.toDouble() -> NOW_METERS
            distance <= NEAR_METERS.toDouble() -> NEAR_METERS
            distance <= ADVANCE_METERS.toDouble() -> ADVANCE_METERS
            else -> return null
        }
        if (!announcedStages.add(stage)) return null

        return if (stage == NOW_METERS) {
            "Now, ${maneuver.instruction}"
        } else {
            val roundedDistance = (distance / 10.0).toInt() * 10
            "In approximately ${roundedDistance.coerceAtLeast(10)} metres, ${maneuver.instruction}"
        }
    }

    private companion object {
        const val ADVANCE_METERS = 500
        const val NEAR_METERS = 150
        const val NOW_METERS = 25
    }
}
