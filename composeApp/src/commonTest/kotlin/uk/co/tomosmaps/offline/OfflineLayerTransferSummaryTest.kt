package uk.co.tomosmaps.offline

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OfflineLayerTransferSummaryTest {
    @Test
    fun aggregatesProgressAndInstalledStorageAcrossIndependentLayers() {
        val summary = CombinedOfflineTransferSummary.from(
            listOf(
                OfflineLayerTransferProgress(OfflineLayerArtifact.MAP, OfflineLayerTransferState.DOWNLOADING,
                    bytesReceived = 50, totalBytes = 100, installedBytes = 0),
                OfflineLayerTransferProgress(OfflineLayerArtifact.ROUTING, OfflineLayerTransferState.INSTALLED,
                    bytesReceived = 200, totalBytes = 200, installedBytes = 180),
                OfflineLayerTransferProgress(OfflineLayerArtifact.SEARCH, OfflineLayerTransferState.QUEUED,
                    bytesReceived = 0, totalBytes = 50, installedBytes = 0),
            )
        )
        assertEquals(3, summary.selectedLayers)
        assertEquals(1, summary.completedLayers)
        assertEquals(250L, summary.receivedBytes)
        assertEquals(350L, summary.totalDownloadBytes)
        assertEquals(180L, summary.installedStorageBytes)
        assertEquals(250f / 350f, summary.fraction)
        assertTrue(summary.active)
        assertFalse(summary.failed)
    }

    @Test
    fun reportsCompleteOnlyWhenEverySelectedLayerIsInstalled() {
        val summary = CombinedOfflineTransferSummary.from(
            OfflineLayerArtifact.entries.map {
                OfflineLayerTransferProgress(it, OfflineLayerTransferState.INSTALLED,
                    bytesReceived = 10, totalBytes = 10, installedBytes = 8)
            }
        )
        assertEquals(1f, summary.fraction)
        assertEquals(24L, summary.installedStorageBytes)
        assertFalse(summary.active)
    }

    @Test
    fun rejectsDuplicateLayerRows() {
        val layer = OfflineLayerTransferProgress(OfflineLayerArtifact.MAP, OfflineLayerTransferState.QUEUED)
        val result = runCatching { CombinedOfflineTransferSummary(listOf(layer, layer)) }
        assertTrue(result.isFailure)
    }
}
