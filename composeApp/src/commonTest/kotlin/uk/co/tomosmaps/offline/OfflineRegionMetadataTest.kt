package uk.co.tomosmaps.offline

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFailsWith

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
    fun duplicateRegionIdsFailInsteadOfSelectingAnArbitraryPack() {
        val first = OfflineRegionMetadata("gb-london", "London", "provider-a")
        val second = OfflineRegionMetadata("gb-london", "Greater London", "provider-b")
        val packs = listOf(first, second)

        assertFailsWith<IllegalArgumentException> {
            findUniqueOfflineRegion(packs, "gb-london") { it }
        }
    }

    @Test
    fun uniqueRegionIdResolvesAndMissingIdReturnsNull() {
        val region = OfflineRegionMetadata("gb-london", "London", "provider")

        assertEquals(region, findUniqueOfflineRegion(listOf(region), "gb-london") { it })
        assertNull(findUniqueOfflineRegion(listOf(region), "gb-scotland") { it })
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
