package uk.co.tomosmaps.search

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** A UK postcode result geocoded to WGS84 coordinates by Postcodes.io. */
data class UkPostcodeResult(
    val postcode: String,
    val latitude: Double,
    val longitude: Double,
    val country: String?,
    val region: String?,
    val adminDistrict: String?,
)

/**
 * Prefix postcode search using the public Postcodes.io API.
 *
 * Search is deliberately postcode-only: it does not pretend that a postcode
 * identifies an individual property or address.
 */
class UkPostcodeSearch(
    private val client: HttpClient,
    private val baseUrl: String = "https://api.postcodes.io",
) {
    suspend fun search(query: String, limit: Int = DEFAULT_LIMIT): Result<List<UkPostcodeResult>> {
        val normalized = query.trim().replace(Regex("\\s+"), " ").uppercase()
        if (normalized.length < 2) return Result.failure(
            IllegalArgumentException("Enter at least two characters of a UK postcode.")
        )
        if (normalized.length > 10 || !normalized.matches(Regex("[A-Z0-9 ]+"))) {
            return Result.failure(IllegalArgumentException("Enter a valid UK postcode prefix."))
        }
        require(limit in 1..20) { "Postcode result limit must be between 1 and 20." }

        return runCatching {
            val response = client.get("${baseUrl.trimEnd('/')}/postcodes") {
                parameter("q", normalized)
                parameter("limit", limit)
            }
            if (response.status.value !in 200..299) {
                error("Postcode search failed (HTTP ${response.status.value}).")
            }
            val root = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            val status = root["status"]?.jsonPrimitive?.content?.toIntOrNull()
            if (status != 200) error("Postcode service returned status ${status ?: "unknown"}.")
            root["result"]?.jsonArray.orEmpty().mapNotNull { item ->
                val fields = item.jsonObject
                val postcode = fields["postcode"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val latitude = fields["latitude"]?.jsonPrimitive?.content?.toDoubleOrNull()
                    ?: return@mapNotNull null
                val longitude = fields["longitude"]?.jsonPrimitive?.content?.toDoubleOrNull()
                    ?: return@mapNotNull null
                if (!latitude.isFinite() || latitude !in -90.0..90.0 ||
                    !longitude.isFinite() || longitude !in -180.0..180.0
                ) return@mapNotNull null
                UkPostcodeResult(
                    postcode = postcode,
                    latitude = latitude,
                    longitude = longitude,
                    country = fields["country"]?.jsonPrimitive?.content,
                    region = fields["region"]?.jsonPrimitive?.content,
                    adminDistrict = fields["admin_district"]?.jsonPrimitive?.content,
                )
            }
        }
    }

    companion object {
        const val DEFAULT_LIMIT = 8
    }
}
