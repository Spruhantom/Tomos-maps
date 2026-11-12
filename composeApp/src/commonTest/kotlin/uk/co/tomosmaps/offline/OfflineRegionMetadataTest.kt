package uk.co.tomosmaps.offline

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OfflineRegionMetadataTest {
    @Test
    fun metadataRoundTrips() {
        val original = OfflineRegionMetadata(
            id = "gb-london",
            name = "Greater London",
            providerId = "openfreemap-liberty",
        )

        assertEquals(original, OfflineRegionMetadata.decode(original.encode()))
    }

    @Test
    fun rejectsUnknownVersionAndMalformedFields() {
        assertNull(OfflineRegionMetadata.decode("other-version\nid\nname\nprovider".encodeToByteArray()))
        assertNull(OfflineRegionMetadata.decode("tomos-offline-region-v1\nid\nname".encodeToByteArray()))
        assertNull(OfflineRegionMetadata.decode("tomos-offline-region-v1\nid\n\nprovider".encodeToByteArray()))
        assertNull(OfflineRegionMetadata.decode("tomos-offline-region-v1\nid\nname\r\nprovider".encodeToByteArray()))
        assertNull(OfflineRegionMetadata.decode(null))
    }
}
