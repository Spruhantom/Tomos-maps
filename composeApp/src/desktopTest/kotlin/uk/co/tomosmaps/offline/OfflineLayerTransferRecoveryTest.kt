package uk.co.tomosmaps.offline

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OfflineLayerTransferRecoveryTest {
    private data class Response(val status: Int, val headers: Map<String, String>, val body: InputStream)

    private class FakeConnection(
        url: URL,
        private val respond: (FakeConnection) -> Response,
    ) : HttpURLConnection(url) {
        private val response by lazy { respond(this) }
        @Volatile var disconnected = false

        override fun connect() = Unit
        override fun disconnect() { disconnected = true }
        override fun usingProxy() = false
        override fun getResponseCode(): Int = response.status
        override fun getHeaderField(name: String): String? = response.headers[name]
        override fun getInputStream(): InputStream = response.body
    }

    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }

    private fun bytesResponse(bytes: ByteArray, status: Int = 200, headers: Map<String, String> = emptyMap()) =
        Response(status, headers + ("Content-Length" to bytes.size.toString()), ByteArrayInputStream(bytes))

    @Test
    fun pausedPartialArchiveIsRecoveredAndResumedAfterTransferObjectRestart() = runBlocking {
        val root = Files.createTempDirectory("tomos-layer-transfer-recovery").toFile()
        try {
            val searchIndex = """{"formatVersion":1,"records":[{"id":"p1","label":"L1 1AA","latitude":53.4,"longitude":-2.99,"postcode":"L1 1AA"}]}""".toByteArray(Charsets.UTF_8)
            val archiveOutput = ByteArrayOutputStream()
            ZipOutputStream(archiveOutput).use { zip ->
                zip.putNextEntry(ZipEntry("search/search-index.json"))
                zip.write(searchIndex)
                zip.closeEntry()
            }
            val archive = archiveOutput.toByteArray()
            val manifest = """
                {"formatVersion":1,"id":"liverpool-search-v1","areaId":"liverpool-city-region",
                 "layer":"SEARCH","version":"1.0.0","totalSizeBytes":${searchIndex.size},
                 "coverage":{"west":-3.35,"south":53.28,"east":-2.57,"north":53.71},
                 "attribution":"© OpenStreetMap contributors",
                 "files":[{"path":"search/search-index.json","sizeBytes":${searchIndex.size},"sha256":"${sha(searchIndex)}"}]}
            """.trimIndent().toByteArray(Charsets.UTF_8)
            val keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
            val manifestUrl = "https://offline.test/liverpool-search.manifest.json"
            val archiveUrl = "https://offline.test/liverpool-search.zip"
            val catalogueUrl = "https://offline.test/catalogue.json"
            val signatureUrl = "https://offline.test/catalogue.sig"
            val catalogue = """
                {"formatVersion":1,"packages":[{
                  "id":"liverpool-search-v1","areaId":"liverpool-city-region","areaName":"Liverpool City Region",
                  "layer":"SEARCH","version":"1.0.0","manifestUrl":"$manifestUrl",
                  "manifestSha256":"${sha(manifest)}","archiveUrl":"$archiveUrl",
                  "archiveSha256":"${sha(archive)}","archiveSizeBytes":${archive.size},
                  "installedSizeBytes":${searchIndex.size}
                }]}
            """.trimIndent().toByteArray(Charsets.UTF_8)
            val signer = Signature.getInstance("Ed25519")
            signer.initSign(keyPair.private)
            signer.update(catalogue)
            val signature = Base64.getEncoder().encode(signer.sign()).toByteArray(Charsets.UTF_8)
            val pinnedKey = Base64.getEncoder().encodeToString(keyPair.public.encoded)
            val firstArchiveChunk = CountDownLatch(1)
            val pauseFirstArchive = AtomicBoolean(true)

            val openConnection: (String) -> HttpURLConnection = { address ->
                FakeConnection(URL(address)) { connection ->
                    when (connection.url.path) {
                        "/catalogue.json" -> bytesResponse(catalogue)
                        "/catalogue.sig" -> bytesResponse(signature)
                        "/liverpool-search.manifest.json" -> bytesResponse(manifest)
                        "/liverpool-search.zip" -> {
                            val range = connection.getRequestProperty("Range")
                            if (range != null) {
                                val offset = range.removePrefix("bytes=").removeSuffix("-").toInt()
                                val remaining = archive.copyOfRange(offset, archive.size)
                                bytesResponse(
                                    remaining,
                                    status = HttpURLConnection.HTTP_PARTIAL,
                                    headers = mapOf("Content-Range" to "bytes $offset-${archive.size - 1}/${archive.size}"),
                                )
                            } else if (pauseFirstArchive.compareAndSet(true, false)) {
                                val body = object : InputStream() {
                                    private var sent = false
                                    override fun read(): Int {
                                        val one = ByteArray(1)
                                        val count = read(one, 0, 1)
                                        return if (count < 0) -1 else one[0].toInt() and 0xff
                                    }
                                    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                                        if (!sent) {
                                            val count = min(128, min(length, archive.size))
                                            archive.copyInto(buffer, offset, 0, count)
                                            sent = true
                                            firstArchiveChunk.countDown()
                                            return count
                                        }
                                        while (!connection.disconnected) Thread.sleep(5)
                                        throw IOException("Fake connection interrupted by pause")
                                    }
                                }
                                Response(200, mapOf("Content-Length" to archive.size.toString()), body)
                            } else {
                                bytesResponse(archive)
                            }
                        }
                        else -> error("Unexpected fake HTTPS URL: ${connection.url}")
                    }
                }
            }

            val transfer = JvmOfflineLayerPackageTransfer(root.absolutePath, pinnedKey, openConnection)
            assertTrue(transfer.refreshCatalogue(catalogueUrl, signatureUrl).isSuccess)

            val download = async(Dispatchers.IO) { transfer.download("liverpool-search-v1") }
            assertTrue(firstArchiveChunk.await(5, TimeUnit.SECONDS), "Archive transfer did not start.")
            transfer.pause("liverpool-search-v1")
            assertTrue(download.await().isFailure)
            val paused = transfer.transfers().single()
            assertEquals(OfflineLayerTransferState.PAUSED, paused.state)
            assertTrue(paused.bytesReceived > 0)

            val recovered = JvmOfflineLayerPackageTransfer(root.absolutePath, pinnedKey, openConnection)
            assertTrue(recovered.refreshCatalogue(catalogueUrl, signatureUrl).isSuccess)
            val recoveredPartial = recovered.transfers().single()
            assertEquals(OfflineLayerTransferState.PAUSED, recoveredPartial.state)
            assertTrue(recoveredPartial.bytesReceived > 0)

            val installed = recovered.resume("liverpool-search-v1").getOrThrow()
            assertEquals(OfflineLayerTransferState.INSTALLED, installed.state)
            assertEquals(archive.size.toLong(), installed.bytesReceived)
            val hits = recovered.searchInstalled("liverpool-city-region", "L1 1AA", 10)
            assertEquals("L1 1AA", hits.single().result.record.postcode)

            recovered.remove("liverpool-city-region", OfflineLayerArtifact.SEARCH).getOrThrow()
            assertTrue(recovered.installed().isEmpty())
        } finally {
            root.deleteRecursively()
        }
    }
}
