package uk.co.tomosmaps.offline

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipInputStream

actual fun createOfflineLayerPackageInstaller(storageDirectory: String): OfflineLayerPackageInstaller =
    JvmOfflineLayerPackageInstaller(storageDirectory)

private class JvmOfflineLayerPackageInstaller(storageDirectory: String) : OfflineLayerPackageInstaller {
    private val root = File(storageDirectory).canonicalFile
    private val packageRoot = File(root, "independent-layers")
    private val pointerRoot = File(root, "independent-active")
    private val stagingRoot = File(root, "independent-staging")

    override suspend fun installed(): List<OfflineLayerTransferRecord> = withContext(Dispatchers.IO) {
        if (!pointerRoot.isDirectory) return@withContext emptyList()
        pointerRoot.walkTopDown().filter { it.isFile && it.name.endsWith(".txt") }.toList().mapNotNull { pointer ->
            runCatching {
                val areaId = pointer.parentFile.name
                require(areaId.matches(Regex("[a-z0-9][a-z0-9._-]*")))
                val layer = OfflineLayerArtifact.valueOf(pointer.name.removeSuffix(".txt").uppercase())
                val packageId = pointer.readText(Charsets.UTF_8).trim()
                require(packageId.matches(Regex("[a-z0-9][a-z0-9._-]*")))
                val directory = File(packageRoot, "$areaId/${layer.name.lowercase()}/$packageId").canonicalFile
                require(inside(root, directory))
                val manifestFile = File(directory, "manifest.json")
                val manifest = decodeOfflineLayerPackageManifest(manifestFile.readText(Charsets.UTF_8))
                OfflineLayerTransferRecord(
                    packageId, areaId, layer, OfflineLayerTransferState.INSTALLED,
                    manifest.totalSizeBytes, manifest.totalSizeBytes, manifest.totalSizeBytes,
                    installedDirectory = "independent-layers/$areaId/${layer.name.lowercase()}/$packageId",
                    installedFiles = manifest.files.map { it.path },
                )
            }.getOrNull()
        }.sortedWith(compareBy<OfflineLayerTransferRecord> { it.areaId }.thenBy { it.layer.ordinal })
    }

