package uk.co.tomosmaps.offline

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class SignedOfflineLayerCatalogue(
    val formatVersion: Int,
    val packages: List<OfflineLayerPackageDescriptor>,
) {
    companion object {
        const val FORMAT_VERSION = 1
        const val MAX_BYTES = 4 * 1024 * 1024

        suspend fun verifyAndParse(
            json: String,
            signatureBase64: String,
            pinnedPublicKeyBase64: String,
        ): SignedOfflineLayerCatalogue {
            require(json.isNotBlank() && json.encodeToByteArray().size <= MAX_BYTES) {
                "Layer catalogue is empty or exceeds 4 MiB."
            }
            require(verifyRegionalCatalogueSignature(json.encodeToByteArray(), signatureBase64, pinnedPublicKeyBase64)) {
                "Offline layer catalogue signature is invalid."
            }
            val root = Json.parseToJsonElement(json).jsonObject
            val version = root["formatVersion"]?.jsonPrimitive?.content?.toIntOrNull()
            require(version == FORMAT_VERSION) { "Unsupported offline layer catalogue format." }
            val entries = root["packages"]?.jsonArray ?: error("Layer catalogue has no packages.")
            require(entries.size in 1..2_000)
            fun required(item: kotlinx.serialization.json.JsonObject, key: String) =
                item[key]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                    ?: error("Layer catalogue field '$key' is missing.")
            val packages = entries.map { value ->
                val item = value.jsonObject
                OfflineLayerPackageDescriptor(
                    id = required(item, "id"),
                    areaId = required(item, "areaId"),
                    areaName = required(item, "areaName"),
                    layer = OfflineLayerArtifact.valueOf(required(item, "layer")),
                    version = required(item, "version"),
                    manifestUrl = required(item, "manifestUrl"),
                    manifestSha256 = required(item, "manifestSha256"),
                    archiveUrl = required(item, "archiveUrl"),
                    archiveSha256 = required(item, "archiveSha256"),
                    archiveSizeBytes = required(item, "archiveSizeBytes").toLong(),
                    installedSizeBytes = required(item, "installedSizeBytes").toLong(),
                )
            }
            require(packages.map { it.id }.distinct().size == packages.size) { "Duplicate layer package IDs." }
            require(packages.map { it.areaId to it.layer }.distinct().size == packages.size) {
                "Catalogue contains more than one active version for an area/layer pair."
            }
            return SignedOfflineLayerCatalogue(version!!, packages)
        }
    }
}
