package uk.co.tomosmaps.offline

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
    suspend fun download(packageId: String): Result<MapPackage>
    suspend fun pause(packageId: String)
    suspend fun resume(packageId: String): Result<MapPackage>
    suspend fun delete(packageId: String): Result<Unit>
}

expect fun createRegionalPackageTransfer(
    storageDirectory: String,
    pinnedEd25519PublicKeyBase64: String,
): RegionalPackageTransfer
