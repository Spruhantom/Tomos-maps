package uk.co.tomosmaps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import uk.co.tomosmaps.offline.OpenFreeMapResourcePipeline
import uk.co.tomosmaps.offline.OfflineRegionManager
import uk.co.tomosmaps.offline.RegionalPackageManager
import uk.co.tomosmaps.offline.RegionalPackageTransfer
import uk.co.tomosmaps.offline.OfflineLayerPackageTransfer
import uk.co.tomosmaps.offline.createOfflineLayerPackageTransfer
import uk.co.tomosmaps.offline.RegionalPackageBuildConfig
import uk.co.tomosmaps.offline.MapPackage
import uk.co.tomosmaps.offline.createRegionalOfflineMapStyle
import uk.co.tomosmaps.offline.regionalMapDataFileUri
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.CameraUpdate
import org.maplibre.compose.layers.LocationIndicatorLayer
import org.maplibre.compose.location.LocationTrackingEffect
import org.maplibre.compose.location.rememberLocationState
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.DefaultMapRuntime
import org.maplibre.compose.map.rememberMapState
import org.maplibre.compose.style.BaseStyle
import org.maplibre.spatialk.geojson.Position
import org.maplibre.spatialk.units.extensions.inMeters
import uk.co.tomosmaps.navigation.LiveNavigationController
import uk.co.tomosmaps.navigation.OfflineValhallaRoutingClient
import uk.co.tomosmaps.navigation.OfflineValhallaRuntime
import uk.co.tomosmaps.navigation.LiveNavigationSnapshot
import uk.co.tomosmaps.navigation.ManeuverInstruction
import uk.co.tomosmaps.navigation.ManeuverAnnouncementTracker
import uk.co.tomosmaps.navigation.rememberPlatformVoiceGuidance
import uk.co.tomosmaps.navigation.NavigationStatus
import uk.co.tomosmaps.navigation.UkPostcodeLookupClient
import uk.co.tomosmaps.navigation.UkPostcodeLookupResult
import uk.co.tomosmaps.navigation.normalizeUkPostcode
import uk.co.tomosmaps.navigation.RouteCoordinate
import uk.co.tomosmaps.navigation.ValhallaCosting
import uk.co.tomosmaps.navigation.ValhallaRouteRequest
import uk.co.tomosmaps.navigation.ValhallaRouteResult
import uk.co.tomosmaps.navigation.ValhallaRoutingClient

