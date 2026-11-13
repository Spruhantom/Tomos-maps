package uk.co.tomosmaps.offline

/**
 * A signed package catalogue entry. URLs must be HTTPS and checksums are
 * verified before a package can become active.
 */
data class RegionalPackageDescriptor(
    val id: String,
    val name: String,
    val version: String,
    val manifestUrl: String,
    val manifestSha256: String,
    val estimatedSizeBytes: Long,
) {
    init {
        require(id.matches(Regex("[a-z0-9][a-z0-9._-]*")))
        require(name.isNotBlank() && version.isNotBlank())
        require(manifestUrl.startsWith("https://"))
        require(manifestSha256.matches(Regex("[a-fA-F0-9]{64}")))
        require(estimatedSizeBytes > 0)
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
 * Platform-backed transfer/install service. Implementations must persist
 * partial downloads, resume with HTTP Range where supported, verify the
 * manifest and every file checksum, and atomically activate the new package.
 */
interface RegionalPackageTransfer {
    suspend fun installedPackages(): List<MapPackage>
    suspend fun transfers(): List<PackageTransferProgress>
    suspend fun download(descriptor: RegionalPackageDescriptor)
    suspend fun pause(packageId: String)
    suspend fun resume(packageId: String)
    suspend fun delete(packageId: String)
}
