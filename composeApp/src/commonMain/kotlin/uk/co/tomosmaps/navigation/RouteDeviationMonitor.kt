package uk.co.tomosmaps.navigation

/**
 * Debounces GPS deviations so a single noisy fix cannot trigger an off-route warning.
 * A driver is considered off route after three consecutive fixes beyond the threshold and
 * back on route after two consecutive fixes inside it.
 */
class RouteDeviationMonitor(
    private val offRouteThresholdMeters: Double = 75.0,
    private val fixesToDeclareOffRoute: Int = 3,
    private val fixesToRecover: Int = 2,
) {
    private var offRouteFixes = 0
    private var onRouteFixes = 0
    private var currentState = RouteDeviationState.ON_ROUTE

    init {
        require(offRouteThresholdMeters.isFinite() && offRouteThresholdMeters > 0.0)
        require(fixesToDeclareOffRoute > 0)
        require(fixesToRecover > 0)
    }

    fun update(distanceFromRouteMeters: Double?): RouteDeviationState {
        val distance = distanceFromRouteMeters
        if (distance == null || !distance.isFinite() || distance < 0.0) {
            // A missing/invalid fix breaks the consecutive-fix sequence. Keeping
            // old counters would let separated GPS samples trigger false reroutes.
            offRouteFixes = 0
            onRouteFixes = 0
            return currentState
        }

        if (distance > offRouteThresholdMeters) {
            offRouteFixes++
            onRouteFixes = 0
            if (offRouteFixes >= fixesToDeclareOffRoute) {
                currentState = RouteDeviationState.OFF_ROUTE
            } else if (currentState != RouteDeviationState.OFF_ROUTE) {
                currentState = RouteDeviationState.SUSPECTED
            }
        } else {
            onRouteFixes++
            offRouteFixes = 0
            if (currentState != RouteDeviationState.OFF_ROUTE || onRouteFixes >= fixesToRecover) {
                currentState = RouteDeviationState.ON_ROUTE
            }
        }
        return currentState
    }

    fun reset() {
        offRouteFixes = 0
        onRouteFixes = 0
        currentState = RouteDeviationState.ON_ROUTE
    }
}

enum class RouteDeviationState { ON_ROUTE, SUSPECTED, OFF_ROUTE }
