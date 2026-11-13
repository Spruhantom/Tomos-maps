package uk.co.tomosmaps.navigation

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.accept
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Makes a real POST request to a user-configured Valhalla deployment.
 *
 * The client deliberately has no default host: callers must supply their own
 * trusted HTTPS endpoint (localhost HTTP is permitted for development).
 */
class ValhallaRoutingClient(
    private val httpClient: HttpClient = HttpClient(CIO),
    private val json: Json = Json { ignoreUnknownKeys = true },
) : AutoCloseable {
    suspend fun calculateRoute(
        baseUrl: String,
        request: ValhallaRouteRequest,
    ): ValhallaRouteResult {
        val routeUrl = try {
            ValhallaRouteRequestBuilder.routeUrl(baseUrl)
        } catch (exception: IllegalArgumentException) {
            return ValhallaRouteResult.Failure(exception.message ?: "Invalid Valhalla endpoint.")
        }

        return try {
            val response = httpClient.post(routeUrl) {
                contentType(ContentType.Application.Json)
                accept(ContentType.Application.Json)
                setBody(ValhallaRouteRequestBuilder.jsonBody(request))
            }
            val responseText = response.bodyAsText()
            if (response.status.value !in 200..299) {
                val message = parseErrorMessage(responseText)
                return ValhallaRouteResult.Failure(
                    message ?: "Valhalla returned HTTP ${response.status.value}."
                )
            }
            decodeRouteResponse(responseText)
        } catch (exception: Exception) {
            ValhallaRouteResult.Failure(
                exception.message?.takeIf { it.isNotBlank() }
                    ?: "Unable to contact the configured Valhalla routing service."
            )
        }
    }

    override fun close() {
        httpClient.close()
    }

    /** Decode a raw response from either HTTP or the on-device native engine. */
    fun decodeRouteResponse(text: String): ValhallaRouteResult = try {
        ValhallaRouteAdapter.adapt(decodeResponse(text))
    } catch (exception: Exception) {
        ValhallaRouteResult.Failure(
            exception.message?.takeIf { it.isNotBlank() } ?: "Valhalla returned an invalid route response."
        )
    }

    private fun decodeResponse(text: String): ValhallaRouteResponse {
        val root = json.parseToJsonElement(text).jsonObject
        val error = parseErrorMessage(root)
        val tripElement = root["trip"] ?: return ValhallaRouteResponse(null, error)
        val trip = tripElement.jsonObject
        val summary = trip["summary"]?.let { element ->
            val value = element.jsonObject
            ValhallaSummary(
                lengthKilometers = value.requiredDouble("length"),
                timeSeconds = value.requiredDouble("time"),
            )
        }
        val legs = trip["legs"]?.jsonArray?.map { legElement ->
            val leg = legElement.jsonObject
            val maneuvers = leg["maneuvers"]?.jsonArray.orEmpty().map { maneuverElement ->
                val maneuver = maneuverElement.jsonObject
                ValhallaManeuver(
                    type = maneuver.requiredInt("type"),
                    instruction = maneuver.requiredString("instruction"),
                    streetNames = maneuver["street_names"]?.jsonArray
                        ?.mapNotNull { it.jsonPrimitive.contentOrNull() }.orEmpty(),
                    beginShapeIndex = maneuver.requiredInt("begin_shape_index"),
                )
            }
            ValhallaLeg(
                shape = leg.requiredString("shape"),
                maneuvers = maneuvers,
            )
        }.orEmpty()

        return ValhallaRouteResponse(
            trip = ValhallaTrip(
                status = trip.requiredInt("status"),
                statusMessage = trip["status_message"]?.jsonPrimitive?.contentOrNull().orEmpty(),
                summary = summary,
                legs = legs,
            ),
            error = error,
        )
    }

    private fun parseErrorMessage(text: String): String? = runCatching {
        parseErrorMessage(json.parseToJsonElement(text).jsonObject)
    }.getOrNull()

    private fun parseErrorMessage(root: JsonObject): String? {
        val error = root["error"] ?: return null
        return when (error) {
            is JsonPrimitive -> error.contentOrNull()
            is JsonObject -> error["message"]?.jsonPrimitive?.contentOrNull()
                ?: error["error"]?.jsonPrimitive?.contentOrNull()
            else -> null
        }?.takeIf { it.isNotBlank() }
    }

    private fun JsonObject.requiredString(name: String): String =
        this[name]?.jsonPrimitive?.contentOrNull()?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Valhalla response is missing '$name'.")

    private fun JsonObject.requiredInt(name: String): Int =
        this[name]?.jsonPrimitive?.intOrNull
            ?: throw IllegalArgumentException("Valhalla response has an invalid '$name'.")

    private fun JsonObject.requiredDouble(name: String): Double =
        this[name]?.jsonPrimitive?.doubleOrNull
            ?: throw IllegalArgumentException("Valhalla response has an invalid '$name'.")

    private fun JsonPrimitive.contentOrNull(): String? =
        if (isString) content else content.takeIf { it != "null" }
}
