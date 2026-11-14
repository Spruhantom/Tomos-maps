package uk.co.tomosmaps.offline

interface OfflineLayerPackageTransfer {
    suspend fun refreshCatalogue(catalogueUrl: String, signatureUrl: String): Result<List<OfflineLayerPackageDescriptor>>
    suspend fun available(): List<OfflineLayerPackageDescriptor>
    suspend fun installed(): List<OfflineLayerTransferRecord>
    suspend fun searchInstalled(areaId: String, query: String, limit: Int = OfflineSearchIndex.DEFAULT_LIMIT): List<RegionalPackageSearchHit>
    suspend fun transfers(): List<OfflineLayerTransferRecord>
    suspend fun download(packageId: String): Result<OfflineLayerTransferRecord>
    suspend fun pause(packageId: String)
    suspend fun resume(packageId: String): Result<OfflineLayerTransferRecord>
    suspend fun remove(areaId: String, layer: OfflineLayerArtifact): Result<Unit>
    suspend fun combinedSummary(areaId: String): CombinedOfflineTransferSummary
}

expect fun createOfflineLayerPackageTransfer(
    storageDirectory: String,
    pinnedEd25519PublicKeyBase64: String,
): OfflineLayerPackageTransfer
