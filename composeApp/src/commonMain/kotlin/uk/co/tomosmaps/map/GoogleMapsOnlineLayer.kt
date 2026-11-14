package uk.co.tomosmaps.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import uk.co.tomosmaps.navigation.RouteCoordinate

/** True only on platforms that provide Google's online Maps SDK integration. */
expect val googleMapsOnlineSupported: Boolean

/** Android-only Google Maps layer; never used for offline map storage or routing. */
@Composable
expect fun GoogleMapsOnlineLayer(
    modifier: Modifier,
    currentLatitude: Double?,
    currentLongitude: Double?,
    routeCoordinates: List<RouteCoordinate>,
)
