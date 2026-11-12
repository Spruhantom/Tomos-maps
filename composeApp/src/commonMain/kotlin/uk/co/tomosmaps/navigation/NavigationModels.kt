package uk.co.tomosmaps.navigation

/** Geographic coordinate in WGS84 decimal degrees. */
data class GeoPoint(
    val latitude: Double,
    val longitude: Double
) {
    init {
        require(latitude in -90.0..90.0) { "Latitude must be between -90 and 90" }
        require(longitude in -180.0..180.0) { "Longitude must be between -180 and 180" }
    }
}

enum class TravelMode { DRIVE, WALK, CYCLE }

data class RouteRequest(
    val origin: GeoPoint,
    val destination: GeoPoint,
    val mode: TravelMode = TravelMode.DRIVE,
    val avoidTolls: Boolean = false,
    val avoidMotorways: Boolean = false
)

data class ManeuverInstruction(
    val text: String,
    val distanceMeters: Int,
    val streetName: String? = null
) {
    init {
        require(text.isNotBlank()) { "Instruction text cannot be blank" }
        require(distanceMeters >= 0) { "Instruction distance cannot be negative" }
    }
}

data class NavigationRoute(
    val id: String,
    val distanceMeters: Long,
    val durationSeconds: Long,
    val instructions: List<ManeuverInstruction>
) {
    init {
        require(id.isNotBlank())
        require(distanceMeters >= 0)
        require(durationSeconds >= 0)
    }
}

/** Implementations must calculate from locally available graph data for offline use. */
interface OfflineRoutePlanner {
    suspend fun calculateRoute(request: RouteRequest): Result<NavigationRoute>
}
