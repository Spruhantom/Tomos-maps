package uk.co.tomosmaps.offline

interface RegionalPackageArchiveInstaller {
    suspend fun installedPackages(): List<MapPackage>
    suspend fun install(archiveFile: String, manifestJson: String, expectedManifestSha256: String): Result<MapPackage>
    suspend fun remove(packageId: String): Result<Unit>
}

expect fun createRegionalPackageArchiveInstaller(storageDirectory: String): RegionalPackageArchiveInstaller
