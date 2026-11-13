package uk.co.tomosmaps.offline

/**
 * Creates a MapLibre Native style whose vector tiles are read from an installed
 * regional MBTiles archive. The URI must point to a verified local file.
 */
expect suspend fun createRegionalOfflineMapStyle(
    mbtilesFileUri: String,
    attribution: String,
): String
