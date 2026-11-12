package uk.co.tomosmaps.navigation

enum class ValhallaCosting(val apiValue: String) {
    CAR("auto"),
    BICYCLE("bicycle"),
    PEDESTRIAN("pedestrian"),
}

data class ValhallaRouteRequest(
    val origin: RouteCoordinate,
    val destination: RouteCoordinate,
    val costing: ValhallaCosting = ValhallaCosting.CAR,
    val units: String = "kilometers",
)

/**
 * Builds a POST request for a configured Valhalla deployment.
 * Callers supply their own trusted endpoint; no public routing host is assumed.
 */
object ValhallaRouteRequestBuilder {
    fun routeUrl(baseUrl: String): String {
        val normalized = baseUrl.trim().trimEnd('/')
        require(normalized.startsWith("https://") || normalized.startsWith("http://localhost")) {
            "Valhalla endpoint must use HTTPS (HTTP is allowed only for localhost development)."
        }
        require(!normalized.contains("?") && !normalized.contains("#")) {
            "Valhalla base URL must not contain a query or fragment."
        }
        return normalized + "/route"
    }

    fun jsonBody(request: ValhallaRouteRequest): String {
        require(request.units == "kilometers" || request.units == "miles") {
            "Units must be kilometers or miles."
        }
        return "{\"locations\":[" +
            "{\"lat\":" + request.origin.latitude + ",\"lon\":" + request.origin.longitude + ",\"type\":\"break\"}," +
            "{\"lat\":" + request.destination.latitude + ",\"lon\":" + request.destination.longitude + ",\"type\":\"break\"}]," +
            "\"costing\":\"" + request.costing.apiValue + "\",\"units\":\"" + request.units +
            "\",\"shape_format\":\"polyline6\",\"directions_options\":{\"units\":\"" +
            request.units + "\"}}"
    }
}
