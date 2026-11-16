package uk.co.tomosmaps.offline

import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class IndependentLayerOfflineIntegrationTest {
    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }

    private fun fixture(
        root: File,
        layer: OfflineLayerArtifact,
        id: String,
        files: Map<String, ByteArray>,
    ): Pair<File, String> {
        val manifestFiles = files.map { (path, bytes) ->
            """{"path":"$path","sizeBytes":${bytes.size},"sha256":"${sha(bytes)}"}"""
        }
        val total = files.values.sumOf { it.size.toLong() }
        val manifest = """
            {"formatVersion":1,"id":"$id","areaId":"liverpool-city-region",
             "layer":"$layer","version":"1.0.0","totalSizeBytes":$total,
             "coverage":{"west":-3.35,"south":53.28,"east":-2.57,"north":53.71},
             "attribution":"© OpenStreetMap contributors",
             "files":[${manifestFiles.joinToString(",")}]}
        """.trimIndent()
        val archive = File(root, "$id.zip")
        ZipOutputStream(archive.outputStream()).use { zip ->
            files.forEach { (path, bytes) ->
                zip.putNextEntry(ZipEntry(path)); zip.write(bytes); zip.closeEntry()
            }
        }
        return archive to manifest
    }

    @Test
    fun installedLayersAreJoinedToMapStyleAndOfflineSearch() = runBlocking {
        val root = Files.createTempDirectory("tomos-independent-e2e").toFile()
        try {
            val fixtures = listOf(
                fixture(root, OfflineLayerArtifact.MAP, "liverpool-map-v1", mapOf(
                    "map/region.mbtiles" to "mbtiles".encodeToByteArray(),
                    "map/glyphs/Noto Sans Regular/0-255.pbf" to byteArrayOf(1, 2),
                    "map/glyphs/Noto Sans Regular/256-511.pbf" to byteArrayOf(3, 4),
                )),
                fixture(root, OfflineLayerArtifact.ROUTING, "liverpool-routing-v1", mapOf(
                    "routing/tiles.tar" to "graph".encodeToByteArray(),
                    "routing/admins.sqlite" to "admins".encodeToByteArray(),
                    "routing/timezones.sqlite" to "timezones".encodeToByteArray(),
                    "routing/valhalla.json" to "{}".encodeToByteArray(),
                )),
                fixture(root, OfflineLayerArtifact.SEARCH, "liverpool-search-v1", mapOf(
                    "search/search-index.json" to """{"formatVersion":1,"records":[{"id":"p1","label":"L1 1AA","latitude":53.4,"longitude":-2.99,"postcode":"L1 1AA"}]}""".encodeToByteArray(),
                )),
            )
            val installer = createOfflineLayerPackageInstaller(root.absolutePath)
            fixtures.forEach { (archive, manifest) ->
                installer.install(archive.absolutePath, manifest, sha(manifest.encodeToByteArray())).getOrThrow()
            }
            val transfer = createOfflineLayerPackageTransfer(root.absolutePath, "")
            val installed = transfer.installed()
            val packageData = independentLayerMapPackage(installed, "liverpool-city-region")
            assertNotNull(packageData)
            requireNotNull(packageData)
            assertTrue(packageData.mapDataPath.endsWith("map/region.mbtiles"))
            assertTrue(packageData.routingDataPath.endsWith("routing/tiles.tar"))
            assertTrue(packageData.searchDataPath?.endsWith("search/search-index.json") == true)

            val hits = transfer.searchInstalled("liverpool-city-region", "L1 1AA", 10)
            assertEquals("L1 1AA", hits.single().result.record.postcode)

            val style = createRegionalOfflineMapStyle(
                regionalMapDataFileUri(root.absolutePath, packageData.mapDataPath),
                packageData.attribution ?: error("Missing attribution"),
            )
            assertTrue(style.contains("mbtiles:"))
            assertTrue(style.contains("/glyphs/"))
            assertFalse(style.contains("https://"))

            transfer.remove("liverpool-city-region", OfflineLayerArtifact.MAP).getOrThrow()
            assertEquals(
                setOf(OfflineLayerArtifact.ROUTING, OfflineLayerArtifact.SEARCH),
                transfer.installed().map { it.layer }.toSet(),
            )
            assertEquals(null, independentLayerMapPackage(transfer.installed(), "liverpool-city-region"))
        } finally {
            root.deleteRecursively()
        }
    }
}
