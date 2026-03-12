package com.example.emomap

object MapConfig {
    // Keep remote style for optional future use/debug.
    const val DEFAULT_STYLE_URL = "https://demotiles.maplibre.org/style.json"

    // Raster style avoids vector glyph/sprite dependencies and is more robust on mobile networks.
    val DEFAULT_STYLE_JSON: String = """
        {
          "version": 8,
          "name": "OSM Raster",
          "sources": {
            "osm": {
              "type": "raster",
              "tiles": [
                "https://a.tile.openstreetmap.org/{z}/{x}/{y}.png",
                "https://b.tile.openstreetmap.org/{z}/{x}/{y}.png",
                "https://c.tile.openstreetmap.org/{z}/{x}/{y}.png"
              ],
              "tileSize": 256,
              "minzoom": 0,
              "maxzoom": 19
            }
          },
          "layers": [
            {
              "id": "background",
              "type": "background",
              "paint": {
                "background-color": "#f3efe2"
              }
            },
            {
              "id": "osm",
              "type": "raster",
              "source": "osm",
              "minzoom": 0,
              "maxzoom": 19
            }
          ]
        }
    """.trimIndent()
}