@Composable
fun App(
    regionalPackageTransfer: RegionalPackageTransfer? = null,
    offlineValhallaRuntime: OfflineValhallaRuntime? = null,
    regionalPackageStorageDirectory: String? = null,
    foldPosture: String = "FLAT",
    onStartOfflineNavigation: (suspend (MapPackage, RouteCoordinate, RouteCoordinate) -> ValhallaRouteResult)? = null,
    onStopOfflineNavigation: (() -> Unit)? = null,
    enableSharedOfflineRerouting: Boolean = true,
) {
    val locationState = rememberLocationState()
    val independentLayerTransfer = remember(regionalPackageStorageDirectory) {
        regionalPackageStorageDirectory?.let { storage ->
            createOfflineLayerPackageTransfer(
                storageDirectory = storage,
                pinnedEd25519PublicKeyBase64 = RegionalPackageBuildConfig.PINNED_ED25519_PUBLIC_KEY_BASE64,
            )
        }
    }
    // Route all OpenFreeMap resource loads through the app's request pipeline.
    // It adds the required User-Agent, caps concurrency at four and enforces the
    // provider's per-download 3,000-tile / 50 MB limits for offline-pack requests.
    remember {
        DefaultMapRuntime.configure {
            resourceProvider = OpenFreeMapResourcePipeline.provider
        }
    }
    val voiceGuidance = rememberPlatformVoiceGuidance()
    val announcementTracker = remember { ManeuverAnnouncementTracker() }
    var voiceGuidanceEnabled by remember { mutableStateOf(true) }
    var arrivalAnnouncementSent by remember { mutableStateOf(false) }
    var routeGeometry by remember { mutableStateOf<uk.co.tomosmaps.navigation.RouteGeometry?>(null) }
    var gpsIsStale by remember { mutableStateOf(false) }
    val routeGeoJson = routeGeometry?.let { geometry ->
        val coordinates = geometry.coordinates.joinToString(",") { "[${it.longitude},${it.latitude}]" }
        "{\"type\":\"FeatureCollection\",\"features\":[{\"type\":\"Feature\",\"properties\":{},\"geometry\":{\"type\":\"LineString\",\"coordinates\":[$coordinates]}}]}"
    } ?: "{\"type\":\"FeatureCollection\",\"features\":[]}"
    var selectedOfflineRegion by remember { mutableStateOf<MapPackage?>(null) }
    var offlineMapStyle by remember { mutableStateOf<BaseStyle?>(null) }
    var useOnlinePreview by remember { mutableStateOf(false) }
    var mapDataMessage by remember { mutableStateOf("Loading installed regional maps…") }
    val loadingMapStyle = remember {
        BaseStyle.Json(
            """{"version":8,"name":"TomOS offline map loading","sources":{},"layers":[{"id":"tomos-loading-background","type":"background","paint":{"background-color":"#f1f0e8"}}]}"""
        )
    }
    val mapState = rememberMapState(
        baseStyle = offlineMapStyle
            ?: if (useOnlinePreview) BaseStyle.Uri(MapProviderCatalog.developmentPreview.styleUrl)
            else loadingMapStyle,
        initialCameraPosition = CameraPosition(
            center = Position(latitude = 54.5, longitude = -3.0),
            zoom = 5.4
        )
    ) {
        val routeSource = rememberGeoJsonSource(GeoJsonData.JsonString(routeGeoJson))
        LineLayer(id = "tomos-calculated-route", source = routeSource, color = const(Color(0xFF168CFF)), width = const(6.dp))
        LocationIndicatorLayer(
            id = "tomos-user-location",
            locationState = locationState
        )
    }
    val scope = rememberCoroutineScope()
    var showOfflineManager by remember { mutableStateOf(false) }
    var showRegionalPackageManager by remember { mutableStateOf(false) }
    LaunchedEffect(regionalPackageTransfer, regionalPackageStorageDirectory) {
        val transfer = regionalPackageTransfer
        if (transfer != null && regionalPackageStorageDirectory != null) {
            val installed = runCatching { transfer.installedPackages() }.getOrDefault(emptyList())
            if (installed.isEmpty()) showRegionalPackageManager = true
        }
    }
    LaunchedEffect(regionalPackageTransfer, regionalPackageStorageDirectory, showRegionalPackageManager) {
        val transfer = regionalPackageTransfer
        val storage = regionalPackageStorageDirectory
        if (storage == null) {
            useOnlinePreview = true
            mapDataMessage = "Online map preview"
            return@LaunchedEffect
        }
        runCatching {
            val regions = transfer?.installedPackages().orEmpty()
            val independentRecords = independentLayerTransfer?.installed().orEmpty()
            val independentAreas = independentRecords.map { it.areaId }.distinct()
            val independentPackages = independentAreas.mapNotNull { areaId ->
                uk.co.tomosmaps.offline.independentLayerMapPackage(independentRecords, areaId)
            }
            val allRegions = regions + independentPackages
            val location = locationState.lastLocation?.position
            val preferred = allRegions.firstOrNull { region ->
                val bounds = region.coverage
                bounds != null && location != null &&
                    bounds.contains(location.latitude, location.longitude)
            } ?: allRegions.firstOrNull { it.id == "liverpool-city-region" } ?: allRegions.firstOrNull()
            if (preferred == null) {
                selectedOfflineRegion = null
                offlineMapStyle = null
                useOnlinePreview = true
                mapDataMessage = if (independentRecords.isNotEmpty()) "Independent layers installed, but no verified MBTiles map was found" else "Online map preview · no regional map installed"
            } else {
                val fileUri = regionalMapDataFileUri(storage, preferred.mapDataPath)
                val style = createRegionalOfflineMapStyle(
                    fileUri,
                    preferred.attribution ?: "© OpenMapTiles © OpenStreetMap contributors",
                )
                selectedOfflineRegion = preferred
                offlineMapStyle = BaseStyle.Json(style)
                useOnlinePreview = false
                mapDataMessage = "Offline map · ${preferred.displayName}"
                preferred.coverage?.let { bounds ->
                    mapState.animateCamera(
                        CameraUpdate(
                            center = Position(
                                latitude = (bounds.south + bounds.north) / 2.0,
                                longitude = (bounds.west + bounds.east) / 2.0,
                            ),
                            zoom = 10.5,
                        )
                    )
                }
            }
        }.onFailure {
            selectedOfflineRegion = null
            offlineMapStyle = null
            useOnlinePreview = false
            mapDataMessage = "Offline map unavailable · ${it.message ?: "invalid regional map"}"
        }
    }
    var showNavigationPreview by remember { mutableStateOf(false) }
    val navigationController = remember { LiveNavigationController() }
    val routeDeviationMonitor = remember { uk.co.tomosmaps.navigation.RouteDeviationMonitor() }
    var routeDeviationState by remember { mutableStateOf(uk.co.tomosmaps.navigation.RouteDeviationState.ON_ROUTE) }
    var navigation by remember { mutableStateOf(LiveNavigationSnapshot()) }
    var routeMessage by remember { mutableStateOf<String?>(null) }
    var activeOfflineRegion by remember { mutableStateOf<MapPackage?>(null) }
    var activeDestination by remember { mutableStateOf<RouteCoordinate?>(null) }
    var isCalculatingRoute by remember { mutableStateOf(false) }
    val currentLatitude = locationState.lastLocation?.position?.latitude
    val currentLongitude = locationState.lastLocation?.position?.longitude

    LaunchedEffect(currentLatitude, currentLongitude, locationState.lastLocation) {
        val measurement = locationState.lastLocation
        if (measurement != null && currentLatitude != null && currentLongitude != null) {
            gpsIsStale = locationState.lastLocationMeasurementMark?.elapsedNow()?.let { it > kotlin.time.Duration.parse("10s") } ?: true
            val speed = measurement.distancePerSecond?.inMeters?.takeIf { it.isFinite() && it >= 0.0 }
            if (!gpsIsStale) {
                val updated = navigationController.updateLocation(
                    RouteCoordinate(currentLatitude, currentLongitude),
                    speedMetersPerSecond = speed,
                )
                navigation = updated
                val deviation = routeDeviationMonitor.update(updated.progress?.distanceFromRouteMeters)
                if (deviation != routeDeviationState) {
                    if (deviation == uk.co.tomosmaps.navigation.RouteDeviationState.OFF_ROUTE) {
                        routeMessage = "Off route · recalculating with the installed offline map…"
                        val region = activeOfflineRegion
                        val destination = activeDestination
                        if (enableSharedOfflineRerouting && region != null &&
                            offlineValhallaRuntime != null && destination != null
                        ) {
                            scope.launch {
                                isCalculatingRoute = true
                                val rerouteClient = ValhallaRoutingClient()
                                try {
                                    when (val reroute = OfflineValhallaRoutingClient(
                                        rerouteClient, offlineValhallaRuntime
                                    ).calculateRoute(
                                        region,
                                        RouteCoordinate(currentLatitude, currentLongitude),
                                        destination,
                                        ValhallaCosting.CAR,
                                    )) {
                                        is ValhallaRouteResult.Success -> {
                                            routeGeometry = reroute.geometry
                                            navigation = navigationController.start(reroute.geometry)
                                            announcementTracker.reset()
                                            arrivalAnnouncementSent = false
                                            routeDeviationMonitor.reset()
                                            routeDeviationState = uk.co.tomosmaps.navigation.RouteDeviationState.ON_ROUTE
                                            routeMessage = "New offline route calculated · ${reroute.route.distanceMeters} m"
                                        }
                                        is ValhallaRouteResult.Failure -> {
                                            routeMessage = "Off route · offline rerouting failed: ${reroute.reason}"
                                        }
                                    }
                                } finally {
                                    rerouteClient.close()
                                    isCalculatingRoute = false
                                }
                            }
                        } else if (!enableSharedOfflineRerouting) {
                            routeMessage = "Off route · Ferrostar is managing Android rerouting."
                        } else {
                            routeMessage = "Off route · no matching installed offline region is available for rerouting."
                        }
                    } else if (deviation == uk.co.tomosmaps.navigation.RouteDeviationState.ON_ROUTE &&
                        routeDeviationState == uk.co.tomosmaps.navigation.RouteDeviationState.OFF_ROUTE
                    ) {
                        routeMessage = "Back on route."
                    }
                    routeDeviationState = deviation
                }
            }
        }
    }
    // Re-check location age continuously: a one-shot timer tied to the first GPS fix
    // could leave spoken guidance active if the location provider later stalls.
    LaunchedEffect(showNavigationPreview, locationState.lastLocationMeasurementMark) {
        while (showNavigationPreview) {
            gpsIsStale = locationState.lastLocationMeasurementMark
                ?.elapsedNow()
                ?.let { it > kotlin.time.Duration.parse("10s") }
                ?: true
            if (gpsIsStale) voiceGuidance.stop()
            kotlinx.coroutines.delay(1_000)
        }
    }
    LaunchedEffect(navigation.status, navigation.progress, gpsIsStale, voiceGuidanceEnabled) {
        if (gpsIsStale) voiceGuidance.stop()
        if (voiceGuidanceEnabled && !gpsIsStale) {
            when (navigation.status) {
                NavigationStatus.ACTIVE -> announcementTracker
                    .nextAnnouncement(navigation.progress)
                    ?.let { voiceGuidance.announce(ManeuverInstruction(it, 0)) }
                NavigationStatus.ARRIVED -> if (!arrivalAnnouncementSent) {
                    arrivalAnnouncementSent = true
                    voiceGuidance.announce(ManeuverInstruction("You have arrived at your destination.", 0))
                }
                else -> Unit
            }
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
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                // Respond to the available window size instead of checking device models.
                // This covers folded, unfolded, split-screen and resized window layouts.
                val expandedLayout = maxWidth >= 600.dp || foldPosture == "BOOK" || foldPosture == "SEPARATING"
                val tabletopLayout = foldPosture == "TABLETOP"
                MaplibreMap(
                    modifier = Modifier.fillMaxSize(),
                    state = mapState
                )

                if (!showNavigationPreview) Surface(
                    modifier = Modifier
                        .align(when { tabletopLayout -> Alignment.BottomStart; expandedLayout -> Alignment.CenterStart; else -> Alignment.TopStart })
                        .widthIn(max = if (expandedLayout) 400.dp else 720.dp)
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
                                onClick = { showRegionalPackageManager = true },
                                enabled = regionalPackageTransfer != null
                            ) {
                                Text("Regional data")
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
                        if (selectedOfflineRegion != null) {
                            Button(onClick = { useOnlinePreview = !useOnlinePreview }) {
                                Text(if (useOnlinePreview) "Use offline region" else "Use online map")
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
                        .align(if (tabletopLayout) Alignment.TopStart else Alignment.BottomStart)
                        .padding(12.dp),
                    shape = RoundedCornerShape(10.dp),
                    tonalElevation = 3.dp
                ) {
                    Text(
                        mapDataMessage,
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
                        voiceGuidanceEnabled = voiceGuidanceEnabled,
                        onVoiceGuidanceChange = { voiceGuidanceEnabled = it },
                        onCalculateRoute = { endpoint, postcode ->
                            val originLatitude = currentLatitude
                            val originLongitude = currentLongitude
                            if (originLatitude == null || originLongitude == null) {
                                routeMessage = "Waiting for a current GPS position."
                            } else {
                                scope.launch {
                                    isCalculatingRoute = true
                                    routeMessage = null
                                    try {
                                        val combinedRegions = regionalPackageTransfer?.installedPackages().orEmpty()
                                        val independentRecords = independentLayerTransfer?.installed().orEmpty()
                                        val independentAreas = independentRecords.map { it.areaId }.distinct()
                                        val independentRegions = independentAreas.mapNotNull { areaId ->
                                            uk.co.tomosmaps.offline.independentLayerMapPackage(independentRecords, areaId)
                                                ?.takeIf { it.routingDataPath.startsWith("independent-layers/") }
                                        }
                                        val installedRegions = combinedRegions + independentRegions
                                        val normalizedPostcode = normalizeUkPostcode(postcode)
                                        val localPostcode = if (normalizedPostcode != null) {
                                            runCatching {
                                                val combinedHit = regionalPackageTransfer?.searchInstalledPackages(postcode, 1)
                                                    ?.firstOrNull { hit ->
                                                        hit.result.matchedBy == uk.co.tomosmaps.offline.OfflineSearchMatch.POSTCODE &&
                                                            normalizeUkPostcode(hit.result.record.postcode.orEmpty()) == normalizedPostcode
                                                    }
                                                combinedHit ?: independentAreas.firstNotNullOfOrNull { areaId ->
                                                    independentLayerTransfer?.searchInstalled(areaId, postcode, 1)
                                                        ?.firstOrNull { hit ->
                                                            hit.result.matchedBy == uk.co.tomosmaps.offline.OfflineSearchMatch.POSTCODE &&
                                                                normalizeUkPostcode(hit.result.record.postcode.orEmpty()) == normalizedPostcode
                                                        }
                                                }
                                            }.onFailure {
                                                routeMessage = "Unable to read the installed offline postcode index: ${it.message}"
                                            }.getOrNull()
                                        } else null
                                        val lookupResult = localPostcode?.let { hit ->
                                            UkPostcodeLookupResult.Success(
                                                postcode = hit.result.record.postcode ?: normalizedPostcode!!,
                                                coordinate = hit.result.record.coordinate
                                            )
                                        } ?: if (combinedRegions.isNotEmpty() || independentRecords.any { it.layer == uk.co.tomosmaps.offline.OfflineLayerArtifact.SEARCH }) {
                                            UkPostcodeLookupResult.Failure(
                                                "That postcode is not present in the installed offline region. Download the region covering the destination to search and route without internet."
                                            )
                                        } else {
                                            UkPostcodeLookupClient().use { it.lookup(postcode) }
                                        }
                                        when (val lookup = lookupResult) {
                                            is UkPostcodeLookupResult.Failure -> routeMessage = lookup.reason
                                            is UkPostcodeLookupResult.Success -> {
                                                routeMessage = if (localPostcode != null) "Found ${lookup.postcode} in offline regional data. Calculating route…" else "Found ${lookup.postcode}. Calculating route…"
                                                ValhallaRoutingClient().use { client ->
                                                    val origin = RouteCoordinate(originLatitude, originLongitude)
                                                    val request = ValhallaRouteRequest(
                                                        origin = origin,
                                                        destination = lookup.coordinate,
                                                        costing = ValhallaCosting.CAR,
                                                    )
                                                    val offlineRegion = installedRegions.firstOrNull { region ->
                                                        region.coverage?.let { bounds ->
                                                            bounds.contains(origin.latitude, origin.longitude) &&
                                                                bounds.contains(lookup.coordinate.latitude, lookup.coordinate.longitude)
                                                        } == true
                                                    }
                                                    val result = if (installedRegions.isNotEmpty()) {
                                                        when {
                                                            offlineRegion == null -> ValhallaRouteResult.Failure(
                                                                "This journey is outside the coverage of your installed offline regions. No online fallback was attempted."
                                                            )
                                                            offlineValhallaRuntime == null -> ValhallaRouteResult.Failure(
                                                                "The local offline routing engine is not available on this device."
                                                            )
                                                            else -> onStartOfflineNavigation?.invoke(
                                                                offlineRegion, origin, lookup.coordinate
                                                            ) ?: OfflineValhallaRoutingClient(client, offlineValhallaRuntime)
                                                                .calculateRoute(offlineRegion, origin, lookup.coordinate, ValhallaCosting.CAR)
                                                        }
                                                    } else {
                                                        client.calculateRoute(endpoint, request)
                                                    }
                                                    when (result) {
                                                        is ValhallaRouteResult.Success -> {
                                                            routeGeometry = result.geometry
                                                            announcementTracker.reset()
                                                            arrivalAnnouncementSent = false
                                                            navigation = navigationController.start(result.geometry)
                                                            activeOfflineRegion = offlineRegion
                                                            activeDestination = lookup.coordinate
                                                            routeDeviationMonitor.reset()
                                                            routeDeviationState = uk.co.tomosmaps.navigation.RouteDeviationState.ON_ROUTE
                                                            routeMessage = "Route to ${lookup.postcode} calculated · ${result.route.distanceMeters} m"
                                                        }
                                                        is ValhallaRouteResult.Failure -> routeMessage = result.reason
                                                    }
                                                }
                                            }
                                        }
                                    } catch (exception: Exception) {
                                        routeMessage = exception.message?.takeIf { it.isNotBlank() }
                                            ?: "Unable to look up the postcode or calculate the route. Check your connection and routing endpoint."
                                    } finally {
                                        isCalculatingRoute = false
                                    }
                                }
                            }
                        },
                        onEndPreview = {
                            scope.launch {
                                voiceGuidance.stop()
                                onStopOfflineNavigation?.invoke()
                                offlineValhallaRuntime?.stop()
                            }
                            announcementTracker.reset()
                            navigation = navigationController.stop()
                            routeDeviationMonitor.reset()
                            routeDeviationState = uk.co.tomosmaps.navigation.RouteDeviationState.ON_ROUTE
                            routeGeometry = null
                            activeOfflineRegion = null
                            activeDestination = null
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
                    OfflineRegionManager(
                        onDismiss = { showOfflineManager = false },
                        initialCenterLatitude = currentLatitude ?: mapState.cameraPosition.center.latitude,
                        initialCenterLongitude = currentLongitude ?: mapState.cameraPosition.center.longitude,
                        layerTransfer = independentLayerTransfer,
                    )
                }
                if (showRegionalPackageManager) {
                    regionalPackageTransfer?.let { transfer ->
                        RegionalPackageManager(
                            transfer = transfer,
                            publisherKeyConfigured = RegionalPackageBuildConfig.publisherKeyConfigured,
                            onDismiss = { showRegionalPackageManager = false }
                        )
                    }
                }
            }
        }
    }
}
