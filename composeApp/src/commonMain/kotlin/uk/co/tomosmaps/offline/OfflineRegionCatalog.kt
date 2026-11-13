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
        displayName = "OpenFreeMap Dark",
        styleUrl = "https://tiles.openfreemap.org/styles/dark",
        attribution = "© OpenStreetMap contributors",
        supportsOfflineDownloads = false,
        policyNote = "OpenFreeMap permits limited offline downloads (up to 3,000 tiles or 50 MB per download), with at most four parallel requests and a unique User-Agent. TomOS sends its application User-Agent, but MapLibre Compose 0.20.0 does not expose a supported four-request cap; new downloads stay blocked until that limit can be enforced safely.",
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
        OfflineRegionPreset("gb-liverpool-centre", "Liverpool City Centre", -3.02, 53.39, -2.96, 53.43, 10.0, 16.0),
        OfflineRegionPreset("gb-manchester-centre", "Manchester City Centre", -2.28, 53.46, -2.20, 53.50, 10.0, 16.0),
        OfflineRegionPreset("gb-london-centre", "Central London", -0.15, 51.49, -0.08, 51.54, 10.0, 16.0),
        OfflineRegionPreset("gb-birmingham-centre", "Birmingham City Centre", -1.92, 52.46, -1.86, 52.50, 10.0, 16.0),
        OfflineRegionPreset("gb-leeds-centre", "Leeds City Centre", -1.57, 53.78, -1.51, 53.82, 10.0, 16.0),
        OfflineRegionPreset("gb-glasgow-centre", "Glasgow City Centre", -4.29, 55.85, -4.20, 55.89, 10.0, 16.0),
        OfflineRegionPreset("gb-edinburgh-centre", "Edinburgh City Centre", -3.22, 55.93, -3.16, 55.97, 10.0, 16.0),
    )

    fun find(regionId: String): OfflineRegionPreset? =
        ukPresets.firstOrNull { it.id == regionId }
}
