package uk.co.tomosmaps.offline

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** A single independently signed archive for one area and one functional layer. */
data class OfflineLayerPackageDescriptor(
    val id: String,
    val areaId: String,
    val areaName: String,
    val layer: OfflineLayerArtifact,
    val version: String,
    val manifestUrl: String,
    val manifestSha256: String,
    val archiveUrl: String,
    val archiveSha256: String,
    val archiveSizeBytes: Long,
    val installedSizeBytes: Long,
) {
    init {
        require(id.matches(Regex("[a-z0-9][a-z0-9._-]*")))
        require(areaId.matches(Regex("[a-z0-9][a-z0-9._-]*")))
        require(areaName.isNotBlank() && version.matches(Regex("[A-Za-z0-9][A-Za-z0-9._+-]*")))
        require(manifestUrl.isHttps() && archiveUrl.isHttps())
        require(manifestSha256.matches(Regex("[a-fA-F0-9]{64}")))
        require(archiveSha256.matches(Regex("[a-fA-F0-9]{64}")))
        require(archiveSizeBytes in 1..RegionalPackageDescriptor.MAX_PACKAGE_DOWNLOAD_BYTES)
        require(installedSizeBytes in 1..RegionalPackageDescriptor.MAX_PACKAGE_DOWNLOAD_BYTES)
    }
    private fun String.isHttps() = startsWith("https://") && length <= 2048 &&
        none { it.isWhitespace() || it.isISOControl() }
}

data class OfflineLayerFile(
    val path: String,
    val sizeBytes: Long,
    val sha256: String,
) {
    init {
        require(isSafePackagePath(path))
        require(path.substringBefore('/') != "manifest.json") { "manifest.json is reserved for the verified package manifest." }
        require(sizeBytes > 0)
        require(sha256.matches(Regex("[a-fA-F0-9]{64}")))
    }
}

data class OfflineLayerPackageManifest(
    val formatVersion: Int,
    val id: String,
    val areaId: String,
    val layer: OfflineLayerArtifact,
    val version: String,
    val totalSizeBytes: Long,
    val files: List<OfflineLayerFile>,
    val coverage: MapPackageCoverage,
    val attribution: String? = null,
) {
    init {
        require(formatVersion == CURRENT_FORMAT_VERSION)
        require(id.matches(Regex("[a-z0-9][a-z0-9._-]*")))
        require(areaId.matches(Regex("[a-z0-9][a-z0-9._-]*")))
        require(version.matches(Regex("[A-Za-z0-9][A-Za-z0-9._+-]*")))
        require(totalSizeBytes > 0 && totalSizeBytes <= RegionalPackageDescriptor.MAX_PACKAGE_DOWNLOAD_BYTES)
        require(files.isNotEmpty() && files.size <= 10_000)
        require(files.map { it.path }.distinct().size == files.size)
        require(files.sumOf { it.sizeBytes } == totalSizeBytes)
        require(attribution == null || attribution.isNotBlank())
    }
    companion object { const val CURRENT_FORMAT_VERSION = 1 }
}

fun decodeOfflineLayerPackageManifest(jsonText: String): OfflineLayerPackageManifest {
    require(jsonText.isNotBlank() && jsonText.encodeToByteArray().size <= 1_048_576) {
        "Layer manifest is empty or exceeds 1 MiB."
    }
    val root = Json.parseToJsonElement(jsonText).jsonObject
    val coverage = root["coverage"]?.jsonObject ?: error("Layer manifest has no coverage.")
    val files = root["files"]?.jsonArray ?: error("Layer manifest has no files.")
    require(files.size in 1..10_000)
    return OfflineLayerPackageManifest(
        formatVersion = root["formatVersion"]?.jsonPrimitive?.content?.toIntOrNull() ?: error("Invalid layer manifest format."),
        id = root["id"]?.jsonPrimitive?.content ?: error("Missing layer package id."),
        areaId = root["areaId"]?.jsonPrimitive?.content ?: error("Missing layer area id."),
        layer = root["layer"]?.jsonPrimitive?.content?.let { OfflineLayerArtifact.valueOf(it) } ?: error("Missing layer type."),
        version = root["version"]?.jsonPrimitive?.content ?: error("Missing layer version."),
        totalSizeBytes = root["totalSizeBytes"]?.jsonPrimitive?.longOrNull ?: error("Invalid layer size."),
        files = files.map { value ->
            val item = value.jsonObject
            OfflineLayerFile(
                path = item["path"]?.jsonPrimitive?.content ?: error("Missing layer file path."),
                sizeBytes = item["sizeBytes"]?.jsonPrimitive?.longOrNull ?: error("Invalid layer file size."),
                sha256 = item["sha256"]?.jsonPrimitive?.content ?: error("Missing layer file checksum."),
            )
        },
        coverage = MapPackageCoverage(
            west = coverage["west"]?.jsonPrimitive?.doubleOrNull ?: error("Invalid coverage west."),
            south = coverage["south"]?.jsonPrimitive?.doubleOrNull ?: error("Invalid coverage south."),
            east = coverage["east"]?.jsonPrimitive?.doubleOrNull ?: error("Invalid coverage east."),
            north = coverage["north"]?.jsonPrimitive?.doubleOrNull ?: error("Invalid coverage north."),
        ),
        attribution = root["attribution"]?.jsonPrimitive?.content,
    )
}

/** A separately verified and resumable layer transfer. */
data class OfflineLayerTransferRecord(
    val packageId: String,
    val areaId: String,
    val layer: OfflineLayerArtifact,
    val state: OfflineLayerTransferState,
    val bytesReceived: Long,
    val archiveSizeBytes: Long,
    val installedBytes: Long = 0,
    val message: String? = null,
    /** Relative to the app's offline storage root; populated only after verified installation. */
    val installedDirectory: String? = null,
    /** Relative file paths declared and checksum-verified by the installed manifest. */
    val installedFiles: List<String> = emptyList(),

) {
    init {
        require(packageId.isNotBlank() && areaId.isNotBlank())
        require(bytesReceived >= 0 && archiveSizeBytes > 0 && bytesReceived <= archiveSizeBytes)
        require(installedBytes >= 0)
        require(installedDirectory == null || isSafePackagePath(installedDirectory))
        require(installedFiles.all { isSafePackagePath(it) })
    }
    fun toProgress() = OfflineLayerTransferProgress(
        layer = layer,
        state = state,
        bytesReceived = bytesReceived,
        totalBytes = archiveSizeBytes,
        installedBytes = installedBytes,
        error = if (state == OfflineLayerTransferState.FAILED) message else null,
    )
}
