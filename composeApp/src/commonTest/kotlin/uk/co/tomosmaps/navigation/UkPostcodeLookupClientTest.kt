package uk.co.tomosmaps.navigation

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class UkPostcodeLookupClientTest {
    @Test
    fun normalizesPostcodeAndReturnsCoordinates() = runBlocking {
        var requestedPath = ""
        val client = UkPostcodeLookupClient(
            HttpClient(MockEngine { request ->
                requestedPath = request.url.encodedPath
                respond(
                    content = """{"status":200,"result":{"postcode":"SW1A 1AA","latitude":51.501,"longitude":-0.141}}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            })
        )

        val result = client.lookup(" sw1a  1aa ")

        assertEquals("/postcodes/SW1A1AA", requestedPath)
        val success = assertIs<UkPostcodeLookupResult.Success>(result)
        assertEquals("SW1A 1AA", success.postcode)
        assertEquals(RouteCoordinate(51.501, -0.141), success.coordinate)
        client.close()
    }

    @Test
    fun rejectsInvalidPostcodeWithoutNetworkRequest() = runBlocking {
        var requested = false
        val client = UkPostcodeLookupClient(
            HttpClient(MockEngine {
                requested = true
                error("Invalid postcode must not make a request.")
            })
        )

        val result = client.lookup("not a postcode")

        assertIs<UkPostcodeLookupResult.Failure>(result)
        assertEquals(false, requested)
        client.close()
    }

    @Test
    fun returnsClearNotFoundMessage() = runBlocking {
        val client = UkPostcodeLookupClient(
            HttpClient(MockEngine {
                respond(
                    content = """{"status":404,"error":"Invalid postcode"}""",
                    status = HttpStatusCode.NotFound,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            })
        )

        val result = client.lookup("SW1A 1AA")

        assertEquals(
            "That UK postcode could not be found.",
            assertIs<UkPostcodeLookupResult.Failure>(result).reason,
        )
        client.close()
    }
}
