package uk.co.tomosmaps.offline

/** Descriptor accepted only from a verified publisher-signed catalogue. */
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
        require(manifestUrl.isHttpsUrl() && archiveUrl.isHttpsUrl())
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
 * A platform-backed, resumable regional package pipeline. The publisher key is
 * pinned by the application; catalogue entries cannot be supplied directly by
 * callers, only obtained through refreshCatalogue() signature verification.
 */
interface RegionalPackageTransfer {
    suspend fun refreshCatalogue(catalogueUrl: String, signatureUrl: String): Result<List<RegionalPackageDescriptor>>
    suspend fun availablePackages(): List<RegionalPackageDescriptor>
    suspend fun installedPackages(): List<MapPackage>
    suspend fun transfers(): List<PackageTransferProgress>
    suspend fun searchInstalledPackages(query: String, limit: Int = OfflineSearchIndex.DEFAULT_LIMIT): List<RegionalPackageSearchHit>
    suspend fun download(packageId: String): Result<MapPackage>
    suspend fun pause(packageId: String)
    suspend fun resume(packageId: String): Result<MapPackage>
    suspend fun delete(packageId: String): Result<Unit>
}

expect fun createRegionalPackageTransfer(
    storageDirectory: String,
    pinnedEd25519PublicKeyBase64: String,
): RegionalPackageTransfer


/** Search result tagged with the installed region that supplied it. */
data class RegionalPackageSearchHit(
    val packageId: String,
    val packageName: String,
    val result: OfflineSearchResult,
)
