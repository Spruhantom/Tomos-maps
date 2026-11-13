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

actual fun createRegionalPackageArchiveInstaller(storageDirectory: String): RegionalPackageArchiveInstaller =
    JvmRegionalPackageArchiveInstaller(storageDirectory)

internal class JvmRegionalPackageArchiveInstaller(storageDirectory: String) : RegionalPackageArchiveInstaller {
    private val root = File(storageDirectory).canonicalFile
    private val packages = File(root, "packages")
    private val active = File(root, "active")
    private val staging = File(root, "staging")

    override suspend fun installedPackages(): List<MapPackage> = withContext(Dispatchers.IO) {
        if (!active.isDirectory) return@withContext emptyList()
        active.listFiles().orEmpty().filter { it.isFile && it.name.endsWith(".txt") }.mapNotNull { pointer ->
            runCatching {
                val id = pointer.name.removeSuffix(".txt")
                require(id.matches(Regex("[a-z0-9][a-z0-9._-]*")))
                val version = pointer.readText().trim()
                require(version.matches(Regex("[A-Za-z0-9][A-Za-z0-9._+-]*")))
                toPackage(File(packages, id + "/" + version))
            }.getOrNull()
        }.sortedBy { it.displayName.lowercase() }
    }

    override suspend fun install(archiveFile: String, manifestJson: String, expectedManifestSha256: String): Result<MapPackage> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(expectedManifestSha256.matches(Regex("[a-fA-F0-9]{64}"))) { "Invalid manifest SHA-256." }
                require(sha256(manifestJson.toByteArray(Charsets.UTF_8)).equals(expectedManifestSha256, true)) {
                    "Manifest checksum verification failed."
                }
                val manifest = decodeMapPackageManifest(manifestJson)
                val archive = File(archiveFile).canonicalFile
                require(archive.isFile && archive.length() in 1..(20L * 1024 * 1024 * 1024)) {
                    "Archive is missing, empty or exceeds 20 GiB."
                }
                root.mkdirs(); packages.mkdirs(); active.mkdirs(); staging.mkdirs()
                val stage = File(staging, manifest.id + "-" + UUID.randomUUID())
                val destination = File(packages, manifest.id + "/" + manifest.packageVersion)
                require(inside(root, stage) && inside(root, destination)) { "Unsafe package storage path." }
                require(!destination.exists()) { "This package version is already installed." }
                stage.mkdirs()
                try {
                    extract(archive, stage, manifest)
                    File(stage, "manifest.json").writeText(manifestJson, Charsets.UTF_8)
                    destination.parentFile.mkdirs()
                    Files.move(stage.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE)
                    val pointer = File(active, manifest.id + ".txt")
                    val temp = File(active, manifest.id + "-" + UUID.randomUUID() + ".tmp")
                    temp.writeText(manifest.packageVersion, Charsets.UTF_8)
                    try {
                        Files.move(temp.toPath(), pointer.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                    } catch (failure: Throwable) {
                        temp.delete()
                        destination.deleteRecursively()
                        throw failure
                    }
                    toPackage(destination)
                } catch (failure: Throwable) {
                    stage.deleteRecursively()
                    throw failure
                }
            }
        }

    override suspend fun remove(packageId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(packageId.matches(Regex("[a-z0-9][a-z0-9._-]*"))) { "Invalid package id." }
            val pointer = File(active, packageId + ".txt")
            val version = pointer.takeIf { it.isFile }?.readText()?.trim()
            pointer.delete()
            if (version != null && version.matches(Regex("[A-Za-z0-9][A-Za-z0-9._+-]*"))) {
                val dir = File(packages, packageId + "/" + version).canonicalFile
                require(inside(root, dir)) { "Unsafe installed package path." }
                dir.deleteRecursively()
            }
        }
    }

    private fun extract(archive: File, stage: File, manifest: MapPackageManifest) {
        val declared = manifest.files.associateBy { it.path }
        val seen = mutableSetOf<String>()
        var total = 0L
        ZipInputStream(FileInputStream(archive).buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name
                require(isSafePackagePath(name.trimEnd('/'))) { "Unsafe archive entry path." }
                if (entry.isDirectory) {
                    require(declared.keys.any { it.startsWith(name) }) { "Undeclared archive directory." }
                    zip.closeEntry()
                    continue
                }
                val expected = declared[name] ?: error("Archive contains undeclared file: " + name)
                require(seen.add(name)) { "Duplicate archive entry: " + name }
                val output = File(stage, name).canonicalFile
                require(inside(stage, output)) { "Archive entry escapes staging directory." }
                output.parentFile.mkdirs()
                val digest = MessageDigest.getInstance("SHA-256")
                var size = 0L
                FileOutputStream(output).buffered().use { sink ->
                    val buffer = ByteArray(65536)
                    while (true) {
                        val count = zip.read(buffer)
                        if (count < 0) break
                        size += count; total += count
                        require(size <= expected.sizeBytes && total <= manifest.totalSizeBytes) { "Archive expands beyond declared size." }
                        digest.update(buffer, 0, count); sink.write(buffer, 0, count)
                    }
                }
                require(size == expected.sizeBytes) { "Incorrect file size: " + name }
                require(hex(digest.digest()).equals(expected.sha256, true)) { "Checksum failed: " + name }
                zip.closeEntry()
            }
        }
        require(seen == declared.keys) { "Archive is missing declared files." }
        require(total == manifest.totalSizeBytes) { "Archive total size does not match manifest." }
    }

    private fun toPackage(directory: File): MapPackage {
        val manifestFile = File(directory, "manifest.json")
        require(manifestFile.isFile)
        val m = decodeMapPackageManifest(manifestFile.readText(Charsets.UTF_8))
        val prefix = "packages/" + m.id + "/" + m.packageVersion + "/"
        fun rolePath(role: MapPackageFileRole) = prefix + m.files.first { it.role == role }.path
        return MapPackage(
            id = m.id, displayName = m.displayName, version = m.packageVersion,
            mapDataPath = rolePath(MapPackageFileRole.MAP_DATA),
            routingDataPath = rolePath(MapPackageFileRole.ROUTING_GRAPH),
            searchDataPath = m.files.firstOrNull { it.role == MapPackageFileRole.SEARCH_INDEX }?.let { prefix + it.path },
            sizeBytes = m.totalSizeBytes, sha256 = sha256(manifestFile.readBytes()),
        )
    }

    private fun inside(parent: File, child: File): Boolean =
        child.canonicalFile.toPath().startsWith(parent.canonicalFile.toPath()) &&
            child.canonicalFile != parent.canonicalFile

    private fun sha256(bytes: ByteArray): String = hex(MessageDigest.getInstance("SHA-256").digest(bytes))
    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
}
