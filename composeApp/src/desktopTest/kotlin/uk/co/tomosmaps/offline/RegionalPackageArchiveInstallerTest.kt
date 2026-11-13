package uk.co.tomosmaps.offline

import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RegionalPackageArchiveInstallerTest {
    @Test
    fun verifiesExtractsAndActivatesPackageAtomically() = runBlocking {
        val root = Files.createTempDirectory("tomos-package-test").toFile()
        val archive = File(root, "liverpool.zip")
        val map = "vector-map-data".toByteArray()
        val graph = "valhalla-graph".toByteArray()
        val manifest = manifest(map.size, graph.size, hash(map), hash(graph))
        writeZip(archive, map, graph)
        val installer = createRegionalPackageArchiveInstaller(File(root, "app-data").path)

        val installed = installer.install(archive.path, manifest, hash(manifest.toByteArray())).getOrThrow()

        assertEquals("gb-liverpool", installed.id)
        assertEquals(1, installer.installedPackages().size)
        assertTrue(File(root, "app-data/" + installed.mapDataPath).readBytes().contentEquals(map))
        assertTrue(File(root, "app-data/" + installed.routingDataPath).readBytes().contentEquals(graph))
        installer.remove("gb-liverpool").getOrThrow()
        assertTrue(installer.installedPackages().isEmpty())
        root.deleteRecursively()
    }

    @Test
    fun rejectsIncorrectManifestDigestWithoutActivatingPackage() = runBlocking {
        val root = Files.createTempDirectory("tomos-package-bad-digest").toFile()
        val archive = File(root, "liverpool.zip")
        val map = byteArrayOf(1, 2)
        val graph = byteArrayOf(3, 4)
        writeZip(archive, map, graph)
        val manifest = manifest(2, 2, hash(map), hash(graph))
        val installer = createRegionalPackageArchiveInstaller(File(root, "app-data").path)

        val result = installer.install(archive.path, manifest, "0".repeat(64))

        assertTrue(result.isFailure)
        assertTrue(installer.installedPackages().isEmpty())
        root.deleteRecursively()
    }

    @Test
    fun rejectsUndeclaredArchiveEntries() = runBlocking {
        val root = Files.createTempDirectory("tomos-package-extra-entry").toFile()
        val archive = File(root, "liverpool.zip")
        val map = byteArrayOf(1, 2)
        val graph = byteArrayOf(3, 4)
        writeZip(archive, map, graph, includeExtra = true)
        val manifest = manifest(2, 2, hash(map), hash(graph))
        val installer = createRegionalPackageArchiveInstaller(File(root, "app-data").path)

        val result = installer.install(archive.path, manifest, hash(manifest.toByteArray()))

        assertTrue(result.isFailure)
        assertTrue(installer.installedPackages().isEmpty())
        root.deleteRecursively()
    }

    private fun manifest(mapSize: Int, graphSize: Int, mapHash: String, graphHash: String): String =
        buildString {
            append("{\"formatVersion\":1,\"id\":\"gb-liverpool\",\"displayName\":\"Liverpool City Region\",")
            append("\"packageVersion\":\"1.0.0\",\"regionCode\":\"GB-LIV\",\"totalSizeBytes\":")
            append(mapSize + graphSize)
            append(",\"files\":[{\"path\":\"map/region.mbtiles\",\"role\":\"MAP_DATA\",\"sizeBytes\":")
            append(mapSize)
            append(",\"sha256\":\"").append(mapHash).append("\"},{\"path\":\"routing/valhalla.tar\",\"role\":\"ROUTING_GRAPH\",\"sizeBytes\":")
            append(graphSize)
            append(",\"sha256\":\"").append(graphHash).append("\"}]}")
        }

    private fun writeZip(file: File, map: ByteArray, graph: ByteArray, includeExtra: Boolean = false) {
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("map/region.mbtiles")); zip.write(map); zip.closeEntry()
            zip.putNextEntry(ZipEntry("routing/valhalla.tar")); zip.write(graph); zip.closeEntry()
            if (includeExtra) {
                zip.putNextEntry(ZipEntry("unexpected.txt")); zip.write("extra".toByteArray()); zip.closeEntry()
            }
        }
    }

    private fun hash(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
