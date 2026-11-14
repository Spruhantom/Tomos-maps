package uk.co.tomosmaps.offline

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OfflinePackageLayerSizesTest {
    private fun file(path: String, role: MapPackageFileRole, size: Long) =
        MapPackageFile(path, role, size, "a".repeat(64))

    @Test
    fun reportsIndependentLayerSizesFromManifestFiles() {
        val manifest = MapPackageManifest(
            formatVersion = 1,
            id = "liverpool-city-region",
            displayName = "Liverpool City Region",
            packageVersion = "1.0.0",
            regionCode = "UK-LIV",
            totalSizeBytes = 1_500,
            files = listOf(
                file("map/region.mbtiles", MapPackageFileRole.MAP_DATA, 500),
                file("map/glyphs/font.pbf", MapPackageFileRole.MAP_GLYPHS, 100),
                file("routing/tiles.tar", MapPackageFileRole.ROUTING_GRAPH, 700),
                file("routing/admins.sqlite", MapPackageFileRole.ROUTING_SUPPORT, 100),
                file("search/search-index.json", MapPackageFileRole.SEARCH_INDEX, 100),
            ),
        )

        val sizes = manifest.layerSizesBytes()
        assertEquals(600L, sizes[OfflineMapLayer.MAP])
        assertEquals(800L, sizes[OfflineMapLayer.ROUTING])
        assertEquals(100L, sizes[OfflineMapLayer.SEARCH])
    }

    @Test
    fun missingOptionalSearchLayerHasNoInventedSize() {
        val manifest = MapPackageManifest(
            formatVersion = 1,
            id = "region-without-search",
            displayName = "Region without search",
            packageVersion = "1.0.0",
            regionCode = "UK-TEST",
            totalSizeBytes = 1_200,
            files = listOf(
                file("map/region.mbtiles", MapPackageFileRole.MAP_DATA, 500),
                file("routing/tiles.tar", MapPackageFileRole.ROUTING_GRAPH, 700),
            ),
        )

        assertNull(manifest.layerSizesBytes()[OfflineMapLayer.SEARCH])
    }
}
