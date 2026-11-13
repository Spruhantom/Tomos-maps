package uk.co.tomosmaps.offline

import org.maplibre.compose.sources.mbtilesUrl

actual suspend fun createRegionalOfflineMapStyle(
    mbtilesFileUri: String,
    attribution: String,
): String {
    require(mbtilesFileUri.startsWith("file:")) { "Regional MBTiles must be a local file URI." }
    require(attribution.isNotBlank()) { "Regional map attribution is required." }
    val tileUrl = mbtilesUrl(mbtilesFileUri)
    val glyphsUrl = mbtilesFileUri.substringBeforeLast('/') +
        "/glyphs/{fontstack}/{range}.pbf"
    val safeAttribution = attribution
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", " ")
    return """
        {
          "version": 8,
          "name": "TomOS regional offline map",
          "glyphs": "$glyphsUrl",
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
            },
            {
              "id": "tomos-place-labels",
              "type": "symbol",
              "source": "tomos-regional",
              "source-layer": "place",
              "layout": {
                "text-field": ["coalesce", ["get", "name:en"], ["get", "name"]],
                "text-font": ["Noto Sans Regular"],
                "text-size": ["interpolate", ["linear"], ["zoom"], 5, 10, 10, 13, 15, 16],
                "text-max-width": 8
              },
              "paint": {
                "text-color": "#41413b",
                "text-halo-color": "#ffffff",
                "text-halo-width": 1.2
              }
            },
            {
              "id": "tomos-road-labels",
              "type": "symbol",
              "source": "tomos-regional",
              "source-layer": "transportation_name",
              "layout": {
                "symbol-placement": "line",
                "text-field": ["coalesce", ["get", "name:en"], ["get", "name"]],
                "text-font": ["Noto Sans Regular"],
                "text-size": 10
              },
              "paint": {
                "text-color": "#5d5548",
                "text-halo-color": "#ffffff",
                "text-halo-width": 1
              }
            },
            {
              "id": "tomos-water-labels",
              "type": "symbol",
              "source": "tomos-regional",
              "source-layer": "water_name",
              "layout": {
                "symbol-placement": "line",
                "text-field": ["coalesce", ["get", "name:en"], ["get", "name"]],
                "text-font": ["Noto Sans Regular"],
                "text-size": 10
              },
              "paint": {
                "text-color": "#4d839e",
                "text-halo-color": "#eaf6fb",
                "text-halo-width": 1
              }
            }
          ]
        }
    """.trimIndent()
}
