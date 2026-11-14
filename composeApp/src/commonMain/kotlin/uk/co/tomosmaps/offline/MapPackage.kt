package uk.co.tomosmaps.offline

/**
 * Metadata for a locally installed regional package.
 * Paths are relative to the application's private data directory.
 */
data class MapPackage(
    val id: String,
    val displayName: String,
    val version: String,
    val mapDataPath: String,
    val routingDataPath: String,
    val searchDataPath: String? = null,
    val sizeBytes: Long,
    val sha256: String,
    val coverage: MapPackageCoverage? = null,
    val attribution: String? = null,
    /** Exact manifest-declared bytes for map data and glyphs. */
    val mapLayerSizeBytes: Long? = null,
    /** Exact manifest-declared bytes for routing graph and support files. */
    val routingLayerSizeBytes: Long? = null,
    /** Exact manifest-declared bytes for the postcode/place index, if present. */
    val searchLayerSizeBytes: Long? = null,
) {
    init {
        require(id.matches(Regex("[a-z0-9][a-z0-9._-]*"))) { "Invalid package id" }
        require(displayName.isNotBlank())
        require(version.isNotBlank())
        require(isSafePackagePath(mapDataPath)) { "Invalid map data path" }
        require(isSafePackagePath(routingDataPath)) { "Invalid routing data path" }
        require(searchDataPath == null || isSafePackagePath(searchDataPath)) { "Invalid search data path" }
        require(sizeBytes > 0)
        require(sha256.matches(Regex("[a-fA-F0-9]{64}"))) { "Expected a SHA-256 checksum" }
        require(listOf(mapLayerSizeBytes, routingLayerSizeBytes, searchLayerSizeBytes).all { it == null || it > 0 })
    }
}

interface MapPackageStore {
    suspend fun installedPackages(): List<MapPackage>
    suspend fun install(packageFile: String): Result<MapPackage>
    suspend fun remove(packageId: String): Result<Unit>
}

/** Shared status model for Android and Linux map download screens. */
sealed interface OfflineDataState {
    data object Empty : OfflineDataState
    data class Ready(val packages: List<MapPackage>) : OfflineDataState
    data class Updating(val packageId: String, val progress: Float) : OfflineDataState {
        init { require(progress in 0f..1f) }
    }
    data class Failed(val message: String) : OfflineDataState
}
