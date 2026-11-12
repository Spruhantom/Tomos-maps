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
    val sha256: String
) {
    init {
        require(id.matches(Regex("[a-z0-9][a-z0-9._-]*"))) { "Invalid map package id" }
        require(displayName.isNotBlank())
        require(version.isNotBlank())
        require(isSafePackagePath(mapDataPath)) { "Invalid map data path" }
        require(isSafePackagePath(routingDataPath)) { "Invalid routing data path" }
        require(searchDataPath == null || isSafePackagePath(searchDataPath)) { "Invalid search data path" }
        require(sizeBytes > 0)
        require(sha256.matches(Regex("[a-fA-F0-9]{64}"))) { "Expected a SHA-256 checksum" }
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
