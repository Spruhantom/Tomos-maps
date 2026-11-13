package uk.co.tomosmaps.offline

/**
 * A package descriptor accepted only from a verified signed catalogue.
 * URLs are HTTPS and both the manifest and archive are pinned by SHA-256.
 */
data class RegionalPackageDescriptor(
    val id: String,
    val name: String,
    val version: String,
    val manifestUrl: String,
    val manifestSha256: String,
    val estimatedSizeBytes: Long,
    val archiveUrl: String,
    val archiveSha256: String,
) {
    init {
        require(id.matches(Regex("[a-z0-9][a-z0-9._-]*")))
        require(name.isNotBlank() && version.matches(Regex("[A-Za-z0-9][A-Za-z0-9._+-]*")))
        require(manifestUrl.isHttpsUrl())
        require(archiveUrl.isHttpsUrl())
        require(manifestSha256.matches(Regex("[a-fA-F0-9]{64}")))
        require(archiveSha256.matches(Regex("[a-fA-F0-9]{64}")))
        require(estimatedSizeBytes in 1..MAX_PACKAGE_DOWNLOAD_BYTES)
    }

    private fun String.isHttpsUrl(): Boolean =
        startsWith("https://") && length <= 2048 && none { it.isWhitespace() || it.isISOControl() }

    companion object {
        const val MAX_PACKAGE_DOWNLOAD_BYTES = 20L * 1024 * 1024 * 1024
    }
}

enum class PackageTransferStatus { QUEUED, DOWNLOADING, PAUSED, VERIFYING, INSTALLING, COMPLETE, FAILED }

data class PackageTransferProgress(
    val packageId: String,
    val status: PackageTransferStatus,
    val bytesReceived: Long,
    val totalBytes: Long,
    val message: String? = null,
) {
    init {
        require(packageId.isNotBlank())
        require(bytesReceived >= 0 && totalBytes >= 0)
        require(totalBytes == 0L || bytesReceived <= totalBytes)
    }
    val fraction: Float
        get() = if (totalBytes <= 0L) 0f else (bytesReceived.toDouble() / totalBytes).toFloat().coerceIn(0f, 1f)
}

/**
 * Platform-backed transfer/install service. It must persist partial downloads,
 * resume with HTTP Range, validate the signed catalogue descriptor, verify the
 * archive/manifest and every file, then atomically activate the package.
 */
interface RegionalPackageTransfer {
    suspend fun installedPackages(): List<MapPackage>
    suspend fun transfers(): List<PackageTransferProgress>
    suspend fun download(descriptor: RegionalPackageDescriptor)
    suspend fun pause(packageId: String)
    suspend fun resume(packageId: String)
    suspend fun delete(packageId: String)
}
