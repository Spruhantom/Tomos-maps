package uk.co.tomosmaps.offline

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MapPackageManifestTest {
    private val checksum = "a".repeat(64)

    @Test
    fun acceptsValidRelativePackagePath() {
        assertTrue(isSafePackagePath("uk/england/london/map.mbtiles"))
    }

    @Test
    fun rejectsTraversalAndAbsolutePaths() {
        assertFalse(isSafePackagePath("../outside.mbtiles"))
        assertFalse(isSafePackagePath("regions/../../outside.mbtiles"))
        assertFalse(isSafePackagePath("/tmp/map.mbtiles"))
        assertFalse(isSafePackagePath("C:/maps/map.mbtiles"))
        assertFalse(isSafePackagePath("regions\\map.mbtiles"))
    }

    @Test
    fun requiresMapAndRoutingFiles() {
        assertFailsWith<IllegalArgumentException> {
            MapPackageManifest(
                formatVersion = 1,
                id = "gb-london",
                displayName = "London",
                packageVersion = "1.0.0",
                regionCode = "GB-LND",
                totalSizeBytes = 10,
                files = listOf(
                    MapPackageFile("london/map.mbtiles", MapPackageFileRole.MAP_DATA, 10, checksum)
                )
            )
        }
    }

    @Test
    fun rejectsMismatchedArchiveSize() {
        assertFailsWith<IllegalArgumentException> {
            MapPackageManifest(
                formatVersion = 1,
                id = "gb-london",
                displayName = "London",
                packageVersion = "1.0.0",
                regionCode = "GB-LND",
                totalSizeBytes = 99,
                files = listOf(
                    MapPackageFile("london/map.mbtiles", MapPackageFileRole.MAP_DATA, 10, checksum),
                    MapPackageFile("london/graph.bin", MapPackageFileRole.ROUTING_GRAPH, 20, checksum)
                )
            )
        }
    }

    @Test
    fun acceptsCompleteConsistentManifest() {
        MapPackageManifest(
            formatVersion = 1,
            id = "gb-london",
            displayName = "London",
            packageVersion = "1.0.0",
            regionCode = "GB-LND",
            totalSizeBytes = 30,
            files = listOf(
                MapPackageFile("london/map.mbtiles", MapPackageFileRole.MAP_DATA, 10, checksum),
                MapPackageFile("london/graph.bin", MapPackageFileRole.ROUTING_GRAPH, 20, checksum)
            )
        )
    }
}
