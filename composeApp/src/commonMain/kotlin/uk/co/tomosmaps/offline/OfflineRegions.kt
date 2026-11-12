package uk.co.tomosmaps.offline

/**
 * A rectangular region to cache for offline map display.
 *
 * Routing graphs, search indexes and voice assets are deliberately not part of
 * this request: a MapLibre tile pack only makes the basemap render offline.
 */
data class OfflineRegionRequest(
    val id: String,
    val name: String,
    val providerId: String,
    val styleUrl: String,
    val west: Double,
    val south: Double,
    val east: Double,
    val north: Double,
    val minZoom: Double = 5.0,
    val maxZoom: Double = 14.0,
    val pixelRatio: Float = 1.0f,
    /** Must be explicitly confirmed against the provider's current terms. */
    val providerAllowsOfflineDownloads: Boolean = false,
) {
    init {
        require(id.isNotBlank()) { "Region id must not be blank" }
        require(name.isNotBlank()) { "Region name must not be blank" }
        require(providerId.isNotBlank()) { "Provider id must not be blank" }
        require(styleUrl.startsWith("https://")) { "Style URL must use HTTPS" }
        require(listOf(id, name, providerId).none { it.contains('\n') }) {
            "Region metadata fields must not contain newlines"
        }
        require(west in -180.0..180.0 && east in -180.0..180.0) {
            "Longitude bounds must be between -180 and 180"
        }
        require(south in -90.0..90.0 && north in -90.0..90.0 && south < north) {
            "Latitude bounds must be ordered and between -90 and 90"
        }
        require(west < east) { "Antimeridian-crossing regions must be split into two packages" }
        require(minZoom >= 0.0 && maxZoom <= 22.0 && minZoom <= maxZoom) {
            "Zoom range must be ordered and within 0..22"
        }
        require(pixelRatio > 0.0f) { "Pixel ratio must be positive" }
        require(providerAllowsOfflineDownloads) {
            "Offline downloads are disabled until the map provider explicitly permits them"
        }
    }
}

enum class OfflineRegionActivity { DOWNLOADING, PAUSED, COMPLETE, ERROR, UNKNOWN }

data class OfflineRegionSnapshot(
    val id: String,
    val name: String,
    val providerId: String,
    val progressLabel: String,
    val activity: OfflineRegionActivity = OfflineRegionActivity.UNKNOWN,
    val completedResources: Long? = null,
    val errorMessage: String? = null,
)

interface OfflineRegionStore {
    suspend fun listRegions(): List<OfflineRegionSnapshot>
    suspend fun download(request: OfflineRegionRequest)
    suspend fun pause(regionId: String)
    suspend fun resume(regionId: String)
    suspend fun delete(regionId: String)
}

/** Platform implementations use MapLibre's native offline database. */
expect fun createOfflineRegionStore(): OfflineRegionStore
