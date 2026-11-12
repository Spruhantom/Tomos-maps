package uk.co.tomosmaps.offline

import kotlinx.coroutines.flow.first
import org.maplibre.compose.map.DefaultMapRuntime
import org.maplibre.compose.offline.DownloadProgress
import org.maplibre.compose.offline.DownloadStatus
import org.maplibre.compose.offline.OfflinePack
import org.maplibre.compose.offline.OfflinePackDefinition
import org.maplibre.compose.offline.OfflineStorage
import org.maplibre.compose.offline.OfflineStorageState
import org.maplibre.compose.offline.offlineStorage
import org.maplibre.spatialk.geojson.BoundingBox

actual fun createOfflineRegionStore(): OfflineRegionStore =
    MapLibreOfflineRegionStore(DefaultMapRuntime.instance.offlineStorage)

internal class MapLibreOfflineRegionStore(
    private val storage: OfflineStorage,
) : OfflineRegionStore {

    override suspend fun listRegions(): List<OfflineRegionSnapshot> =
        readyPacks().mapNotNull { pack ->
            val metadata = OfflineRegionMetadata.decode(pack.metadata.value) ?: return@mapNotNull null
            val progress = pack.downloadProgress.value
            val completed = (progress as? DownloadProgress.Healthy)?.completedResourceCount
            val error = when (progress) {
                is DownloadProgress.Error -> progress.message
                is DownloadProgress.TileLimitExceeded -> "Tile limit exceeded: ${progress.limit}"
                else -> null
            }
            OfflineRegionSnapshot(
                id = metadata.id,
                name = metadata.name,
                providerId = metadata.providerId,
                progressLabel = progress.label(),
                activity = progress.activity(),
                completedResources = completed?.toLong(),
                errorMessage = error,
            )
        }.sortedWith(compareBy<OfflineRegionSnapshot> { it.name.lowercase() }.thenBy { it.id })

    override suspend fun download(request: OfflineRegionRequest) {
        val existing = findPack(request.id)
        if (existing != null) {
            // A repeated request must not resume a pack that is already active or complete.
            // Paused and failed/incomplete packs remain eligible for resumption.
            val progress = existing.downloadProgress.value
            if (progress is DownloadProgress.Healthy &&
                (progress.status == DownloadStatus.Downloading ||
                    progress.status == DownloadStatus.Complete)
            ) {
                return
            }
            storage.resume(existing)
            return
        }
        val pack = storage.create(
            definition = OfflinePackDefinition.TilePyramid(
                styleUrl = request.styleUrl,
                bounds = BoundingBox(
                    west = request.west,
                    south = request.south,
                    east = request.east,
                    north = request.north,
                ),
                pixelRatio = request.pixelRatio,
                minZoom = request.minZoom,
                maxZoom = request.maxZoom,
            ),
            metadata = OfflineRegionMetadata(
                id = request.id,
                name = request.name,
                providerId = request.providerId,
            ).encode(),
        )
        storage.resume(pack)
    }

    override suspend fun pause(regionId: String) {
        storage.pause(requirePack(regionId))
    }

    override suspend fun resume(regionId: String) {
        storage.resume(requirePack(regionId))
    }

    override suspend fun delete(regionId: String) {
        storage.delete(requirePack(regionId))
    }

    private suspend fun requirePack(regionId: String): OfflinePack =
        findPack(regionId) ?: error("Offline region '$regionId' was not found")

    private suspend fun findPack(regionId: String): OfflinePack? =
        findUniqueOfflineRegion(readyPacks(), regionId) { pack ->
            OfflineRegionMetadata.decode(pack.metadata.value)
        }

    private suspend fun readyPacks(): Set<OfflinePack> =
        when (val state = storage.state.first { it !is OfflineStorageState.Loading }) {
            is OfflineStorageState.Ready -> state.packs
            is OfflineStorageState.Failed ->
                throw IllegalStateException("MapLibre offline storage failed to initialise", state.cause)
            else -> emptySet()
        }

    private fun DownloadProgress.activity(): OfflineRegionActivity = when (this) {
        is DownloadProgress.Healthy -> when (status) {
            DownloadStatus.Downloading -> OfflineRegionActivity.DOWNLOADING
            DownloadStatus.Paused -> OfflineRegionActivity.PAUSED
            DownloadStatus.Complete -> OfflineRegionActivity.COMPLETE
            else -> OfflineRegionActivity.UNKNOWN
        }
        is DownloadProgress.Error, is DownloadProgress.TileLimitExceeded ->
            OfflineRegionActivity.ERROR
        else -> OfflineRegionActivity.UNKNOWN
    }

    private fun DownloadProgress.label(): String = when (this) {
        is DownloadProgress.Healthy ->
            "Resources: $completedResourceCount · $status"
        is DownloadProgress.Error -> "Error: $message"
        is DownloadProgress.TileLimitExceeded -> "Tile limit exceeded: $limit"
        else -> toString()
    }

}
