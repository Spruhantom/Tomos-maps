package uk.co.tomosmaps.navigation

enum class NavigationStatus { IDLE, CALCULATING, ACTIVE, REROUTING, ARRIVED, ERROR }

/** Platform-neutral navigation state; GPS and speech are supplied by platform adapters. */
data class NavigationState(
    val status: NavigationStatus = NavigationStatus.IDLE,
    val route: NavigationRoute? = null,
    val currentLocation: GeoPoint? = null,
    val nextInstructionIndex: Int = 0,
    val errorMessage: String? = null
) {
    init {
        require(nextInstructionIndex >= 0)
        require(route == null || nextInstructionIndex <= route.instructions.size)
    }
}

/**
 * Location updates are supplied by the platform adapter. VoiceGuidance is
 * declared once in VoiceGuidance.kt so the expect/actual speech adapters and
 * the live navigation UI share one consistent contract.
 */
interface LocationProvider {
    fun start(onLocation: (GeoPoint) -> Unit, onError: (String) -> Unit)
    fun stop()
}
