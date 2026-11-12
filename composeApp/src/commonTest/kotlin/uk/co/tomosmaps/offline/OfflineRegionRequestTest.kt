package uk.co.tomosmaps.offline

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OfflineRegionRequestTest {
    @Test
    fun acceptsAValidPermittedRegion() {
        val request = OfflineRegionRequest(
            id = "gb-london",
            name = "London",
            providerId = "licensed-provider",
            styleUrl = "https://maps.example.test/style.json",
            west = -0.55,
            south = 51.28,
            east = 0.35,
            north = 51.72,
            providerAllowsOfflineDownloads = true,
        )

        assertEquals("gb-london", request.id)
        assertEquals(5.0, request.minZoom)
    }

    @Test
    fun rejectsUnverifiedProviderPermission() {
        assertFailsWith<IllegalArgumentException> {
            OfflineRegionRequest(
                id = "gb-london",
                name = "London",
                providerId = "unverified-provider",
                styleUrl = "https://maps.example.test/style.json",
                west = -0.55,
                south = 51.28,
                east = 0.35,
                north = 51.72,
            )
        }
    }

    @Test
    fun rejectsInvalidBounds() {
        assertFailsWith<IllegalArgumentException> {
            OfflineRegionRequest(
                id = "gb-london",
                name = "London",
                providerId = "licensed-provider",
                styleUrl = "https://maps.example.test/style.json",
                west = 0.35,
                south = 51.28,
                east = -0.55,
                north = 51.72,
                providerAllowsOfflineDownloads = true,
            )
        }
    }

    @Test
    fun rejectsUnsupportedZoomRange() {
        assertFailsWith<IllegalArgumentException> {
            OfflineRegionRequest(
                id = "gb-london",
                name = "London",
                providerId = "licensed-provider",
                styleUrl = "https://maps.example.test/style.json",
                west = -0.55,
                south = 51.28,
                east = 0.35,
                north = 51.72,
                minZoom = 16.0,
                maxZoom = 15.0,
                providerAllowsOfflineDownloads = true,
            )
        }
    }
}
