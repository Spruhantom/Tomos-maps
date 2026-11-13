package uk.co.tomosmaps.offline

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OfflineRegionCatalogTest {
    @Test
    fun developmentProviderAllowsOnlyPolicyCappedOfflineDownloads() {
        val provider = requireNotNull(MapProviderCatalog.find("openfreemap-liberty"))
        assertTrue(provider.supportsOfflineDownloads)
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
    fun liverpoolCityCentrePresetCanBeLookedUp() {
        val liverpool = requireNotNull(OfflineRegionCatalog.find("gb-liverpool-centre"))
        assertEquals("Liverpool City Centre", liverpool.name)
        assertTrue(liverpool.west < -2.99 && liverpool.east > -2.99)
    }

    @Test
    fun londonCityCentrePresetCanBeLookedUp() {
        assertEquals("Central London", OfflineRegionCatalog.find("gb-london-centre")?.name)
    }
}
