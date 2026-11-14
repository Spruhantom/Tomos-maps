package uk.co.tomosmaps.offline

/** Independent artifacts selected for a custom offline area. */
enum class OfflineLayerArtifact(val displayName: String) {
    MAP("Map"),
    ROUTING("Roads and routing"),
    SEARCH("Postcodes and places"),
}

enum class OfflineLayerTransferState {
    NOT_SELECTED, QUEUED, DOWNLOADING, PAUSED, VERIFYING, INSTALLING, INSTALLED, FAILED
}

/** Progress is measured against the signed archive byte count, never an estimate. */
data class OfflineLayerTransferProgress(
    val layer: OfflineLayerArtifact,
    val state: OfflineLayerTransferState,
    val bytesReceived: Long = 0,
    val totalBytes: Long = 0,
    val installedBytes: Long = 0,
    val error: String? = null,
) {
    init {
        require(bytesReceived >= 0 && totalBytes >= 0 && installedBytes >= 0)
        require(totalBytes == 0L || bytesReceived <= totalBytes)
        require(error == null || error.isNotBlank())
    }

    val fraction: Float
        get() = when {
            state == OfflineLayerTransferState.INSTALLED -> 1f
            totalBytes <= 0 -> 0f
            else -> (bytesReceived.toDouble() / totalBytes).toFloat().coerceIn(0f, 1f)
        }
}

data class CombinedOfflineTransferSummary(
    val layers: List<OfflineLayerTransferProgress>,
) {
    init {
        require(layers.map { it.layer }.distinct().size == layers.size) {
            "Each offline layer may appear only once in a combined transfer."
        }
    }

    val selectedLayers: Int get() = layers.count { it.state != OfflineLayerTransferState.NOT_SELECTED }
    val completedLayers: Int get() = layers.count { it.state == OfflineLayerTransferState.INSTALLED }
    val receivedBytes: Long get() = layers.sumOf { it.bytesReceived }
    val totalDownloadBytes: Long get() = layers.sumOf { it.totalBytes }
    val installedStorageBytes: Long get() = layers.sumOf { it.installedBytes }
    val active: Boolean get() = layers.any {
        it.state in setOf(
            OfflineLayerTransferState.QUEUED,
            OfflineLayerTransferState.DOWNLOADING,
            OfflineLayerTransferState.VERIFYING,
            OfflineLayerTransferState.INSTALLING,
        )
    }
    val failed: Boolean get() = layers.any { it.state == OfflineLayerTransferState.FAILED }
    val fraction: Float
        get() = if (totalDownloadBytes <= 0L) {
            if (selectedLayers > 0 && completedLayers == selectedLayers) 1f else 0f
        } else {
            (receivedBytes.toDouble() / totalDownloadBytes).toFloat().coerceIn(0f, 1f)
        }

    companion object {
        fun from(layers: Iterable<OfflineLayerTransferProgress>) =
            CombinedOfflineTransferSummary(layers.toList())
    }
}
