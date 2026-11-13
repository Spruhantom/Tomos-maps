package uk.co.tomosmaps.navigation

/**
 * Converts route progress into deduplicated spoken announcements.
 * Platform speech engines consume the returned text; this class owns timing
 * and session de-duplication so Android and Linux behave consistently.
 */
class VoiceGuidancePlanner(
    private val thresholdsMeters: List<Int> = listOf(800, 250, 80),
) {
    private var announcedThresholds = mutableSetOf<Int>()
    private var announcedManeuver: RouteManeuver? = null
    private var arrivalAnnounced = false

    init {
        require(thresholdsMeters.isNotEmpty())
        require(thresholdsMeters.all { it > 0 })
        require(thresholdsMeters.distinct().size == thresholdsMeters.size)
    }

    fun reset() {
        announcedThresholds.clear()
        announcedManeuver = null
        arrivalAnnounced = false
    }

    fun nextAnnouncement(progress: NavigationProgress?, arrived: Boolean = false): String? {
        if (arrived) {
            if (arrivalAnnounced) return null
            arrivalAnnounced = true
            return "You have arrived at your destination."
        }
        progress ?: return null
        val maneuver = progress.nextManeuver ?: return null
        if (maneuver != announcedManeuver) {
            announcedManeuver = maneuver
            announcedThresholds.clear()
        }
        val distance = progress.distanceToManeuverMeters ?: return null
        val threshold = thresholdsMeters.firstOrNull {
            distance <= it && it !in announcedThresholds
        } ?: return null
        announcedThresholds += threshold
        val distanceText = when {
            distance >= 1000 -> "In ${(distance / 1000.0).formatOneDecimal()} kilometres"
            distance >= 100 -> "In ${(distance / 10.0).toInt() * 10} metres"
            else -> "In ${distance.toInt().coerceAtLeast(0)} metres"
        }
        val instruction = maneuver.instruction.trim().removeSuffix(".")
        return "$distanceText, $instruction."
    }

    private fun Double.formatOneDecimal(): String {
        val rounded = (this * 10.0).toInt() / 10.0
        return rounded.toString()
    }
}
