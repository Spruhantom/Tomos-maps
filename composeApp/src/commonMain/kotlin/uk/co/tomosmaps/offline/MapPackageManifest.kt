package uk.co.tomosmaps.offline

/**
 * Portable manifest contract for an offline region archive.
 *
 * The archive installer must verify every file's SHA-256 and size before making
 * the package visible. Installation should be staged and atomically committed.
 */
data class MapPackageManifest(
    val formatVersion: Int,
    val id: String,
    val displayName: String,
    val packageVersion: String,
    val regionCode: String,
    val totalSizeBytes: Long,
    val files: List<MapPackageFile>
) {
    init {
        require(formatVersion == CURRENT_FORMAT_VERSION) { "Unsupported map package format" }
        require(id.matches(Regex("[a-z0-9][a-z0-9._-]*"))) { "Invalid map package id" }
        require(displayName.isNotBlank())
        require(packageVersion.isNotBlank())
        require(regionCode.matches(Regex("[A-Z0-9_-]{2,16}"))) { "Invalid region code" }
        require(totalSizeBytes > 0)
        require(files.isNotEmpty())
        require(files.map { it.path }.distinct().size == files.size) { "Duplicate package file path" }
        require(files.any { it.role == MapPackageFileRole.MAP_DATA }) { "Map data is required" }
        require(files.any { it.role == MapPackageFileRole.ROUTING_GRAPH }) { "Routing graph is required" }
        require(files.sumOf { it.sizeBytes } == totalSizeBytes) { "Package size does not match file sizes" }
    }

    companion object {
        const val CURRENT_FORMAT_VERSION = 1
    }
}

enum class MapPackageFileRole {
    MAP_DATA,
    ROUTING_GRAPH,
    SEARCH_INDEX,
    VOICE_GUIDANCE,
    STYLE_METADATA
}

data class MapPackageFile(
    val path: String,
    val role: MapPackageFileRole,
    val sizeBytes: Long,
    val sha256: String
) {
    init {
        require(isSafePackagePath(path)) { "Package paths must be safe relative paths" }
        require(sizeBytes > 0)
        require(sha256.matches(Regex("[a-fA-F0-9]{64}"))) { "Expected a SHA-256 checksum" }
    }
}

/**
 * Reject absolute paths, traversal segments and platform-specific separators.
 * Package paths always use forward slashes on every supported platform.
 */
fun isSafePackagePath(path: String): Boolean {
    if (path.isBlank() || path.startsWith("/") || path.contains('\\') || path.contains(':')) {
        return false
    }
    val segments = path.split('/')
    return segments.all { it.isNotBlank() && it != "." && it != ".." }
}
