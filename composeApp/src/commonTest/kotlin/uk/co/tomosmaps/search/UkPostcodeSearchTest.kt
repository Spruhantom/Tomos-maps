package uk.co.tomosmaps.search

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class UkPostcodeSearchTest {
    @Test
    fun normalisesPrefixAndParsesCoordinates() = runBlocking {
        var requestedUrl = ""
        val client = HttpClient(MockEngine { request ->
            requestedUrl = request.url.toString()
            respond(
                content = """{"status":200,"result":[{"postcode":"SW1A 0AA","latitude":51.501,"longitude":-0.141,"country":"England","region":"London","admin_district":"Westminster"}]}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        })
        try {
            val results = UkPostcodeSearch(client).search(" sw1a ").getOrThrow()
            assertTrue(requestedUrl.contains("q=SW1A"))
            assertEquals(1, results.size)
            assertEquals("SW1A 0AA", results.single().postcode)
            assertEquals(51.501, results.single().latitude)
            assertEquals(-0.141, results.single().longitude)
            assertEquals("Westminster", results.single().adminDistrict)
        } finally {
            client.close()
        }
    }

    @Test
    fun rejectsShortAndInvalidQueriesBeforeNetworkAccess() = runBlocking {
        var requested = false
        val client = HttpClient(MockEngine {
            requested = true
            respond("{}", HttpStatusCode.OK)
        })
        try {
            assertFalse(UkPostcodeSearch(client).search("S").isSuccess)
            assertFalse(UkPostcodeSearch(client).search("SW1A!").isSuccess)
            assertFalse(requested)
        } finally {
            client.close()
        }
    }

    @Test
    fun malformedCoordinatesAreDiscarded() = runBlocking {
        val client = HttpClient(MockEngine {
            respond(
                """{"status":200,"result":[{"postcode":"SW1A 0AA","latitude":999,"longitude":0}]}""",
                HttpStatusCode.OK,
            )
        })
        try {
            assertTrue(UkPostcodeSearch(client).search("SW1A").getOrThrow().isEmpty())
        } finally {
            client.close()
        }
    }
}
