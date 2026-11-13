package uk.co.tomosmaps.navigation

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

sealed interface UkPostcodeLookupResult {
    data class Success(val postcode: String, val coordinate: RouteCoordinate) : UkPostcodeLookupResult
    data class Failure(val reason: String) : UkPostcodeLookupResult
}

/** UK postcode geocoding through the unauthenticated Postcodes.io API. */
class UkPostcodeLookupClient(
    private val httpClient: HttpClient = HttpClient(CIO),
    private val json: Json = Json { ignoreUnknownKeys = true },
) : AutoCloseable {

    suspend fun lookup(postcode: String): UkPostcodeLookupResult {
        val normalized = normalizeUkPostcode(postcode)
            ?: return UkPostcodeLookupResult.Failure("Enter a valid UK postcode, for example SW1A 1AA.")

        return try {
            val response = httpClient.get("https://api.postcodes.io/postcodes/$normalized")
            if (response.status.value != 200) {
                return UkPostcodeLookupResult.Failure(
                    if (response.status.value == 404) "That UK postcode could not be found."
                    else "Postcode lookup failed (HTTP ${response.status.value})."
                )
            }

            val root = json.parseToJsonElement(response.bodyAsText()).jsonObject
            val result = root["result"]?.jsonObject
                ?: return UkPostcodeLookupResult.Failure("The postcode service returned no result.")
            val latitude = result["latitude"]?.jsonPrimitive?.doubleOrNull
                ?: return UkPostcodeLookupResult.Failure("The postcode service returned no latitude.")
            val longitude = result["longitude"]?.jsonPrimitive?.doubleOrNull
                ?: return UkPostcodeLookupResult.Failure("The postcode service returned no longitude.")
            if (!latitude.isFinite() || !longitude.isFinite() ||
                latitude !in -90.0..90.0 || longitude !in -180.0..180.0
            ) {
                return UkPostcodeLookupResult.Failure("The postcode service returned invalid coordinates.")
            }

            val canonicalPostcode = result["postcode"]?.jsonPrimitive?.content ?: normalized
            UkPostcodeLookupResult.Success(
                postcode = canonicalPostcode,
                coordinate = RouteCoordinate(latitude, longitude),
            )
        } catch (exception: Exception) {
            UkPostcodeLookupResult.Failure(
                exception.message?.takeIf { it.isNotBlank() }
                    ?: "Unable to look up the postcode. Check your connection and try again."
            )
        }
    }

    override fun close() {
        httpClient.close()
    }
}

/**
 * Returns the API-safe compact postcode only for complete UK postcodes.
 * Spaces and case are normalised before the value is placed in a URL path.
 */
fun normalizeUkPostcode(postcode: String): String? {
    val compact = postcode.trim().uppercase().filterNot { it.isWhitespace() }
    if (compact == "GIR0AA") return compact
    return compact.takeIf {
        it.matches(Regex("[A-Z]{1,2}[0-9][A-Z0-9]?[0-9][A-Z]{2}"))
    }
}
