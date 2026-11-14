package uk.co.tomosmaps.offline

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class IndependentLayerMapPackageTest {
    private val coverage = MapPackageCoverage(west = -3.2, south = 53.2, east = -2.5, north = 53.7)
    private val hash = "a".repeat(64)

    private fun record(
        layer: OfflineLayerArtifact,
        files: List<String>,
        coverage: MapPackageCoverage = this.coverage,
        size: Long = 100,
    ) = OfflineLayerTransferRecord(
        packageId = "liverpool-${layer.name.lowercase()}-v1",
        areaId = "liverpool",
        layer = layer,
        state = OfflineLayerTransferState.INSTALLED,
        bytesReceived = size,
        archiveSizeBytes = size,
        installedBytes = size,
        installedDirectory = "independent-layers/liverpool/${layer.name.lowercase()}/liverpool-${layer.name.lowercase()}-v1",
        installedFiles = files,
        installedVersion = "1",
        installedCoverage = coverage,
        installedAttribution = "© OpenStreetMap contributors",
        manifestSha256 = hash,
    )

    @Test
    fun mapLayerAloneCanCreateOfflineMapPackage() {
        val map = record(OfflineLayerArtifact.MAP, listOf("liverpool.mbtiles"))
        val packageData = independentLayerMapPackage(listOf(map), "liverpool")
        assertNotNull(packageData)
        assertEquals(
            "independent-layers/liverpool/map/liverpool-map-v1/liverpool.mbtiles",
            packageData.mapDataPath,
        )
        assertNull(packageData.searchDataPath)
        assertEquals(100L, packageData.mapLayerSizeBytes)
    }

    @Test
    fun allIndependentLayersAreResolvedIntoRuntimePathsAndSizes() {
        val records = listOf(
            record(OfflineLayerArtifact.MAP, listOf("liverpool.mbtiles"), size = 100),
            record(OfflineLayerArtifact.ROUTING, listOf("tiles.tar", "admins.sqlite", "timezones.sqlite", "valhalla.json"), size = 200),
            record(OfflineLayerArtifact.SEARCH, listOf("postcode-index.json"), size = 50),
        )
        val packageData = assertNotNull(independentLayerMapPackage(records, "liverpool"))
        assertEquals(
            "independent-layers/liverpool/routing/liverpool-routing-v1/tiles.tar",
            packageData.routingDataPath,
        )
        assertEquals(
            "independent-layers/liverpool/search/liverpool-search-v1/postcode-index.json",
            packageData.searchDataPath,
        )
        assertEquals(350L, packageData.sizeBytes)
        assertEquals(200L, packageData.routingLayerSizeBytes)
        assertEquals(50L, packageData.searchLayerSizeBytes)
    }

    @Test
    fun incompatibleCoverageIsNotCombined() {
        val other = MapPackageCoverage(west = -3.0, south = 53.2, east = -2.5, north = 53.7)
        val records = listOf(
            record(OfflineLayerArtifact.MAP, listOf("liverpool.mbtiles")),
            record(OfflineLayerArtifact.ROUTING, listOf("tiles.tar"), coverage = other),
        )
        assertNull(independentLayerMapPackage(records, "liverpool"))
    }

    @Test
    fun unverifiedOrIncompleteMapLayerIsNotExposed() {
        val record = record(OfflineLayerArtifact.MAP, listOf("not-a-map.txt"))
        assertNull(independentLayerMapPackage(listOf(record), "liverpool"))
        assertNull(independentLayerMapPackage(listOf(record.copy(manifestSha256 = null)), "liverpool"))
    }
}
