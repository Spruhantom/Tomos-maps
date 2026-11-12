package uk.co.tomosmaps.offline

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OfflineRegionCatalogTest {
    @Test
    fun developmentProviderIsNotApprovedForOfflineDownloads() {
        val provider = requireNotNull(MapProviderCatalog.find("openfreemap-liberty"))

        assertFalse(provider.supportsOfflineDownloads)
        assertTrue(provider.styleUrl.startsWith("https://"))
        assertTrue(provider.policyNote.isNotBlank())
    }

    @Test
    fun unknownProvidersAreNotTreatedAsApproved() {
        assertNull(MapProviderCatalog.find("unknown-provider"))
    }

    @Test
    fun ukPresetsHaveUniqueIdsAndValidBounds() {
        val presets = OfflineRegionCatalog.ukPresets

        assertTrue(presets.isNotEmpty())
        assertEquals(presets.size, presets.map { it.id }.toSet().size)
        presets.forEach { preset ->
            assertTrue(preset.west < preset.east)
            assertTrue(preset.south < preset.north)
            assertTrue(preset.minZoom <= preset.maxZoom)
        }
    }

    @Test
    fun londonPresetCanBeLookedUp() {
        assertEquals(
            "Greater London",
            OfflineRegionCatalog.find("gb-london")?.name,
        )
    }
}
