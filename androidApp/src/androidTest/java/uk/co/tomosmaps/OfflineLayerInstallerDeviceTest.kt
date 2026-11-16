package uk.co.tomosmaps

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.tomosmaps.offline.OfflineLayerArtifact
import uk.co.tomosmaps.offline.OfflineLayerPackageInstaller
import uk.co.tomosmaps.offline.OfflineLayerTransferState
import uk.co.tomosmaps.offline.createOfflineLayerPackageInstaller
import uk.co.tomosmaps.offline.createOfflineLayerPackageTransfer
import uk.co.tomosmaps.offline.createRegionalOfflineMapStyle
import uk.co.tomosmaps.offline.independentLayerMapPackage
import uk.co.tomosmaps.offline.regionalMapDataFileUri
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class OfflineLayerInstallerDeviceTest {
    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }

    private fun createLayer(
        root: File,
        layer: OfflineLayerArtifact,
        id: String,
        assets: Map<String, ByteArray>,
    ): Pair<File, String> {
        val entries = assets.map { (path, bytes) ->
            """{"path":"$path","sizeBytes":${bytes.size},"sha256":"${sha(bytes)}"}"""
        }
        val total = assets.values.sumOf { it.size.toLong() }
        val manifest = """
            {"formatVersion":1,"id":"$id","areaId":"liverpool-city-region",
             "layer":"$layer","version":"2026.11.15","totalSizeBytes":$total,
             "coverage":{"west":-3.35,"south":53.28,"east":-2.57,"north":53.71},
             "attribution":"© OpenStreetMap contributors",
             "files":[${entries.joinToString(",")}]}
        """.trimIndent()
        val archive = File(root, "$id.zip")
        ZipOutputStream(archive.outputStream()).use { zip ->
            assets.forEach { (path, bytes) ->
                zip.putNextEntry(ZipEntry(path))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return archive to manifest
    }

    @Test
    fun androidInstallsRendersSearchesAndRemovesIndependentLayersOffline() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = File(context.filesDir, "offline-layer-device-test").apply {
            deleteRecursively()
            mkdirs()
        }
        try {
            val map = createLayer(root, OfflineLayerArtifact.MAP, "liverpool-map-2026.11.15", mapOf(
                "map/region.mbtiles" to "fixture-mbtiles".encodeToByteArray(),
                "map/glyphs/Noto Sans Regular/0-255.pbf" to byteArrayOf(1, 2, 3),
                "map/glyphs/Noto Sans Regular/256-511.pbf" to byteArrayOf(4, 5, 6),
            ))
            val routing = createLayer(root, OfflineLayerArtifact.ROUTING, "liverpool-routing-2026.11.15", mapOf(
                "routing/tiles.tar" to "fixture-valhalla-graph".encodeToByteArray(),
                "routing/admins.sqlite" to "admins".encodeToByteArray(),
                "routing/timezones.sqlite" to "timezones".encodeToByteArray(),
                "routing/valhalla.json" to "{}".encodeToByteArray(),
            ))
            val search = createLayer(root, OfflineLayerArtifact.SEARCH, "liverpool-search-2026.11.15", mapOf(
                "search/search-index.json" to """{"formatVersion":1,"records":[{"id":"postcode-1","label":"L1 1AA","latitude":53.4073,"longitude":-2.9775,"postcode":"L1 1AA","category":"postcode"}]}""".encodeToByteArray(),
            ))

            val installer: OfflineLayerPackageInstaller = createOfflineLayerPackageInstaller(root.absolutePath)
            listOf(map, routing, search).forEach { (archive, manifest) ->
                installer.install(archive.absolutePath, manifest, sha(manifest.encodeToByteArray())).getOrThrow()
            }

            val transfer = createOfflineLayerPackageTransfer(root.absolutePath, "")
            val installed = transfer.installed()
            assertEquals(3, installed.size)
            assertTrue(installed.all { it.state == OfflineLayerTransferState.INSTALLED })

            val combined = independentLayerMapPackage(installed, "liverpool-city-region")
            assertNotNull(combined)
            requireNotNull(combined)
            assertTrue(combined.mapDataPath.endsWith("map/region.mbtiles"))
            assertTrue(combined.routingDataPath.endsWith("routing/tiles.tar"))
            assertTrue(combined.searchDataPath?.endsWith("search/search-index.json") == true)

            val hits = transfer.searchInstalled("liverpool-city-region", "L1 1AA", 10)
            assertEquals("L1 1AA", hits.single().result.record.postcode)

            val style = createRegionalOfflineMapStyle(
                regionalMapDataFileUri(root.absolutePath, combined.mapDataPath),
                combined.attribution ?: error("Missing attribution"),
            )
            assertTrue(style.contains("mbtiles:"))
            assertTrue(style.contains("/glyphs/"))
            assertFalse(style.contains("https://"))

            transfer.remove("liverpool-city-region", OfflineLayerArtifact.MAP).getOrThrow()
            val remaining = transfer.installed()
            assertEquals(
                setOf(OfflineLayerArtifact.ROUTING, OfflineLayerArtifact.SEARCH),
                remaining.map { it.layer }.toSet(),
            )
            assertEquals(null, independentLayerMapPackage(remaining, "liverpool-city-region"))
        } finally {
            root.deleteRecursively()
        }
    }
}
