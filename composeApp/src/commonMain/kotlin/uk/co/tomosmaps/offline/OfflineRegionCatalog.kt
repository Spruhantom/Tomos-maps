package uk.co.tomosmaps.offline

/**
 * Provider policy is explicit metadata, not inferred from a style URL.
 * A provider must be reviewed before offline downloads can be enabled.
 */
data class MapProviderDescriptor(
    val id: String,
    val displayName: String,
    val styleUrl: String,
    val attribution: String,
    val supportsOfflineDownloads: Boolean,
    val policyNote: String,
) {
    init {
        require(id.isNotBlank())
        require(displayName.isNotBlank())
        require(styleUrl.startsWith("https://"))
        require(attribution.isNotBlank())
        require(policyNote.isNotBlank())
    }
}

object MapProviderCatalog {
    val developmentPreview = MapProviderDescriptor(
        id = "openfreemap-liberty",
        displayName = "OpenFreeMap Dark (development preview)",
        styleUrl = "https://tiles.openfreemap.org/styles/dark",
        attribution = "© OpenStreetMap contributors",
        supportsOfflineDownloads = false,
        policyNote = "Offline caching and redistribution rights have not been verified for TomOS Maps.",
    )

    val providers: List<MapProviderDescriptor> = listOf(developmentPreview)

    fun find(providerId: String): MapProviderDescriptor? =
        providers.firstOrNull { it.id == providerId }
}

/** Starter UK areas. Bounds are geographic coverage suggestions, not tile estimates. */
data class OfflineRegionPreset(
    val id: String,
    val name: String,
    val west: Double,
    val south: Double,
    val east: Double,
    val north: Double,
    val minZoom: Double = 5.0,
    val maxZoom: Double = 14.0,
) {
    init {
        require(id.isNotBlank() && name.isNotBlank())
        require(west in -180.0..180.0 && east in -180.0..180.0 && west < east)
        require(south in -90.0..90.0 && north in -90.0..90.0 && south < north)
        require(minZoom >= 0.0 && maxZoom <= 22.0 && minZoom <= maxZoom)
    }
}

object OfflineRegionCatalog {
    val ukPresets: List<OfflineRegionPreset> = listOf(
        OfflineRegionPreset("gb-liverpool", "Liverpool City Region", -3.15, 53.25, -2.55, 53.60, minZoom = 6.0, maxZoom = 15.0),
        OfflineRegionPreset("gb-london", "Greater London", -0.52, 51.28, 0.34, 51.72),
        OfflineRegionPreset("gb-south-east", "South East England", -1.05, 50.70, 1.45, 52.15),
        OfflineRegionPreset("gb-midlands", "The Midlands", -2.25, 51.90, -0.25, 53.15),
        OfflineRegionPreset("gb-north-west", "North West England", -3.65, 53.25, -1.75, 55.05),
        OfflineRegionPreset("gb-scotland-central", "Central Scotland", -4.75, 55.55, -2.45, 56.25),
    )

    fun find(regionId: String): OfflineRegionPreset? =
        ukPresets.firstOrNull { it.id == regionId }
}
