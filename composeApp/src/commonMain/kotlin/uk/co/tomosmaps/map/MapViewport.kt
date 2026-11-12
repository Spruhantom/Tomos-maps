package uk.co.tomosmaps.map

import uk.co.tomosmaps.navigation.GeoPoint
import uk.co.tomosmaps.offline.isSafePackagePath

/** Camera state shared by Android and Linux map renderers. */
data class MapCamera(
    val center: GeoPoint,
    val zoom: Double = 12.0,
    val bearingDegrees: Double = 0.0,
    val pitchDegrees: Double = 0.0
) {
    init {
        require(zoom.isFinite() && zoom in 0.0..24.0) { "Zoom must be between 0 and 24" }
        require(bearingDegrees.isFinite()) { "Bearing must be finite" }
        require(pitchDegrees.isFinite() && pitchDegrees in 0.0..85.0) {
            "Pitch must be between 0 and 85 degrees"
        }
    }
}

enum class MapMarkerKind { SEARCH_RESULT, FAVOURITE, DESTINATION, WAYPOINT, INCIDENT }

data class MapMarker(
    val id: String,
    val coordinate: GeoPoint,
    val title: String,
    val kind: MapMarkerKind
) {
    init {
        require(id.isNotBlank())
        require(title.isNotBlank())
    }
}

data class RouteOverlay(
    val points: List<GeoPoint>,
    val isActive: Boolean = true
) {
    init {
        require(points.size >= 2) { "A route overlay needs at least two points" }
    }
}

/** A style can be supplied by a provider or loaded from an installed offline package. */
sealed interface MapStyleSource {
    data class Online(val httpsUrl: String) : MapStyleSource {
        init {
            require(httpsUrl.startsWith("https://")) { "Online map styles must use HTTPS" }
        }
    }

    data class Offline(val relativeStylePath: String) : MapStyleSource {
        init {
            require(isSafePackagePath(relativeStylePath)) { "Invalid offline style path" }
        }
    }
}

/**
 * Rendering adapter implemented by each platform's MapLibre integration.
 * Implementations must preserve offline behaviour when an Offline style is active.
 */
interface MapRenderer {
    suspend fun setStyle(source: MapStyleSource)
    suspend fun moveCamera(camera: MapCamera)
    suspend fun showMarkers(markers: List<MapMarker>)
    suspend fun showRoute(route: RouteOverlay?)
}
