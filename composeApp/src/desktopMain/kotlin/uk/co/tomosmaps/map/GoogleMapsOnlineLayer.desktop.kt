package uk.co.tomosmaps.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import uk.co.tomosmaps.navigation.RouteCoordinate

actual val googleMapsOnlineSupported: Boolean = false

@Composable
actual fun GoogleMapsOnlineLayer(
    modifier: Modifier,
    currentLatitude: Double?,
    currentLongitude: Double?,
    routeCoordinates: List<RouteCoordinate>,
) = Unit
