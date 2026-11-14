package uk.co.tomosmaps.navigation

/**
 * Android's Ferrostar session projected into the shared map/navigation presentation.
 * The route coordinates are refreshed whenever Ferrostar replaces a route after deviation.
 */
data class FerrostarNavigationSnapshot(
    val navigation: LiveNavigationSnapshot = LiveNavigationSnapshot(),
    val routeCoordinates: List<RouteCoordinate> = emptyList(),
    val isCalculatingNewRoute: Boolean = false,
)
