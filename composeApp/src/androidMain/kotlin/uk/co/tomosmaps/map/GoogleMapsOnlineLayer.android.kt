package uk.co.tomosmaps.map

import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import uk.co.tomosmaps.navigation.RouteCoordinate

actual val googleMapsOnlineSupported: Boolean = true

@Composable
actual fun GoogleMapsOnlineLayer(
    modifier: Modifier,
    currentLatitude: Double?,
    currentLongitude: Double?,
    routeCoordinates: List<RouteCoordinate>,
    recenterRequest: Int,
) {
    val context = LocalContext.current
    val apiKeyConfigured = remember(context) {
        runCatching {
            context.packageManager.getApplicationInfo(
                context.packageName,
                PackageManager.GET_META_DATA,
            ).metaData?.getString("com.google.android.geo.API_KEY")?.isNotBlank() == true
        }.getOrDefault(false)
    }

    if (!apiKeyConfigured) {
        Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text("Google Maps online is not configured", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Set TOMOS_GOOGLE_MAPS_API_KEY in local Gradle properties or the build environment. " +
                        "Enable Maps SDK for Android in Google Cloud and restrict the key to this Android app. " +
                        "TomOS offline maps remain available through the TomOS map engine.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        return
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(54.5, -3.0), 5.4f)
    }
    LaunchedEffect(currentLatitude, currentLongitude, recenterRequest) {
        val latitude = currentLatitude
        val longitude = currentLongitude
        if (latitude != null && longitude != null) {
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(LatLng(latitude, longitude), 16.5f)
            )
        }
    }

    GoogleMap(
        modifier = modifier,
        cameraPositionState = cameraPositionState,
        properties = MapProperties(mapType = MapType.NORMAL, isMyLocationEnabled = false),
        uiSettings = MapUiSettings(
            zoomControlsEnabled = false,
            myLocationButtonEnabled = false,
            mapToolbarEnabled = false,
            compassEnabled = true,
        ),
    ) {
        if (routeCoordinates.size >= 2) {
            Polyline(
                points = routeCoordinates.map { LatLng(it.latitude, it.longitude) },
                color = Color(0xFF168CFF),
                width = 7f,
            )
        }
    }
}
