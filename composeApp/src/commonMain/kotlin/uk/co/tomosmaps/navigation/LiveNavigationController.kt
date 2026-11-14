package uk.co.tomosmaps.navigation

/**
 * Owns the route-progress lifecycle once a validated route has been calculated.
 * GPS fixes are supplied by the platform location adapter; this class never
 * fabricates positions, speed, distance, or arrival estimates.
 */
data class LiveNavigationSnapshot(
    val status: NavigationStatus = NavigationStatus.IDLE,
    val progress: NavigationProgress? = null,
    val currentLocation: RouteCoordinate? = null,
)

class LiveNavigationController(
    private val arrivalThresholdMeters: Double = 15.0,
) {
    private var tracker: NavigationProgressTracker? = null
    private var snapshot = LiveNavigationSnapshot()

    init {
        require(arrivalThresholdMeters.isFinite() && arrivalThresholdMeters > 0.0)
    }

    fun start(geometry: RouteGeometry): LiveNavigationSnapshot {
        tracker = NavigationProgressTracker(geometry)
        snapshot = LiveNavigationSnapshot(status = NavigationStatus.ACTIVE)
        return snapshot
    }

    fun updateLocation(
        location: RouteCoordinate,
        speedMetersPerSecond: Double?,
    ): LiveNavigationSnapshot {
        val activeTracker = tracker ?: return snapshot
        if (snapshot.status != NavigationStatus.ACTIVE) return snapshot

        val progress = activeTracker.update(location, speedMetersPerSecond)
        snapshot = LiveNavigationSnapshot(
            status = // Route progress is monotonic and may reach the final segment while the GPS fix
            // is still laterally displaced. Do not declare arrival unless the fix is
            // also physically close to the route; otherwise a parallel road or poor
            // GPS snap can produce a premature arrival.
            if (progress.distanceRemainingMeters <= arrivalThresholdMeters &&
                progress.distanceFromRouteMeters.isFinite() &&
                progress.distanceFromRouteMeters <= arrivalThresholdMeters
            ) {
                NavigationStatus.ARRIVED
            } else {
                NavigationStatus.ACTIVE
            },
            progress = progress,
            currentLocation = location,
        )
        return snapshot
    }

    fun stop(): LiveNavigationSnapshot {
        tracker = null
        snapshot = LiveNavigationSnapshot(status = NavigationStatus.IDLE)
        return snapshot
    }

    fun currentState(): LiveNavigationSnapshot = snapshot
}
