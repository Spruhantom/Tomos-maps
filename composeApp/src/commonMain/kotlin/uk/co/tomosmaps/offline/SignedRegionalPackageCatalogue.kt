package uk.co.tomosmaps.offline

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * A signed, versioned catalogue. The signature covers the exact UTF-8 bytes of
 * the catalogue document; callers must supply a publisher key pinned by the app.
 */
data class SignedRegionalPackageCatalogue(
    val formatVersion: Int,
    val packages: List<RegionalPackageDescriptor>,
) {
    companion object {
        const val FORMAT_VERSION = 1
        const val MAX_CATALOGUE_BYTES = 4 * 1024 * 1024

        suspend fun verifyAndParse(
            catalogueJson: String,
            signatureBase64: String,
            pinnedEd25519PublicKeyBase64: String,
        ): SignedRegionalPackageCatalogue {
            require(catalogueJson.isNotBlank() && catalogueJson.encodeToByteArray().size <= MAX_CATALOGUE_BYTES) {
                "Catalogue is empty or exceeds 4 MiB."
            }
            require(verifyRegionalCatalogueSignature(
                catalogueJson.encodeToByteArray(), signatureBase64, pinnedEd25519PublicKeyBase64
            )) { "Regional package catalogue signature is invalid." }

            val root = Json.parseToJsonElement(catalogueJson).jsonObject
            val version = root["formatVersion"]?.jsonPrimitive?.content?.toIntOrNull()
            require(version == FORMAT_VERSION) { "Unsupported regional catalogue format." }
            val entries = root["packages"]?.jsonArray
                ?: throw IllegalArgumentException("Catalogue has no packages.")
            require(entries.size in 1..500) { "Catalogue package count is invalid." }
            val packages = entries.map { value ->
                val item = value.jsonObject
                RegionalPackageDescriptor(
                    id = item.requiredString("id"),
                    name = item.requiredString("name"),
                    version = item.requiredString("version"),
                    manifestUrl = item.requiredString("manifestUrl"),
                    manifestSha256 = item.requiredString("manifestSha256"),
                    estimatedSizeBytes = item.requiredLong("estimatedSizeBytes"),
                    archiveUrl = item.requiredString("archiveUrl"),
                    archiveSha256 = item.requiredString("archiveSha256"),
                )
            }
            require(packages.map { it.id }.distinct().size == packages.size) {
                "Catalogue contains duplicate package IDs."
            }
            return SignedRegionalPackageCatalogue(version!!, packages)
        }

        private fun kotlinx.serialization.json.JsonObject.requiredString(key: String): String =
            this[key]?.jsonPrimitive?.content?.also { require(it.isNotBlank()) }
                ?: throw IllegalArgumentException("Catalogue field '$key' is missing.")

        private fun kotlinx.serialization.json.JsonObject.requiredLong(key: String): Long =
            this[key]?.jsonPrimitive?.content?.toLongOrNull()?.takeIf { it > 0 }
                ?: throw IllegalArgumentException("Catalogue field '$key' is invalid.")
    }
}

/** Platform implementations use the JCA Ed25519 verifier on Android and JVM. */
expect suspend fun verifyRegionalCatalogueSignature(
    payload: ByteArray,
    signatureBase64: String,
    pinnedEd25519PublicKeyBase64: String,
): Boolean