    override suspend fun install(
        archivePath: String,
        manifestJson: String,
        expectedManifestSha256: String,
    ): Result<OfflineLayerTransferRecord> = withContext(Dispatchers.IO) {
        runCatching {
            require(expectedManifestSha256.matches(Regex("[a-fA-F0-9]{64}")))
            require(sha256(manifestJson.toByteArray(Charsets.UTF_8)).equals(expectedManifestSha256, true)) {
                "Layer manifest checksum verification failed."
            }
            val manifest = decodeOfflineLayerPackageManifest(manifestJson)
            val archive = File(archivePath).canonicalFile
            require(archive.isFile && archive.length() in 1..RegionalPackageDescriptor.MAX_PACKAGE_DOWNLOAD_BYTES) {
                "Layer archive is missing, empty or exceeds 20 GiB."
            }
            root.mkdirs(); packageRoot.mkdirs(); pointerRoot.mkdirs(); stagingRoot.mkdirs()
            val stage = File(stagingRoot, manifest.id + "-" + UUID.randomUUID())
            val destination = File(packageRoot, "${manifest.areaId}/${manifest.layer.name.lowercase()}/${manifest.id}").canonicalFile
            require(inside(root, stage) && inside(root, destination))
            require(!destination.exists()) { "This layer package version is already installed." }
            stage.mkdirs()
            try {
                extract(archive, stage, manifest)
                File(stage, "manifest.json").writeText(manifestJson, Charsets.UTF_8)
                destination.parentFile.mkdirs()
                Files.move(stage.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE)
                val pointer = File(File(pointerRoot, manifest.areaId).apply { mkdirs() }, "${manifest.layer.name.lowercase()}.txt")
                val previousId = pointer.takeIf { it.isFile }?.readText(Charsets.UTF_8)?.trim()
                val temp = File(pointerRoot, pointer.name + "-" + UUID.randomUUID() + ".tmp")
                temp.writeText(manifest.id, Charsets.UTF_8)
                try {
                    Files.move(temp.toPath(), pointer.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                } catch (failure: Throwable) {
                    temp.delete(); destination.deleteRecursively(); throw failure
                }
                if (previousId != null && previousId != manifest.id &&
                    previousId.matches(Regex("[a-z0-9][a-z0-9._-]*"))) {
                    runCatching {
                        File(packageRoot, "${manifest.areaId}/${manifest.layer.name.lowercase()}/$previousId")
                            .canonicalFile.takeIf { inside(root, it) }?.deleteRecursively()
                    }
                }
                OfflineLayerTransferRecord(
                    manifest.id, manifest.areaId, manifest.layer,
                    OfflineLayerTransferState.INSTALLED, manifest.totalSizeBytes, manifest.totalSizeBytes,
                    manifest.totalSizeBytes,
                    installedDirectory = "independent-layers/${manifest.areaId}/${manifest.layer.name.lowercase()}/${manifest.id}",
                    installedFiles = manifest.files.map { it.path },
                )
            } catch (failure: Throwable) {
                stage.deleteRecursively(); throw failure
            }
        }
    }

    override suspend fun remove(areaId: String, layer: OfflineLayerArtifact): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(areaId.matches(Regex("[a-z0-9][a-z0-9._-]*")))
            val pointer = File(File(pointerRoot, areaId), "${layer.name.lowercase()}.txt")
            val packageId = pointer.takeIf { it.isFile }?.readText(Charsets.UTF_8)?.trim()
            pointer.delete()
            if (packageId != null) {
                require(packageId.matches(Regex("[a-z0-9][a-z0-9._-]*")))
                val directory = File(packageRoot, "$areaId/${layer.name.lowercase()}/$packageId").canonicalFile
                require(inside(root, directory))
                require(!directory.exists() || directory.deleteRecursively()) { "Unable to remove installed layer files." }
            }
            Unit
        }
    }

    private fun extract(archive: File, stage: File, manifest: OfflineLayerPackageManifest) {
        val declared = manifest.files.associateBy { it.path }
        val seen = mutableSetOf<String>()
        var total = 0L
        ZipInputStream(FileInputStream(archive).buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name
                require(isSafePackagePath(name.trimEnd('/'))) { "Unsafe layer archive path." }
                if (entry.isDirectory) {
                    require(declared.keys.any { it.startsWith(name) }) { "Undeclared archive directory." }
                    zip.closeEntry(); continue
                }
                val expected = declared[name] ?: error("Undeclared layer archive file: $name")
                require(seen.add(name)) { "Duplicate layer archive entry: $name" }
                val output = File(stage, name).canonicalFile
                require(inside(stage, output)) { "Layer archive path escapes staging." }
                output.parentFile.mkdirs()
                val digest = MessageDigest.getInstance("SHA-256")
                var size = 0L
                FileOutputStream(output).buffered().use { sink ->
                    val buffer = ByteArray(65536)
                    while (true) {
                        val count = zip.read(buffer)
                        if (count < 0) break
                        size += count; total += count
                        require(size <= expected.sizeBytes && total <= manifest.totalSizeBytes) {
                            "Layer archive expands beyond declared size."
                        }
                        digest.update(buffer, 0, count); sink.write(buffer, 0, count)
                    }
                }
                require(size == expected.sizeBytes) { "Layer file size mismatch: $name" }
                require(hex(digest.digest()).equals(expected.sha256, true)) { "Layer file checksum failed: $name" }
                zip.closeEntry()
            }
        }
        require(seen == declared.keys) { "Layer archive is missing declared files." }
        require(total == manifest.totalSizeBytes) { "Layer archive total size mismatch." }
    }

    private fun inside(parent: File, child: File): Boolean =
        child.canonicalFile.toPath().startsWith(parent.canonicalFile.toPath()) &&
            child.canonicalFile != parent.canonicalFile

    private fun sha256(bytes: ByteArray) = hex(MessageDigest.getInstance("SHA-256").digest(bytes))
    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }
}
