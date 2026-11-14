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
 * Default online routing host for testing builds. This is a community demo
 * service with fair-use limits, not a production SLA-backed TomOS service.
 */
object TomosRoutingConfig {
    const val DEFAULT_ENDPOINT = "https://valhalla1.openstreetmap.de"
}

/** Builds a POST request for the app-configured Valhalla deployment. */
object ValhallaRouteRequestBuilder {
    fun routeUrl(baseUrl: String): String {
        val normalized = baseUrl.trim().trimEnd('/')
        val isHttps = normalized.startsWith("https://")
        val isLocalHttp = normalized.startsWith("http://") &&
            normalized.removePrefix("http://").substringBefore('/').let { host ->
                host == "localhost" || host.startsWith("localhost:") ||
                    host == "127.0.0.1" || host.startsWith("127.0.0.1:")
            }
        require(isHttps || isLocalHttp) {
            "Valhalla endpoint must use HTTPS (HTTP is allowed only for localhost development)."
        }
        val authority = normalized.removePrefix("https://").removePrefix("http://").substringBefore('/')
        require(authority.isNotBlank() && authority.none { it.isWhitespace() || it == '\\' } &&
            !authority.contains('@')
        ) {
            "Valhalla endpoint must contain a valid host without credentials or whitespace."
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
