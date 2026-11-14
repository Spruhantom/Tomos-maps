package uk.co.tomosmaps.offline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun OfflineRegionManager(
    onDismiss: () -> Unit,
    initialCenterLatitude: Double = 53.4084,
    initialCenterLongitude: Double = -2.9916,
) {
    val store = remember { createOfflineRegionStore() }
    val scope = rememberCoroutineScope()
    var regions by remember { mutableStateOf<List<OfflineRegionSnapshot>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var workingRegion by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf<OfflineRegionSnapshot?>(null) }

    var centerLatitudeText by remember(initialCenterLatitude) {
        mutableStateOf(initialCenterLatitude.toString())
    }
    var centerLongitudeText by remember(initialCenterLongitude) {
        mutableStateOf(initialCenterLongitude.toString())
    }
    var areaSize by remember { mutableStateOf(OfflineAreaSize.CITY) }
    var selectedLayers by remember {
        mutableStateOf(setOf(OfflineMapLayer.MAP, OfflineMapLayer.ROUTING, OfflineMapLayer.SEARCH))
    }

    val estimate = remember(centerLatitudeText, centerLongitudeText, areaSize) {
        val latitude = centerLatitudeText.toDoubleOrNull()
        val longitude = centerLongitudeText.toDoubleOrNull()
        if (latitude == null || longitude == null) null
        else runCatching { OfflineAreaPlanner.plan(latitude, longitude, areaSize) }.getOrNull()
    }

    fun refresh() {
        scope.launch {
            loading = true
            error = null
            runCatching { store.listRegions() }
                .onSuccess { regions = it }
                .onFailure { error = it.message ?: "Unable to read offline map storage" }
            loading = false
        }
    }

    LaunchedEffect(store) { refresh() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Offline maps") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Online maps remain the default. Download an area here to keep its basemap available without an internet connection.",
                    style = MaterialTheme.typography.bodySmall
                )
                MapProviderCatalog.providers.forEach { provider ->
                    Text(provider.displayName, style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (provider.supportsOfflineDownloads) "Offline downloads permitted by provider policy"
                        else "Offline downloads disabled",
                        color = if (provider.supportsOfflineDownloads) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(provider.policyNote, style = MaterialTheme.typography.bodySmall)
                }

                Text("Choose the area centre", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = centerLatitudeText,
                        onValueChange = { centerLatitudeText = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("Latitude") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = centerLongitudeText,
                        onValueChange = { centerLongitudeText = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("Longitude") },
                        singleLine = true
                    )
                }
                Text(
                    "The selected rectangle is centred on these coordinates. You can use the map centre or enter coordinates for another location.",
                    style = MaterialTheme.typography.bodySmall
                )

                Text("How large should the area be?", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OfflineAreaSize.values().forEach { size ->
                        if (areaSize == size) {
                            Button(onClick = { areaSize = size }) { Text(size.title) }
                        } else {
                            OutlinedButton(onClick = { areaSize = size }) { Text(size.title) }
                        }
                    }
                }
                Text(
                    "Small: 1 km radius · City: 3 km radius · Large: 5 km radius",
                    style = MaterialTheme.typography.labelSmall
                )

                Text("Choose downloadable layers", style = MaterialTheme.typography.titleSmall)
                OfflineMapLayer.values().forEach { layer ->
                    val layerEstimate = estimate?.layers?.firstOrNull { it.layer == layer }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Checkbox(
                            checked = layer in selectedLayers,
                            onCheckedChange = { checked ->
                                selectedLayers = if (checked) selectedLayers + layer
                                else selectedLayers - layer
                            }
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(layer.title, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                when {
                                    layerEstimate == null -> "Enter valid centre coordinates to estimate"
                                    layerEstimate.available -> "${formatOfflineBytes(layerEstimate.estimatedBytesMinimum ?: 0L)}–${formatOfflineBytes(layerEstimate.estimatedBytesMaximum ?: 0L)} estimated"
                                    else -> "Requires a published regional package"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (layerEstimate?.available == false) MaterialTheme.colorScheme.secondary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (layerEstimate != null && !layerEstimate.available) {
                                Text(layerEstimate.note, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }

                if (estimate != null) {
                    Text(
                        "Area: ${formatCoordinate(estimate.bounds.south)}, ${formatCoordinate(estimate.bounds.west)} to ${formatCoordinate(estimate.bounds.north)}, ${formatCoordinate(estimate.bounds.east)}",
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(
                        "Estimated map tile pyramid: ${estimate.tileCount} tiles · ${formatOfflineBytes(estimate.selectedDownloadMinimumBytes)}–${formatOfflineBytes(estimate.selectedDownloadMaximumBytes)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (!estimate.withinProviderLimits) {
                        Text(
                            "This area exceeds the provider's 3,000-tile limit. Choose a smaller area.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                } else {
                    Text(
                        "Enter a valid latitude (-85 to 85) and longitude (-180 to 180).",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                if (OfflineMapLayer.ROUTING in selectedLayers || OfflineMapLayer.SEARCH in selectedLayers) {
                    Text(
                        "Roads/routing and postcode/place search are selected, but custom-area versions are not yet generated. They are not silently included in this map-only download; use Regional data for the existing combined offline package.",
                        color = MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Button(
                    enabled = !loading && workingRegion == null &&
                        OfflineMapLayer.MAP in selectedLayers &&
                        estimate?.withinProviderLimits == true,
                    onClick = {
                        val plan = estimate ?: return@Button
                        val latitude = centerLatitudeText.toDouble()
                        val longitude = centerLongitudeText.toDouble()
                        val id = customOfflineRegionId(latitude, longitude, areaSize)
                        workingRegion = id
                        error = null
                        scope.launch {
                            runCatching {
                                store.download(
                                    OfflineRegionRequest(
                                        id = id,
                                        name = "Custom ${areaSize.title.lowercase()} area",
                                        providerId = MapProviderCatalog.developmentPreview.id,
                                        styleUrl = MapProviderCatalog.developmentPreview.styleUrl,
                                        west = plan.bounds.west,
                                        south = plan.bounds.south,
                                        east = plan.bounds.east,
                                        north = plan.bounds.north,
                                        minZoom = OfflineAreaPlanner.MIN_ZOOM,
                                        maxZoom = OfflineAreaPlanner.MAX_ZOOM,
                                        providerAllowsOfflineDownloads =
                                            MapProviderCatalog.developmentPreview.supportsOfflineDownloads,
                                    )
                                )
                            }.onFailure {
                                error = it.message ?: "Unable to start offline map download"
                            }
                            workingRegion = null
                            refresh()
                        }
                    }
                ) {
                    Text("Download map layer")
                }

                Text("Installed map areas", style = MaterialTheme.typography.titleSmall)
                if (loading) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator()
                        Text("Checking installed areas…")
                    }
                } else if (regions.isEmpty()) {
                    Text("No offline map areas installed yet.", style = MaterialTheme.typography.bodySmall)
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        regions.forEach { region ->
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(region.name, style = MaterialTheme.typography.titleMedium)
                                Text(region.progressLabel, style = MaterialTheme.typography.bodySmall)
                                region.completedResources?.let {
                                    Text("$it map resources downloaded", style = MaterialTheme.typography.labelSmall)
                                }
                                region.errorMessage?.let {
                                    Text(it, color = MaterialTheme.colorScheme.error)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (region.activity == OfflineRegionActivity.DOWNLOADING) {
                                        OutlinedButton(
                                            onClick = {
                                                workingRegion = region.id
                                                scope.launch {
                                                    runCatching { store.pause(region.id) }
                                                        .onFailure { error = it.message ?: "Unable to pause download" }
                                                    workingRegion = null
                                                    refresh()
                                                }
                                            },
                                            enabled = workingRegion == null
                                        ) { Text("Pause") }
                                    } else if (region.activity == OfflineRegionActivity.PAUSED) {
                                        Button(
                                            onClick = {
                                                workingRegion = region.id
                                                scope.launch {
                                                    runCatching { store.resume(region.id) }
                                                        .onFailure { error = it.message ?: "Unable to resume download" }
                                                    workingRegion = null
                                                    refresh()
                                                }
                                            },
                                            enabled = workingRegion == null
                                        ) { Text("Resume") }
                                    }
                                    OutlinedButton(
                                        onClick = { confirmDelete = region },
                                        enabled = workingRegion == null
                                    ) { Text("Remove") }
                                }
                            }
                        }
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Text(
                    "Map downloads are limited to 3,000 tiles or 50 MB and four simultaneous requests. Download size is an estimate; the provider's actual responses determine final storage use. Routing and search remain in the separate regional-package pipeline.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Done") } },
        dismissButton = {
            TextButton(onClick = { refresh() }, enabled = !loading && workingRegion == null) {
                Text("Refresh")
            }
        }
    )

    confirmDelete?.let { region ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Remove " + region.name + "?") },
            text = {
                Text("This deletes the downloaded map tiles from this device. It does not remove routing or search data.")
            },
            confirmButton = {
                Button(
                    enabled = workingRegion == null,
                    onClick = {
                        confirmDelete = null
                        workingRegion = region.id
                        scope.launch {
                            runCatching { store.delete(region.id) }
                                .onSuccess { refresh() }
                                .onFailure { error = it.message ?: "Unable to remove this area" }
                            workingRegion = null
                        }
                    }
                ) { Text("Remove area") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("Cancel") }
            }
        )
    }
}

private fun customOfflineRegionId(
    latitude: Double,
    longitude: Double,
    size: OfflineAreaSize,
): String {
    fun coordinate(value: Double): String =
        (value * 10_000.0).roundToInt().toString().replace("-", "m")
    return "custom-${size.name.lowercase()}-${coordinate(latitude)}-${coordinate(longitude)}"
}

private fun formatCoordinate(value: Double): String =
    (value * 10_000.0).roundToInt().toString().let { raw ->
        val negative = raw.startsWith("-")
        val digits = raw.removePrefix("-").padStart(5, '0')
        (if (negative) "-" else "") + digits.dropLast(4) + "." + digits.takeLast(4)
    }

private fun formatOfflineBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L * 1024L -> formatTenths(bytes, 1024L * 1024L * 1024L, "GB")
    bytes >= 1024L * 1024L -> formatTenths(bytes, 1024L * 1024L, "MB")
    bytes >= 1024L -> "${bytes / 1024L} KB"
    else -> "$bytes B"
}

private fun formatTenths(bytes: Long, divisor: Long, unit: String): String {
    val tenths = bytes * 10L / divisor
    return "${tenths / 10L}.${tenths % 10L} $unit"
}
