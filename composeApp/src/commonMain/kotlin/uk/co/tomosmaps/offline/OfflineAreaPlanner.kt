package uk.co.tomosmaps.offline

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

/** User-selected layers for an offline area. */
enum class OfflineMapLayer(val title: String, val customAreaSupported: Boolean) {
    MAP("Map background", true),
    ROUTING("Roads and routing", false),
    SEARCH("UK postcodes and places", false),
}

/** City-sized defaults stay within the OpenFreeMap pack limits at the selected zoom range. */
enum class OfflineAreaSize(val title: String, val radiusKm: Double) {
    SMALL("Small", 1.0),
    CITY("City", 3.0),
    LARGE("Large", 5.0),
}

data class OfflineAreaBounds(
    val west: Double,
    val south: Double,
    val east: Double,
    val north: Double,
) {
    init {
        require(west.isFinite() && east.isFinite() && south.isFinite() && north.isFinite())
        require(west in -180.0..180.0 && east in -180.0..180.0 && west < east)
        require(south in -85.0..85.0 && north in -85.0..85.0 && south < north)
    }
}

data class OfflineLayerEstimate(
    val layer: OfflineMapLayer,
    val available: Boolean,
    val estimatedBytesMinimum: Long? = null,
    val estimatedBytesMaximum: Long? = null,
    val note: String,
)

data class OfflineAreaEstimate(
    val bounds: OfflineAreaBounds,
    val tileCount: Long,
    val withinProviderLimits: Boolean,
    val layers: List<OfflineLayerEstimate>,
) {
    val selectedDownloadMinimumBytes: Long
        get() = layers.filter { it.available }.sumOf { it.estimatedBytesMinimum ?: 0L }
    val selectedDownloadMaximumBytes: Long
        get() = layers.filter { it.available }.sumOf { it.estimatedBytesMaximum ?: 0L }
}

/**
 * Builds a city/small/large rectangle around the chosen map centre and estimates
 * the MapLibre tile pyramid. Byte figures are deliberately ranges: actual
 * vector-tile sizes vary by density, style and provider compression.
 */
object OfflineAreaPlanner {
    const val MIN_ZOOM = 10.0
    const val MAX_ZOOM = 16.0
    const val MAX_ESTIMATED_TILES = OpenFreeMapResourcePipeline.MAX_TILES_PER_DOWNLOAD
    private const val MIN_BYTES_PER_TILE = 8L * 1024L
    private const val MAX_BYTES_PER_TILE = 24L * 1024L
    private const val MAX_MERCATOR_LATITUDE = 85.05112878

    fun plan(
        centerLatitude: Double,
        centerLongitude: Double,
        size: OfflineAreaSize,
    ): OfflineAreaEstimate {
        require(centerLatitude.isFinite() && centerLatitude in -85.0..85.0)
        require(centerLongitude.isFinite() && centerLongitude in -180.0..180.0)

        val latitudeDelta = size.radiusKm / 111.32
        val longitudeScale = cos(centerLatitude * PI / 180.0).coerceAtLeast(0.01)
        val longitudeDelta = size.radiusKm / (111.32 * longitudeScale)
        val bounds = OfflineAreaBounds(
            west = (centerLongitude - longitudeDelta).coerceAtLeast(-180.0),
            south = (centerLatitude - latitudeDelta).coerceAtLeast(-85.0),
            east = (centerLongitude + longitudeDelta).coerceAtMost(180.0),
            north = (centerLatitude + latitudeDelta).coerceAtMost(85.0),
        )
        val tiles = countTilePyramid(bounds, MIN_ZOOM.toInt(), MAX_ZOOM.toInt())
        val mapMinimum = tiles * MIN_BYTES_PER_TILE
        val mapMaximum = tiles * MAX_BYTES_PER_TILE
        val layers = listOf(
            OfflineLayerEstimate(
                OfflineMapLayer.MAP,
                available = tiles <= MAX_ESTIMATED_TILES,
                estimatedBytesMinimum = mapMinimum,
                estimatedBytesMaximum = mapMaximum,
                note = "Estimated from the tile pyramid; actual download size depends on map detail and compression.",
            ),
            OfflineLayerEstimate(
                OfflineMapLayer.ROUTING,
                available = false,
                note = "Custom-area routing graphs are not generated yet. Use a published regional package.",
            ),
            OfflineLayerEstimate(
                OfflineMapLayer.SEARCH,
                available = false,
                note = "Custom-area postcode/place indexes are not generated yet. Use a published regional package.",
            ),
        )
        return OfflineAreaEstimate(
            bounds = bounds,
            tileCount = tiles,
            withinProviderLimits = tiles in 1..MAX_ESTIMATED_TILES,
            layers = layers,
        )
    }

    fun countTilePyramid(bounds: OfflineAreaBounds, minZoom: Int, maxZoom: Int): Long {
        require(minZoom in 0..22 && maxZoom in minZoom..22)
        var total = 0L
        for (zoom in minZoom..maxZoom) {
            val xWest = longitudeToTileX(bounds.west, zoom)
            val xEast = longitudeToTileX(bounds.east, zoom)
            val yNorth = latitudeToTileY(bounds.north, zoom)
            val ySouth = latitudeToTileY(bounds.south, zoom)
            total += (xEast - xWest + 1L) * (ySouth - yNorth + 1L)
        }
        return total
    }

    private fun longitudeToTileX(longitude: Double, zoom: Int): Long {
        val count = 1L shl zoom
        return floor((longitude + 180.0) / 360.0 * count)
            .toLong().coerceIn(0L, count - 1L)
    }

    private fun latitudeToTileY(latitude: Double, zoom: Int): Long {
        val clamped = latitude.coerceIn(-MAX_MERCATOR_LATITUDE, MAX_MERCATOR_LATITUDE)
        val radians = clamped * PI / 180.0
        val value = (1.0 - ln(tan(radians) + 1.0 / cos(radians)) / PI) / 2.0
        val count = 1L shl zoom
        return floor(value * count).toLong().coerceIn(0L, count - 1L)
    }
}
