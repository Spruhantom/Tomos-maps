package uk.co.tomosmaps.offline

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
    private companion object {
        val completionScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

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
        require(request.providerAllowsOfflineDownloads) {
            "The selected provider has not approved offline downloads."
        }
        require(request.providerId == MapProviderCatalog.developmentPreview.id) {
            "The OpenFreeMap download pipeline only accepts its approved provider."
        }
        require(request.styleUrl.startsWith("https://tiles.openfreemap.org/")) {
            "OpenFreeMap offline packs must use the approved OpenFreeMap host."
        }

        val existing = findPack(request.id)
        if (existing != null) {
            val progress = existing.downloadProgress.value
            if (progress is DownloadProgress.Healthy &&
                (progress.status == DownloadStatus.Downloading ||
                    progress.status == DownloadStatus.Complete)
            ) {
                return
            }
            startTracked(existing, request.id)
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
        startTracked(pack, request.id)
    }

    override suspend fun pause(regionId: String) {
        storage.pause(requirePack(regionId))
        OpenFreeMapResourcePipeline.quota.finish(regionId)
    }

    override suspend fun resume(regionId: String) {
        val pack = requirePack(regionId)
        startTracked(pack, regionId)
    }

    override suspend fun delete(regionId: String) {
        storage.delete(requirePack(regionId))
        OpenFreeMapResourcePipeline.quota.finish(regionId)
    }

    private suspend fun startTracked(pack: OfflinePack, regionId: String) {
        val generation = OpenFreeMapResourcePipeline.quota.begin(regionId)
        val completionWatcher = completionScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                // Subscribe before resuming: a small pack can finish very quickly.
                // drop(1) ignores the existing PAUSED/ERROR snapshot and watches
                // only the state changes caused by this resume attempt.
                pack.downloadProgress.drop(1).first { progress ->
                    when (progress) {
                        is DownloadProgress.Healthy -> progress.status == DownloadStatus.Complete
                        is DownloadProgress.Error, is DownloadProgress.TileLimitExceeded -> true
                        else -> false
                    }
                }
            } finally {
                OpenFreeMapResourcePipeline.quota.finish(regionId, generation)
            }
        }
        try {
            storage.resume(pack)
        } catch (exception: Exception) {
            completionWatcher.cancel()
            OpenFreeMapResourcePipeline.quota.finish(regionId, generation)
            throw exception
        }
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
