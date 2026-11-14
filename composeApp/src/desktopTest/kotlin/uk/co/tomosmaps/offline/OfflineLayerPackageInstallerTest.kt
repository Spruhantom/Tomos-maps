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
import kotlin.test.assertTrue

class OfflineLayerPackageInstallerTest {
    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it) }

    private fun fixture(root: File, layer: OfflineLayerArtifact, id: String, fileName: String): Pair<File, String> {
        val bytes = "verified-$id".encodeToByteArray()
        val manifest = """
            {"formatVersion":1,"id":"$id","areaId":"liverpool-city","layer":"$layer",
             "version":"1.0.0","totalSizeBytes":${bytes.size},
             "coverage":{"west":-3.2,"south":53.2,"east":-2.6,"north":53.6},
             "attribution":"© OpenStreetMap contributors",
             "files":[{"path":"$fileName","sizeBytes":${bytes.size},"sha256":"${sha(bytes)}"}]}
        """.trimIndent()
        val archive = File(root, "$id.zip")
        ZipOutputStream(archive.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry(fileName)); zip.write(bytes); zip.closeEntry()
        }
        return archive to manifest
    }

    @Test
    fun installsAndRemovesEachLayerWithoutTouchingOtherLayers() = runBlocking {
        val root = Files.createTempDirectory("tomos-independent-layers").toFile()
        try {
            val installer = createOfflineLayerPackageInstaller(root.absolutePath)
            val map = fixture(root, OfflineLayerArtifact.MAP, "liverpool-map-v1", "map.mbtiles")
            val roads = fixture(root, OfflineLayerArtifact.ROUTING, "liverpool-roads-v1", "routing/tiles.tar")
            val search = fixture(root, OfflineLayerArtifact.SEARCH, "liverpool-search-v1", "search/search-index.json")
            suspend fun install(pair: Pair<File, String>) {
                installer.install(pair.first.absolutePath, pair.second, sha(pair.second.encodeToByteArray())).getOrThrow()
            }
            install(map); install(roads); install(search)
            val installed = installer.installed()
            assertEquals(3, installed.size)
            assertTrue(installed.all { it.state == OfflineLayerTransferState.INSTALLED })
            assertEquals(setOf(OfflineLayerArtifact.MAP, OfflineLayerArtifact.ROUTING, OfflineLayerArtifact.SEARCH),
                installed.map { it.layer }.toSet())
            installer.remove("liverpool-city", OfflineLayerArtifact.ROUTING).getOrThrow()
            val afterRemoval = installer.installed()
            assertEquals(setOf(OfflineLayerArtifact.MAP, OfflineLayerArtifact.SEARCH), afterRemoval.map { it.layer }.toSet())
            assertFalse(File(root, "independent-layers/liverpool-city/routing/liverpool-roads-v1").exists())
        } finally { root.deleteRecursively() }
    }

    @Test
    fun rejectsTamperedLayerFileAndDoesNotActivateIt() = runBlocking {
        val root = Files.createTempDirectory("tomos-layer-tamper").toFile()
        try {
            val fixture = fixture(root, OfflineLayerArtifact.SEARCH, "liverpool-search-tamper", "search/index.json")
            ZipOutputStream(fixture.first.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry("search/index.json")); zip.write("tampered".encodeToByteArray()); zip.closeEntry()
            }
            val installer = createOfflineLayerPackageInstaller(root.absolutePath)
            val result = installer.install(fixture.first.absolutePath, fixture.second, sha(fixture.second.encodeToByteArray()))
            assertTrue(result.isFailure)
            assertTrue(installer.installed().isEmpty())
        } finally { root.deleteRecursively() }
    }
}
