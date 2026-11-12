package uk.co.tomosmaps.offline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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

@Composable
fun OfflineRegionManager(onDismiss: () -> Unit) {
    val store = remember { createOfflineRegionStore() }
    val scope = rememberCoroutineScope()
    var regions by remember { mutableStateOf<List<OfflineRegionSnapshot>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var workingRegion by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf<OfflineRegionSnapshot?>(null) }

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
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Installed map tiles are managed separately from routing, search and voice packages.",
                    style = MaterialTheme.typography.bodySmall
                )
                if (loading) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator()
                        Text("Checking installed regions…")
                    }
                } else if (regions.isEmpty()) {
                    Text("No offline map regions installed yet.")
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(regions, key = { it.id }) { region ->
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(region.name, style = MaterialTheme.typography.titleMedium)
                                Text(region.progressLabel, style = MaterialTheme.typography.bodySmall)
                                region.completedResources?.let {
                                    Text(it.toString() + " map resources downloaded", style = MaterialTheme.typography.labelSmall)
                                }
                                region.errorMessage?.let {
                                    Text(it, color = MaterialTheme.colorScheme.error)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = { confirmDelete = region },
                                        enabled = workingRegion == null
                                    ) {
                                        Text("Remove")
                                    }
                                }
                            }
                        }
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Text(
                    "New downloads are unavailable until TomOS selects a provider whose licence permits offline caching.",
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
                                .onFailure { error = it.message ?: "Unable to remove this region" }
                            workingRegion = null
                        }
                    }
                ) { Text("Remove region") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("Cancel") }
            }
        )
    }
}
