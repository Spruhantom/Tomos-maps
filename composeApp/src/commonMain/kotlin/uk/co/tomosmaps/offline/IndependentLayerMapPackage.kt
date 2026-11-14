package uk.co.tomosmaps.offline

/**
 * Adapts independently verified artifacts to the existing map/navigation
 * package contract. A MAP artifact is sufficient to render the offline map;
 * ROUTING is optional until a route is requested.
 */
fun independentLayerMapPackage(
    records: List<OfflineLayerTransferRecord>,
    areaId: String,
): MapPackage? {
    val area = records.filter {
        it.areaId == areaId && it.state == OfflineLayerTransferState.INSTALLED &&
            it.installedDirectory != null && it.installedCoverage != null
    }
    val map = area.firstOrNull { it.layer == OfflineLayerArtifact.MAP } ?: return null
    val routing = area.firstOrNull { it.layer == OfflineLayerArtifact.ROUTING }
    val search = area.firstOrNull { it.layer == OfflineLayerArtifact.SEARCH }

    fun artifactFile(record: OfflineLayerTransferRecord?, suffix: String): String? {
        if (record == null) return null
        val root = requireNotNull(record.installedDirectory)
        val path = record.installedFiles.firstOrNull { it.substringAfterLast('/').equals(suffix, true) }
            ?: record.installedFiles.firstOrNull { it.endsWith(suffix, true) }
        return path?.let { "$root/$it" }?.takeIf(::isSafePackagePath)
    }

    val mapPath = artifactFile(map, ".mbtiles") ?: return null
    val routingPath = artifactFile(routing, "tiles.tar")
        ?: "independent-unavailable/$areaId/tiles.tar"
    val searchPath = search?.let { record ->
        val root = requireNotNull(record.installedDirectory)
        record.installedFiles.singleOrNull { it.endsWith(".json", true) }
            ?.let { "$root/$it" }?.takeIf(::isSafePackagePath)
    }
    val coverage = map.installedCoverage ?: return null
    if (routing?.installedCoverage != null && routing.installedCoverage != coverage) return null
    if (search?.installedCoverage != null && search.installedCoverage != coverage) return null
    val manifestHash = map.manifestSha256 ?: return null
    val size = map.installedBytes + (routing?.installedBytes ?: 0L) + (search?.installedBytes ?: 0L)
    return MapPackage(
        id = areaId,
        displayName = areaId.replace('-', ' ').replaceFirstChar { it.uppercase() },
        version = map.installedVersion ?: "1",
        mapDataPath = mapPath,
        routingDataPath = routingPath,
        searchDataPath = searchPath,
        sizeBytes = size.coerceAtLeast(1L),
        sha256 = manifestHash,
        coverage = coverage,
        attribution = map.installedAttribution,
        mapLayerSizeBytes = map.installedBytes,
        routingLayerSizeBytes = routing?.installedBytes,
        searchLayerSizeBytes = search?.installedBytes,
    )
}
