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
import androidx.compose.material3.LinearProgressIndicator
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Regional data manager for the signed Liverpool/North West package feed.
 * The signing key is deliberately supplied by the app owner at build time.
 */
@Composable
fun RegionalPackageManager(
    transfer: RegionalPackageTransfer,
    publisherKeyConfigured: Boolean,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var catalogueUrl by remember { mutableStateOf(RegionalPackageBuildConfig.CATALOGUE_URL) }
    var signatureUrl by remember { mutableStateOf(RegionalPackageBuildConfig.CATALOGUE_SIGNATURE_URL) }
    var available by remember { mutableStateOf<List<RegionalPackageDescriptor>>(emptyList()) }
    var installed by remember { mutableStateOf<List<MapPackage>>(emptyList()) }
    var transfers by remember { mutableStateOf<List<PackageTransferProgress>>(emptyList()) }
    var searchText by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<RegionalPackageSearchHit>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<MapPackage?>(null) }

    fun reload() {
        scope.launch {
            runCatching {
                installed = transfer.installedPackages()
                transfers = transfer.transfers()
                available = transfer.availablePackages()
            }.onFailure { error = it.message ?: "Unable to read regional package storage." }
        }
    }

    LaunchedEffect(transfer, publisherKeyConfigured) {
        reload()
        val configuredCatalogue = RegionalPackageBuildConfig.CATALOGUE_URL
        val configuredSignature = RegionalPackageBuildConfig.CATALOGUE_SIGNATURE_URL
        if (publisherKeyConfigured &&
            configuredCatalogue.startsWith("https://") &&
            configuredSignature.startsWith("https://")
        ) {
            refreshing = true
            transfer.refreshCatalogue(configuredCatalogue, configuredSignature)
                .onSuccess { available = it }
                .onFailure { error = it.message ?: "Unable to refresh the signed regional catalogue." }
            refreshing = false
            reload()
        }
    }
    LaunchedEffect(searchText, installed) {
        if (searchText.isBlank()) {
            searchResults = emptyList()
        } else {
            delay(250)
            runCatching { transfer.searchInstalledPackages(searchText) }
                .onSuccess { searchResults = it }
                .onFailure { error = it.message ?: "Offline search index could not be read." }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("UK regional downloads") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Download one verified regional package containing map data, routing data and postcode/place search for the same coverage.",
                    style = MaterialTheme.typography.bodySmall
                )
                if (!publisherKeyConfigured) {
                    Text(
                        "Publisher signing key is not configured yet. Catalogue downloads stay disabled until you add your Ed25519 public key to the app configuration.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                OutlinedTextField(
                    value = catalogueUrl,
                    onValueChange = { catalogueUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Signed catalogue HTTPS URL") },
                    singleLine = true,
                    enabled = publisherKeyConfigured && !refreshing
                )
                OutlinedTextField(
                    value = signatureUrl,
                    onValueChange = { signatureUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Catalogue signature HTTPS URL") },
                    singleLine = true,
                    enabled = publisherKeyConfigured && !refreshing
                )
                Button(
                    onClick = {
                        refreshing = true
                        error = null
                        scope.launch {
                            transfer.refreshCatalogue(catalogueUrl.trim(), signatureUrl.trim())
                                .onSuccess {
                                    available = it
                                    notice = "Verified catalogue loaded: ${it.size} regional packages."
                                }
                                .onFailure { error = it.message ?: "Catalogue verification failed." }
                            refreshing = false
                            reload()
                        }
                    },
                    enabled = publisherKeyConfigured && !refreshing &&
                        catalogueUrl.startsWith("https://") && signatureUrl.startsWith("https://")
                ) { Text(if (refreshing) "Verifying catalogue…" else "Refresh verified catalogue") }

                Text("Available regions", style = MaterialTheme.typography.titleMedium)
                if (available.isEmpty()) {
                    Text("No verified catalogue is loaded. Add the publisher key and catalogue URLs when they are ready.", style = MaterialTheme.typography.bodySmall)
                }
                available.forEach { descriptor ->
                    val progress = transfers.firstOrNull { it.packageId == descriptor.id }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(descriptor.name, style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Version ${descriptor.version} · ${formatRegionalBytes(descriptor.estimatedSizeBytes)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (progress != null && progress.status != PackageTransferStatus.COMPLETE) {
                            Text(
                                "${progress.status.name.lowercase().replace('_', ' ')} · ${formatRegionalBytes(progress.bytesReceived)} / ${formatRegionalBytes(progress.totalBytes)}",
                                style = MaterialTheme.typography.labelSmall
                            )
                            if (progress.totalBytes > 0) LinearProgressIndicator(
                                progress = { progress.fraction },
                                modifier = Modifier.fillMaxWidth()
                            )
                            progress.message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                enabled = !busy && progress?.status != PackageTransferStatus.DOWNLOADING && progress?.status != PackageTransferStatus.QUEUED,
                                onClick = {
                                    busy = true
                                    error = null
                                    scope.launch {
                                        val result = if (progress?.status == PackageTransferStatus.PAUSED) {
                                            transfer.resume(descriptor.id)
                                        } else {
                                            transfer.download(descriptor.id)
                                        }
                                        result
                                            .onSuccess { notice = "${it.displayName} installed and activated." }
                                            .onFailure { error = it.message ?: "Regional package download failed." }
                                        busy = false
                                        reload()
                                    }
                                }
                            ) { Text(if (progress?.status == PackageTransferStatus.PAUSED) "Resume" else "Download") }
                            if (progress?.status == PackageTransferStatus.DOWNLOADING || progress?.status == PackageTransferStatus.QUEUED) {
                                OutlinedButton(
                                    enabled = !busy,
                                    onClick = {
                                        busy = true
                                        scope.launch {
                                            runCatching { transfer.pause(descriptor.id) }
                                                .onFailure { error = it.message ?: "Unable to pause download." }
                                            busy = false
                                            reload()
                                        }
                                    }
                                ) { Text("Pause") }
                            }
                        }
                    }
                }

                Text("Installed packages", style = MaterialTheme.typography.titleMedium)
                if (installed.isEmpty()) Text("No complete regional packages installed.", style = MaterialTheme.typography.bodySmall)
                installed.forEach { region ->
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(region.displayName, style = MaterialTheme.typography.titleSmall)
                        Text("Version ${region.version} · ${formatRegionalBytes(region.sizeBytes)}", style = MaterialTheme.typography.bodySmall)
                        region.attribution?.let {
                            Text("Data attribution: $it", style = MaterialTheme.typography.labelSmall)
                        }
                        Text(
                            if (region.searchDataPath == null) "No offline postcode/place index included"
                            else "Offline map, routing and search files installed",
                            style = MaterialTheme.typography.labelSmall
                        )
                        OutlinedButton(
                            enabled = !busy,
                            onClick = { deleteTarget = region }
                        ) { Text("Remove package") }
                    }
                }

                Text("Search installed regions", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("UK postcode or place") },
                    singleLine = true
                )
                searchResults.forEach { hit ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(hit.result.record.label, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${hit.result.record.postcode ?: hit.packageName} · ${hit.result.record.coordinate.latitude}, ${hit.result.record.coordinate.longitude}",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                if (searchText.isNotBlank() && searchResults.isEmpty()) {
                    Text("No matching postcode or place in the installed regional indexes.", style = MaterialTheme.typography.bodySmall)
                }
                notice?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Done") } },
        dismissButton = {
            TextButton(onClick = { reload() }, enabled = !busy && !refreshing) { Text("Refresh status") }
        }
    )

    deleteTarget?.let { region ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Remove ${region.displayName}?") },
            text = { Text("This removes the active regional package and its installed map, routing and search files from this device.") },
            confirmButton = {
                Button(
                    enabled = !busy,
                    onClick = {
                        deleteTarget = null
                        busy = true
                        scope.launch {
                            transfer.delete(region.id)
                                .onSuccess { notice = "${region.displayName} removed." }
                                .onFailure { error = it.message ?: "Unable to remove package." }
                            busy = false
                            reload()
                        }
                    }
                ) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }
}

private fun formatRegionalBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> {
        val tenths = bytes * 10 / (1024L * 1024 * 1024)
        "${tenths / 10}.${tenths % 10} GB"
    }
    bytes >= 1024L * 1024 -> {
        val tenths = bytes * 10 / (1024L * 1024)
        "${tenths / 10}.${tenths % 10} MB"
    }
    bytes >= 1024L -> "${bytes / 1024L} KB"
    else -> "$bytes B"
}
