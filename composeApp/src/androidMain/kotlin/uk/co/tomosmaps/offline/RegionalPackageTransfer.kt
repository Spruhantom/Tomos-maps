package uk.co.tomosmaps.offline

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

actual fun createRegionalPackageTransfer(
    storageDirectory: String,
    pinnedEd25519PublicKeyBase64: String,
): RegionalPackageTransfer = JvmRegionalPackageTransfer(storageDirectory, pinnedEd25519PublicKeyBase64)

private class JvmRegionalPackageTransfer(
    storageDirectory: String,
    private val pinnedKey: String,
) : RegionalPackageTransfer {
    private val root = File(storageDirectory).canonicalFile
    private val downloads = File(root, "downloads")
    private val installer = createRegionalPackageArchiveInstaller(root.path)
    private val catalogue = ConcurrentHashMap<String, RegionalPackageDescriptor>()
    private val progress = ConcurrentHashMap<String, PackageTransferProgress>()
    private val connections = ConcurrentHashMap<String, HttpURLConnection>()
    private val pauseRequested = ConcurrentHashMap.newKeySet<String>()

    override suspend fun refreshCatalogue(
        catalogueUrl: String,
        signatureUrl: String,
    ): Result<List<RegionalPackageDescriptor>> = withContext(Dispatchers.IO) {
        runCatching {
            val json = fetchHttps(catalogueUrl, SignedRegionalPackageCatalogue.MAX_CATALOGUE_BYTES)
                .toString(Charsets.UTF_8)
            val signature = fetchHttps(signatureUrl, 16 * 1024).toString(Charsets.UTF_8).trim()
            val verified = SignedRegionalPackageCatalogue.verifyAndParse(json, signature, pinnedKey)
            catalogue.clear()
            verified.packages.forEach { catalogue[it.id] = it }
            verified.packages
        }
    }

    override suspend fun availablePackages(): List<RegionalPackageDescriptor> =
        catalogue.values.sortedBy { it.name.lowercase() }

    override suspend fun installedPackages(): List<MapPackage> = installer.installedPackages()

    override suspend fun transfers(): List<PackageTransferProgress> {
        downloads.mkdirs()
        val persisted = downloads.listFiles().orEmpty()
            .filter { it.isFile && it.name.endsWith(".part") }
            .mapNotNull { file ->
                val id = file.name.removeSuffix(".part")
                val descriptor = catalogue[id]
                val known = progress[id]
                if (known != null && known.status == PackageTransferStatus.DOWNLOADING) known
                else PackageTransferProgress(
                    packageId = id,
                    status = PackageTransferStatus.PAUSED,
                    bytesReceived = file.length(),
                    totalBytes = descriptor?.estimatedSizeBytes ?: 0L,
                    message = "Interrupted download is ready to resume.",
                )
            }
        return (progress.values.filter { it.status != PackageTransferStatus.COMPLETE } + persisted)
            .distinctBy { it.packageId }
            .sortedBy { it.packageId }
    }

    override suspend fun searchInstalledPackages(
        query: String,
        limit: Int,
    ): List<RegionalPackageSearchHit> = withContext(Dispatchers.IO) {
        require(limit in 1..OfflineSearchIndex.MAX_LIMIT)
        if (query.isBlank()) return@withContext emptyList()
        installer.installedPackages().flatMap { installed ->
            val relativePath = installed.searchDataPath ?: return@flatMap emptyList()
            val indexFile = File(root, relativePath).canonicalFile
            require(indexFile.toPath().startsWith(root.toPath()) && indexFile != root) {
                "Installed search index path escapes regional package storage."
            }
            if (!indexFile.isFile || indexFile.length() > OfflineSearchIndex.MAX_INDEX_JSON_CHARS) {
                return@flatMap emptyList()
            }
            val index = OfflineSearchIndex.parse(indexFile.readText(Charsets.UTF_8))
            index.search(query, limit).map { match ->
                RegionalPackageSearchHit(installed.id, installed.displayName, match)
            }
        }.sortedWith(
            compareBy<RegionalPackageSearchHit> { it.result.matchedBy.ordinal }
                .thenBy { it.result.record.label.lowercase() }
                .thenBy { it.packageId }
        ).take(limit)
    }

    override suspend fun download(packageId: String): Result<MapPackage> = withContext(Dispatchers.IO) {
        runCatching {
            val descriptor = catalogue[packageId] ?: error("This package is not in the verified publisher catalogue.")
            require(!connections.containsKey(packageId)) { "This package is already downloading." }
            pauseRequested.remove(packageId)
            downloads.mkdirs()
            val part = File(downloads, "$packageId.part")
            val manifestBytes = fetchHttps(descriptor.manifestUrl, 1024 * 1024)
            require(sha256(manifestBytes).equals(descriptor.manifestSha256, true)) {
                "Regional package manifest checksum failed."
            }
            val manifestJson = manifestBytes.toString(Charsets.UTF_8)
            val manifest = decodeMapPackageManifest(manifestJson)
            require(manifest.id == descriptor.id && manifest.packageVersion == descriptor.version) {
                "Manifest identity does not match the signed catalogue entry."
            }
            progress[packageId] = PackageTransferProgress(
                packageId, PackageTransferStatus.DOWNLOADING, part.length(), descriptor.estimatedSizeBytes
            )
            streamArchive(descriptor, part)
            progress[packageId] = PackageTransferProgress(
                packageId, PackageTransferStatus.VERIFYING, part.length(), part.length()
            )
            require(sha256(part).equals(descriptor.archiveSha256, true)) {
                part.delete()
                "Regional package archive checksum failed."
            }
            progress[packageId] = PackageTransferProgress(
                packageId, PackageTransferStatus.INSTALLING, part.length(), part.length()
            )
            val installed = installer.install(part.path, manifestJson, descriptor.manifestSha256).getOrThrow()
            part.delete()
            progress[packageId] = PackageTransferProgress(
                packageId, PackageTransferStatus.COMPLETE, part.length(), part.length()
            )
            installed
        }.onFailure { failure ->
            val paused = pauseRequested.remove(packageId)
            val part = File(downloads, "$packageId.part")
            progress[packageId] = PackageTransferProgress(
                packageId = packageId,
                status = if (paused) PackageTransferStatus.PAUSED else PackageTransferStatus.FAILED,
                bytesReceived = part.takeIf { it.exists() }?.length() ?: 0L,
                totalBytes = maxOf(part.takeIf { it.exists() }?.length() ?: 0L, catalogue[packageId]?.estimatedSizeBytes ?: 0L),
                message = failure.message ?: "Regional package download failed.",
            )
        }
    }

    override suspend fun pause(packageId: String) {
        pauseRequested.add(packageId)
        connections[packageId]?.disconnect()
        val part = File(downloads, "$packageId.part")
        progress[packageId] = PackageTransferProgress(
            packageId, PackageTransferStatus.PAUSED, part.takeIf { it.exists() }?.length() ?: 0L,
            maxOf(part.takeIf { it.exists() }?.length() ?: 0L, catalogue[packageId]?.estimatedSizeBytes ?: 0L)
        )
    }

    override suspend fun resume(packageId: String): Result<MapPackage> = download(packageId)

    override suspend fun delete(packageId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(packageId.matches(Regex("[a-z0-9][a-z0-9._-]*"))) { "Invalid package id." }
            require(!connections.containsKey(packageId)) { "Pause the active download before deleting it." }
            File(downloads, "$packageId.part").delete()
            File(downloads, "$packageId.part.tmp").delete()
            installer.remove(packageId).getOrThrow()
            progress.remove(packageId)
            Unit
        }
    }

    private suspend fun streamArchive(descriptor: RegionalPackageDescriptor, part: File) {
        var offset = part.takeIf { it.isFile }?.length() ?: 0L
        if (offset > descriptor.estimatedSizeBytes) {
            part.delete()
            offset = 0L
        }
        val connection = (URL(descriptor.archiveUrl).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            connectTimeout = 20_000
            readTimeout = 45_000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "TomOS-Maps/1.0 offline-package")
            if (offset > 0L) setRequestProperty("Range", "bytes=$offset-")
        }
        connections[descriptor.id] = connection
        try {
            val status = connection.responseCode
            if (offset > 0L && status == HttpURLConnection.HTTP_OK) {
                // Server ignored Range: restart cleanly rather than append a second copy.
                part.delete()
                offset = 0L
            } else if (offset > 0L && status == HttpURLConnection.HTTP_PARTIAL) {
                require(connection.getHeaderField("Content-Range")?.startsWith("bytes $offset-") == true) {
                    "Server returned an invalid Content-Range for resume."
                }
            } else {
                require(status == HttpURLConnection.HTTP_OK) {
                    "Package download returned HTTP $status; redirects and partial responses are not accepted."
                }
            }
            FileOutputStream(part, offset > 0L).buffered().use { output ->
                connection.inputStream.buffered().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    var received = offset
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        if (descriptor.id in pauseRequested) error("Download paused.")
                        val count = input.read(buffer)
                        if (count < 0) break
                        received += count
                        require(received <= RegionalPackageDescriptor.MAX_PACKAGE_DOWNLOAD_BYTES) {
                            "Package download exceeds the configured 20 GiB limit."
                        }
                        output.write(buffer, 0, count)
                        progress[descriptor.id] = PackageTransferProgress(
                            descriptor.id, PackageTransferStatus.DOWNLOADING, received,
                            maxOf(received, descriptor.estimatedSizeBytes)
                        )
                    }
                }
            }
        } finally {
            connections.remove(descriptor.id)
            connection.disconnect()
        }
    }

    private fun fetchHttps(url: String, maxBytes: Int): ByteArray {
        require(url.startsWith("https://") && url.length <= 2048) { "Only HTTPS package sources are allowed." }
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            connectTimeout = 20_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", "TomOS-Maps/1.0 offline-package")
        }
        try {
            require(connection.responseCode == HttpURLConnection.HTTP_OK) {
                "Package source returned HTTP ${connection.responseCode}; redirects are not accepted."
            }
            val declaredLength = connection.getHeaderFieldLong("Content-Length", -1L)
            require(declaredLength <= maxBytes) { "Package metadata exceeds its size limit." }
            return connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(output.size().toLong() + count <= maxBytes) { "Package metadata exceeds its size limit." }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
