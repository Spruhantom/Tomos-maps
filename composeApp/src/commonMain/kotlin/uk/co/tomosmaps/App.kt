package uk.co.tomosmaps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import uk.co.tomosmaps.offline.MapProviderCatalog
import uk.co.tomosmaps.offline.OfflineRegionManager
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.CameraUpdate
import org.maplibre.compose.layers.LocationIndicatorLayer
import org.maplibre.compose.location.LocationTrackingEffect
import org.maplibre.compose.location.rememberLocationState
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.rememberMapState
import org.maplibre.compose.style.BaseStyle
import org.maplibre.spatialk.geojson.Position
import uk.co.tomosmaps.navigation.LiveNavigationController
import uk.co.tomosmaps.navigation.LiveNavigationSnapshot
import uk.co.tomosmaps.navigation.RouteCoordinate
import uk.co.tomosmaps.navigation.ValhallaCosting
import uk.co.tomosmaps.navigation.ValhallaRouteRequest
import uk.co.tomosmaps.navigation.ValhallaRouteResult
import uk.co.tomosmaps.navigation.ValhallaRoutingClient

@Composable
fun App() {
    val locationState = rememberLocationState()
    val mapState = rememberMapState(
        baseStyle = BaseStyle.Uri(MapProviderCatalog.developmentPreview.styleUrl),
        initialCameraPosition = CameraPosition(
            center = Position(latitude = 54.5, longitude = -3.0),
            zoom = 5.4
        )
    ) {
        LocationIndicatorLayer(
            id = "tomos-user-location",
            locationState = locationState
        )
    }
    val scope = rememberCoroutineScope()
    var showOfflineManager by remember { mutableStateOf(false) }
    var showNavigationPreview by remember { mutableStateOf(false) }
    val navigationController = remember { LiveNavigationController() }
    var navigation by remember { mutableStateOf(LiveNavigationSnapshot()) }
    var routeMessage by remember { mutableStateOf<String?>(null) }
    var isCalculatingRoute by remember { mutableStateOf(false) }
    val currentLatitude = locationState.lastLocation?.position?.latitude
    val currentLongitude = locationState.lastLocation?.position?.longitude

    LaunchedEffect(currentLatitude, currentLongitude, locationState.lastLocation) {
        val measurement = locationState.lastLocation
        if (measurement != null && currentLatitude != null && currentLongitude != null) {
            gpsIsStale = (Clock.System.now() - measurement.measuredAt) > 10.seconds
            val speed = measurement.distancePerSecond?.inMeters?.takeIf { it.isFinite() && it >= 0.0 }
            if (!gpsIsStale) navigation = navigationController.updateLocation(
                RouteCoordinate(currentLatitude, currentLongitude),
                speedMetersPerSecond = speed,
            )
        }
    }
    LaunchedEffect(locationState.lastLocation) {
        if (locationState.lastLocation != null) {
            kotlinx.coroutines.delay(10_000)
            gpsIsStale = true
        }
    }

    LocationTrackingEffect(
        locationState = locationState,
        enabled = showNavigationPreview,
        trackBearing = true,
    ) {
        mapState.animateCamera(
            CameraUpdate(center = currentLocation.position, zoom = 17.0, pitch = 45.0)
        )
    }


    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize()) {
                MaplibreMap(
                    modifier = Modifier.fillMaxSize(),
                    state = mapState
                )

                if (!showNavigationPreview) Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp),
                    shape = RoundedCornerShape(18.dp),
                    tonalElevation = 5.dp
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("TomOS Maps", style = MaterialTheme.typography.titleLarge)
                        Text(
                            if (locationState.lastLocation == null) {
                                "Explore the map or enable your location"
                            } else {
                                "Your location is displayed on the map"
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { showOfflineManager = true }) {
                                Text("Offline maps")
                            }
                            Button(
                                onClick = {
                                    val current = locationState.lastLocation
                                    if (current == null) {
                                        locationState.requestPermission()
                                    } else {
                                        scope.launch {
                                            mapState.animateCamera(
                                                CameraUpdate(
                                                    center = current.position,
                                                    zoom = 15.0
                                                )
                                            )
                                        }
                                    }
                                }
                            ) {
                                Text("My location")
                            }
                        }
                        Button(onClick = {
                            showNavigationPreview = true
                            if (locationState.lastLocation == null) {
                                locationState.requestPermission()
                            }
                        }) {
                            Text("Start navigation preview")
                        }
                    }
                }

                if (!showNavigationPreview) Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            scope.launch {
                                mapState.animateCamera(
                                    CameraUpdate(
                                        zoom = (mapState.cameraPosition.zoom + 1.0)
                                            .coerceAtMost(22.0)
                                    )
                                )
                            }
                        }
                    ) {
                        Text("+")
                    }
                    Button(
                        onClick = {
                            scope.launch {
                                mapState.animateCamera(
                                    CameraUpdate(
                                        zoom = (mapState.cameraPosition.zoom - 1.0)
                                            .coerceAtLeast(1.0)
                                    )
                                )
                            }
                        }
                    ) {
                        Text("−")
                    }
                }

                if (!showNavigationPreview) Surface(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp),
                    shape = RoundedCornerShape(10.dp),
                    tonalElevation = 3.dp
                ) {
                    Text(
                        "Online preview · Offline map manager available",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        style = MaterialTheme.typography.labelSmall
                    )
                }

                if (showNavigationPreview) {
                    Box(
                        modifier = Modifier.fillMaxSize()
                            .background(Color(0x66040A14))
                    )
                    NavigationPreview(
                        currentLatitude = locationState.lastLocation?.position?.latitude,
                        currentLongitude = locationState.lastLocation?.position?.longitude,
                        navigation = navigation,
                        gpsIsStale = gpsIsStale,
                        routeMessage = routeMessage,
                        isCalculatingRoute = isCalculatingRoute,
                        onCalculateRoute = { endpoint, destinationLatitude, destinationLongitude ->
                            val originLatitude = currentLatitude
                            val originLongitude = currentLongitude
                            if (originLatitude == null || originLongitude == null) {
                                routeMessage = "Waiting for a current GPS position."
                            } else {
                                scope.launch {
                                    isCalculatingRoute = true
                                    routeMessage = null
                                    try {
                                        ValhallaRoutingClient().use { client ->
                                            when (val result = client.calculateRoute(
                                                endpoint,
                                                ValhallaRouteRequest(
                                                    origin = RouteCoordinate(originLatitude, originLongitude),
                                                    destination = RouteCoordinate(destinationLatitude, destinationLongitude),
                                                    costing = ValhallaCosting.CAR,
                                                ),
                                            )) {
                                                is ValhallaRouteResult.Success -> {
                                                    routeGeometry = result.geometry
                                                    navigation = navigationController.start(result.geometry)
                                                    routeMessage = "Route calculated · ${result.route.distanceMeters} m"
                                                }
                                                is ValhallaRouteResult.Failure -> routeMessage = result.reason
                                            }
                                        }
                                    } finally {
                                        isCalculatingRoute = false
                                    }
                                }
                            }
                        },
                        onEndPreview = {
                            navigation = navigationController.stop()
                            routeGeometry = null
                            routeMessage = null
                            showNavigationPreview = false
                            scope.launch { mapState.animateCamera(CameraUpdate(pitch = 0.0)) }
                        },
                        onOverview = {
                            scope.launch {
                                mapState.animateCamera(CameraUpdate(zoom = 13.5, pitch = 0.0))
                            }
                        }
                    )
                }

                if (showOfflineManager) {
                    OfflineRegionManager(onDismiss = { showOfflineManager = false })
                }
            }
        }
    }
}
