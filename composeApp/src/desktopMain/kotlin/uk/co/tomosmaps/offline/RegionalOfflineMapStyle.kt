package uk.co.tomosmaps.offline

import org.maplibre.compose.sources.mbtilesUrl

actual suspend fun createRegionalOfflineMapStyle(
    mbtilesFileUri: String,
    attribution: String,
): String {
    require(mbtilesFileUri.startsWith("file:")) { "Regional MBTiles must be a local file URI." }
    require(attribution.isNotBlank()) { "Regional map attribution is required." }
    val tileUrl = mbtilesUrl(mbtilesFileUri)
    val safeAttribution = attribution
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", " ")
    return """
        {
          "version": 8,
          "name": "TomOS regional offline map",
          "metadata": {
            "tomos:offline": true,
            "tomos:attribution": "$safeAttribution"
          },
          "sources": {
            "tomos-regional": {
              "type": "vector",
              "tiles": ["$tileUrl"],
              "minzoom": 0,
              "maxzoom": 14,
              "attribution": "$safeAttribution"
            }
          },
          "layers": [
            {
              "id": "tomos-background",
              "type": "background",
              "paint": {"background-color": "#f1f0e8"}
            },
            {
              "id": "tomos-landcover",
              "type": "fill",
              "source": "tomos-regional",
              "source-layer": "landcover",
              "paint": {"fill-color": "#dce8d1", "fill-opacity": 0.8}
            },
            {
              "id": "tomos-landuse",
              "type": "fill",
              "source": "tomos-regional",
              "source-layer": "landuse",
              "paint": {"fill-color": "#e5ead7", "fill-opacity": 0.85}
            },
            {
              "id": "tomos-water",
              "type": "fill",
              "source": "tomos-regional",
              "source-layer": "water",
              "paint": {"fill-color": "#a9d4e8"}
            },
            {
              "id": "tomos-waterway",
              "type": "line",
              "source": "tomos-regional",
              "source-layer": "waterway",
              "paint": {"line-color": "#8fc7df", "line-width": 1.5}
            },
            {
              "id": "tomos-buildings",
              "type": "fill",
              "source": "tomos-regional",
              "source-layer": "building",
              "minzoom": 13,
              "paint": {"fill-color": "#d4cfc5", "fill-outline-color": "#c2bcb1"}
            },
            {
              "id": "tomos-roads-casing",
              "type": "line",
              "source": "tomos-regional",
              "source-layer": "transportation",
              "paint": {
                "line-color": "#ffffff",
                "line-width": ["interpolate", ["linear"], ["zoom"], 5, 0.7, 10, 2, 15, 7]
              }
            },
            {
              "id": "tomos-roads",
              "type": "line",
              "source": "tomos-regional",
              "source-layer": "transportation",
              "paint": {
                "line-color": "#e8b86a",
                "line-width": ["interpolate", ["linear"], ["zoom"], 5, 0.35, 10, 1.2, 15, 4.5]
              }
            }
          ]
        }
    """.trimIndent()
}
