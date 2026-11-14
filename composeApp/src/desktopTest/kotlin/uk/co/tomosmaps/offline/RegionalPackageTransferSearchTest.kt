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

class RegionalPackageTransferSearchTest {
    @Test
    fun searchesPostcodesFromInstalledPackageFiles() = runBlocking {
        val root = Files.createTempDirectory("tomos-regional-search-test").toFile()
        try {
            val assets = linkedMapOf(
                "map/region.mbtiles" to "map-data".encodeToByteArray(),
                "routing/tiles.tar" to "routing-data".encodeToByteArray(),
                "search/search-index.json" to """
                    {"formatVersion":1,"records":[
                      {"id":"l1-1aa","label":"Liverpool city centre","latitude":53.405,"longitude":-2.99,"postcode":"L1 1AA"}
                    ]}
                """.trimIndent().encodeToByteArray(),
            )
            fun digest(bytes: ByteArray): String =
                MessageDigest.getInstance("SHA-256").digest(bytes)
                    .joinToString("") { "%02x".format(it) }
            val totalSize = assets.values.sumOf { it.size.toLong() }
            val fileEntries = assets.entries.joinToString(",") { (name, bytes) ->
                val role = when {
                    name.startsWith("map/") -> "MAP_DATA"
                    name.startsWith("routing/") -> "ROUTING_GRAPH"
                    else -> "SEARCH_INDEX"
                }
                """{"path":"$name","role":"$role","sizeBytes":${bytes.size},"sha256":"${digest(bytes)}"}"""
            }
            val manifest = """
                {"formatVersion":1,"id":"liverpool","displayName":"Liverpool",
                 "packageVersion":"2026.11.1","regionCode":"UK-LIV","totalSizeBytes":$totalSize,
                 "coverage":{"west":-3.2,"south":53.2,"east":-2.6,"north":53.6},
                 "attribution":"© OpenStreetMap contributors","files":[$fileEntries]}
            """.trimIndent()
            val manifestBytes = manifest.encodeToByteArray()
            val archive = File(root, "liverpool.zip")
            ZipOutputStream(archive.outputStream().buffered()).use { zip ->
                assets.forEach { (name, bytes) ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }

            val installer = createRegionalPackageArchiveInstaller(root.absolutePath)
            installer.install(archive.absolutePath, manifest, digest(manifestBytes)).getOrThrow()
            val transfer = createRegionalPackageTransfer(root.absolutePath, "")
            val results = transfer.searchInstalledPackages("L1 1AA", 5)

            assertEquals(1, results.size)
            assertEquals("Liverpool city centre", results.single().result.record.label)
            assertEquals("liverpool", results.single().packageId)
            assertTrue(results.single().result.record.coordinate.latitude in 53.4..53.41)
        } finally {
            root.deleteRecursively()
        }
    }
}
