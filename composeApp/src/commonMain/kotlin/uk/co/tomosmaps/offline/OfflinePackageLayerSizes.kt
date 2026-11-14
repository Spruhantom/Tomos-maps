package uk.co.tomosmaps.offline

/**
 * Byte totals for independently selectable data layers in a verified package.
 *
 * These are exact uncompressed file sizes declared by the signed package
 * manifest, not estimates for a differently-sized custom area. Map glyphs
 * belong to the map layer; routing support databases belong to routing.
 */
fun MapPackageManifest.layerSizesBytes(): Map<OfflineMapLayer, Long?> {
    fun total(vararg roles: MapPackageFileRole): Long? {
        val matching = files.filter { it.role in roles.toSet() }
        return matching.takeIf { it.isNotEmpty() }?.sumOf { it.sizeBytes }
    }

    return mapOf(
        OfflineMapLayer.MAP to total(
            MapPackageFileRole.MAP_DATA,
            MapPackageFileRole.MAP_GLYPHS,
        ),
        OfflineMapLayer.ROUTING to total(
            MapPackageFileRole.ROUTING_GRAPH,
            MapPackageFileRole.ROUTING_SUPPORT,
        ),
        OfflineMapLayer.SEARCH to total(MapPackageFileRole.SEARCH_INDEX),
    )
}
