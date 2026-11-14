package uk.co.tomosmaps.offline

interface OfflineLayerPackageInstaller {
    suspend fun installed(): List<OfflineLayerTransferRecord>
    suspend fun install(archivePath: String, manifestJson: String, expectedManifestSha256: String): Result<OfflineLayerTransferRecord>
    suspend fun remove(areaId: String, layer: OfflineLayerArtifact): Result<Unit>
}

expect fun createOfflineLayerPackageInstaller(storageDirectory: String): OfflineLayerPackageInstaller
