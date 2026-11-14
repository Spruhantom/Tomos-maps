package uk.co.tomosmaps.offline

import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals

class OfflineLayerSearchTest {
    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it) }

    @Test
    fun searchesVerifiedIndependentPostcodeIndexWithoutCatalogueOrNetwork() = runBlocking {
        val root = Files.createTempDirectory("tomos-independent-search").toFile()
        try {
            val index = """{"formatVersion":1,"records":[{"id":"l1-1aa","label":"Liverpool city centre","latitude":53.405,"longitude":-2.99,"postcode":"L1 1AA","category":"postcode"}]}"""
                .encodeToByteArray()
            val fileName = "search/search-index.json"
            val manifest = """{"formatVersion":1,"id":"liverpool-search-v1","areaId":"liverpool-city","layer":"SEARCH","version":"1.0.0","totalSizeBytes":${index.size},"coverage":{"west":-3.2,"south":53.2,"east":-2.6,"north":53.6},"files":[{"path":"$fileName","sizeBytes":${index.size},"sha256":"${sha(index)}"}]}"""
            val archive = File(root, "search.zip")
            ZipOutputStream(archive.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry(fileName)); zip.write(index); zip.closeEntry()
            }
            val installer = createOfflineLayerPackageInstaller(root.absolutePath)
            installer.install(archive.absolutePath, manifest, sha(manifest.encodeToByteArray())).getOrThrow()

            // Empty publisher key and no catalogue refresh: lookup is local file I/O only.
            val transfer = createOfflineLayerPackageTransfer(root.absolutePath, "")
            val hits = transfer.searchInstalled("liverpool-city", "L1 1AA", 5)
            assertEquals(1, hits.size)
            assertEquals("L1 1AA", hits.single().result.record.postcode)
            assertEquals(53.405, hits.single().result.record.coordinate.latitude)
        } finally { root.deleteRecursively() }
    }
}
