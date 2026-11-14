package uk.co.tomosmaps.offline

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

actual fun createOfflineLayerPackageTransfer(storageDirectory: String, pinnedEd25519PublicKeyBase64: String): OfflineLayerPackageTransfer =
    JvmOfflineLayerPackageTransfer(storageDirectory, pinnedEd25519PublicKeyBase64)

private class JvmOfflineLayerPackageTransfer(storageDirectory: String, private val pinnedKey: String) : OfflineLayerPackageTransfer {
    private val root = File(storageDirectory).canonicalFile
    private val downloads = File(root, "independent-downloads")
    private val installer = createOfflineLayerPackageInstaller(root.path)
    private val catalogue = ConcurrentHashMap<String, OfflineLayerPackageDescriptor>()
    private val progress = ConcurrentHashMap<String, OfflineLayerTransferRecord>()
    private val connections = ConcurrentHashMap<String, HttpURLConnection>()
    private val active = ConcurrentHashMap.newKeySet<String>()
    private val pauseRequested = ConcurrentHashMap.newKeySet<String>()

    override suspend fun refreshCatalogue(catalogueUrl: String, signatureUrl: String): Result<List<OfflineLayerPackageDescriptor>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val json = fetchHttps(catalogueUrl, SignedOfflineLayerCatalogue.MAX_BYTES).toString(Charsets.UTF_8)
                val signature = fetchHttps(signatureUrl, 16 * 1024).toString(Charsets.UTF_8).trim()
                val verified = SignedOfflineLayerCatalogue.verifyAndParse(json, signature, pinnedKey)
                catalogue.clear()
                verified.packages.forEach { catalogue[it.id] = it }
                verified.packages
            }
        }

    override suspend fun available(): List<OfflineLayerPackageDescriptor> =
        catalogue.values.sortedWith(compareBy<OfflineLayerPackageDescriptor> { it.areaName.lowercase() }.thenBy { it.layer.ordinal })

    override suspend fun installed(): List<OfflineLayerTransferRecord> = installer.installed()

    override suspend fun transfers(): List<OfflineLayerTransferRecord> {
        downloads.mkdirs()
        val partial = downloads.listFiles().orEmpty().filter { it.isFile && it.name.endsWith(".part") }.mapNotNull { file ->
            val id = file.name.removeSuffix(".part")
            val d = catalogue[id] ?: return@mapNotNull null
            val known = progress[id]
            if (known != null && known.state == OfflineLayerTransferState.DOWNLOADING) known
            else OfflineLayerTransferRecord(id, d.areaId, d.layer, OfflineLayerTransferState.PAUSED,
                file.length().coerceAtMost(d.archiveSizeBytes), d.archiveSizeBytes,
                message = "Interrupted layer download is ready to resume.")
        }
        return (progress.values.filter { it.state != OfflineLayerTransferState.INSTALLED } + partial)
            .distinctBy { it.packageId }.sortedBy { it.packageId }
    }

    override suspend fun download(packageId: String): Result<OfflineLayerTransferRecord> = withContext(Dispatchers.IO) {
        if (!packageId.matches(Regex("[a-z0-9][a-z0-9._-]*")))
            return@withContext Result.failure(IllegalArgumentException("Invalid layer package ID."))
        if (!active.add(packageId))
            return@withContext Result.failure(IllegalStateException("This layer is already downloading."))
        try {
            runCatching {
                val d = catalogue[packageId] ?: error("Layer is not in the verified publisher catalogue.")
                pauseRequested.remove(packageId)
                downloads.mkdirs()
                val part = File(downloads, "${packageId}.part")
                progress[packageId] = record(d, OfflineLayerTransferState.QUEUED, part.length())
                val manifestBytes = fetchHttps(d.manifestUrl, 1024 * 1024, packageId)
                require(sha256(manifestBytes).equals(d.manifestSha256, true)) { "Layer manifest checksum failed." }
                val manifestJson = manifestBytes.toString(Charsets.UTF_8)
                val manifest = decodeOfflineLayerPackageManifest(manifestJson)
                require(manifest.id == d.id && manifest.areaId == d.areaId && manifest.layer == d.layer &&
                    manifest.version == d.version && manifest.totalSizeBytes == d.installedSizeBytes) {
                    "Layer manifest identity or installed size does not match the signed catalogue."
                }
                if (packageId in pauseRequested) error("Layer download paused.")
                progress[packageId] = record(d, OfflineLayerTransferState.DOWNLOADING, part.length())
                stream(d, part)
                progress[packageId] = record(d, OfflineLayerTransferState.VERIFYING, d.archiveSizeBytes)
                require(part.length() == d.archiveSizeBytes) { "Layer archive length does not match signed catalogue." }
                require(sha256(part).equals(d.archiveSha256, true)) { part.delete(); "Layer archive checksum failed." }
                progress[packageId] = record(d, OfflineLayerTransferState.INSTALLING, d.archiveSizeBytes)
                val installed = installer.install(part.path, manifestJson, d.manifestSha256).getOrThrow()
                part.delete()
                installed.copy(bytesReceived = d.archiveSizeBytes, archiveSizeBytes = d.archiveSizeBytes,
                    installedBytes = d.installedSizeBytes).also { progress[packageId] = it }
            }.onFailure { failure ->
                val paused = pauseRequested.remove(packageId)
                catalogue[packageId]?.let { d ->
                    val received = File(downloads, "${packageId}.part").takeIf { it.isFile }?.length() ?: 0L
                    progress[packageId] = OfflineLayerTransferRecord(packageId, d.areaId, d.layer,
                        if (paused) OfflineLayerTransferState.PAUSED else OfflineLayerTransferState.FAILED,
                        received.coerceAtMost(d.archiveSizeBytes), d.archiveSizeBytes,
                        message = failure.message ?: "Layer download failed.")
                }
            }
        } finally {
            active.remove(packageId)
            connections.remove(packageId)?.disconnect()
        }
    }

    override suspend fun pause(packageId: String) = withContext(Dispatchers.IO) {
        require(active.contains(packageId)) { "No active layer download exists." }
        pauseRequested.add(packageId)
        connections[packageId]?.disconnect()
        while (active.contains(packageId)) delay(25)
        val d = catalogue[packageId] ?: error("Layer is not in the verified catalogue.")
        val received = File(downloads, "${packageId}.part").takeIf { it.isFile }?.length() ?: 0L
        progress[packageId] = record(d, OfflineLayerTransferState.PAUSED, received)
    }

    override suspend fun resume(packageId: String): Result<OfflineLayerTransferRecord> = download(packageId)

    override suspend fun remove(areaId: String, layer: OfflineLayerArtifact): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(areaId.matches(Regex("[a-z0-9][a-z0-9._-]*")))
            val matches = catalogue.values.filter { it.areaId == areaId && it.layer == layer }
            require(matches.none { active.contains(it.id) }) { "Pause the active layer download before removing it." }
            matches.forEach { d -> File(downloads, "${d.id}.part").delete(); progress.remove(d.id) }
            installer.remove(areaId, layer).getOrThrow()
        }
    }

    override suspend fun combinedSummary(areaId: String): CombinedOfflineTransferSummary {
        require(areaId.matches(Regex("[a-z0-9][a-z0-9._-]*")))
        val installedByLayer = installer.installed().filter { it.areaId == areaId }.associateBy { it.layer }
        val transfersByLayer = transfers().filter { it.areaId == areaId }.associateBy { it.layer }
        val descriptors = catalogue.values.filter { it.areaId == areaId }.associateBy { it.layer }
        return CombinedOfflineTransferSummary.from(OfflineLayerArtifact.values().map { layer ->
            val done = installedByLayer[layer]
            val ongoing = transfersByLayer[layer]
            val d = descriptors[layer]
            when {
                done != null -> done.toProgress()
                ongoing != null -> ongoing.toProgress()
                d != null -> OfflineLayerTransferProgress(layer, OfflineLayerTransferState.NOT_SELECTED,
                    totalBytes = d.archiveSizeBytes)
                else -> OfflineLayerTransferProgress(layer, OfflineLayerTransferState.NOT_SELECTED)
            }
        })
    }

    private fun record(d: OfflineLayerPackageDescriptor, state: OfflineLayerTransferState, received: Long) =
        OfflineLayerTransferRecord(d.id, d.areaId, d.layer, state,
            received.coerceAtMost(d.archiveSizeBytes), d.archiveSizeBytes)

    private suspend fun stream(d: OfflineLayerPackageDescriptor, part: File) {
        var offset = part.takeIf { it.isFile }?.length() ?: 0L
        if (offset > d.archiveSizeBytes) { part.delete(); offset = 0L }
        val c = (URL(d.archiveUrl).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false; connectTimeout = 20_000; readTimeout = 45_000; requestMethod = "GET"
            setRequestProperty("User-Agent", "TomOS-Maps/1.0 offline-layer")
            if (offset > 0) setRequestProperty("Range", "bytes=$offset-")
        }
        connections[d.id] = c
        try {
            val status = c.responseCode
            if (offset > 0 && status == HttpURLConnection.HTTP_OK) { part.delete(); offset = 0L }
            else if (offset > 0 && status == HttpURLConnection.HTTP_PARTIAL) {
                require(c.getHeaderField("Content-Range")?.startsWith("bytes $offset-") == true) {
                    "Invalid byte range returned for layer resume."
                }
            } else require(status == HttpURLConnection.HTTP_OK) { "Layer download returned HTTP $status; redirects are not accepted." }
            FileOutputStream(part, offset > 0).buffered().use { output ->
                c.inputStream.buffered().use { input ->
                    val buffer = ByteArray(64 * 1024); var received = offset
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        if (d.id in pauseRequested) error("Layer download paused.")
                        val count = input.read(buffer); if (count < 0) break
                        received += count
                        require(received <= d.archiveSizeBytes) { "Layer archive exceeds its signed size." }
                        output.write(buffer, 0, count)
                        progress[d.id] = record(d, OfflineLayerTransferState.DOWNLOADING, received)
                    }
                }
            }
        } finally { connections.remove(d.id); c.disconnect() }
    }

    private fun fetchHttps(url: String, maxBytes: Int, packageId: String? = null): ByteArray {
        require(url.startsWith("https://") && url.length <= 2048) { "Only HTTPS layer sources are allowed." }
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false; connectTimeout = 20_000; readTimeout = 30_000
            setRequestProperty("User-Agent", "TomOS-Maps/1.0 offline-layer")
        }
        if (packageId != null) {
            connections[packageId] = c
            if (packageId in pauseRequested) { connections.remove(packageId, c); c.disconnect(); error("Layer download paused.") }
        }
        try {
            require(c.responseCode == HttpURLConnection.HTTP_OK) { "Layer metadata returned HTTP ${c.responseCode}; redirects are not accepted." }
            require(c.getHeaderFieldLong("Content-Length", -1L) <= maxBytes) { "Layer metadata exceeds its size limit." }
            return c.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(16 * 1024)
                while (true) {
                    val count = input.read(buffer); if (count < 0) break
                    require(out.size().toLong() + count <= maxBytes) { "Layer metadata exceeds its size limit." }
                    out.write(buffer, 0, count)
                }
                out.toByteArray()
            }
        } finally { if (packageId != null) connections.remove(packageId, c); c.disconnect() }
    }

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it) }
    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
