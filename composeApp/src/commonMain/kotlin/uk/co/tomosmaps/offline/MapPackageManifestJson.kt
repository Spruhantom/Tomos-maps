package uk.co.tomosmaps.offline

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

fun decodeMapPackageManifest(jsonText: String): MapPackageManifest {
    require(jsonText.isNotBlank() && jsonText.length <= 1_048_576) { "Manifest is empty or exceeds 1 MiB." }
    val root = Json.parseToJsonElement(jsonText).jsonObject
    val entries = root["files"]?.jsonArray ?: error("Manifest has no files array.")
    require(entries.size in 1..10_000) { "Manifest file count is invalid." }
    val manifest = MapPackageManifest(
        formatVersion = root["formatVersion"]?.jsonPrimitive?.intOrNull ?: error("Invalid formatVersion."),
        id = root["id"]?.jsonPrimitive?.content ?: error("Missing package id."),
        displayName = root["displayName"]?.jsonPrimitive?.content ?: error("Missing displayName."),
        packageVersion = root["packageVersion"]?.jsonPrimitive?.content ?: error("Missing packageVersion."),
        regionCode = root["regionCode"]?.jsonPrimitive?.content ?: error("Missing regionCode."),
        totalSizeBytes = root["totalSizeBytes"]?.jsonPrimitive?.longOrNull ?: error("Invalid totalSizeBytes."),
        files = entries.map { value ->
            val item = value.jsonObject
            val role = item["role"]?.jsonPrimitive?.content ?: error("Missing file role.")
            MapPackageFile(
                path = item["path"]?.jsonPrimitive?.content ?: error("Missing file path."),
                role = runCatching { MapPackageFileRole.valueOf(role) }.getOrElse { error("Unknown file role.") },
                sizeBytes = item["sizeBytes"]?.jsonPrimitive?.longOrNull ?: error("Invalid file size."),
                sha256 = item["sha256"]?.jsonPrimitive?.content ?: error("Missing file checksum."),
            )
        },
    )
    require(manifest.totalSizeBytes <= 20L * 1024 * 1024 * 1024) { "Unpacked package exceeds 20 GiB." }
    require(manifest.files.all { it.sizeBytes <= 10L * 1024 * 1024 * 1024 }) { "A package file exceeds 10 GiB." }
    return manifest
}
