package uk.co.tomosmaps.offline

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MapPackageManifestJsonTest {
    private val checksum = "a".repeat(64)

    @Test
    fun decodesCompleteLiverpoolPackageManifest() {
        val json = """
            {
              "formatVersion": 1,
              "id": "gb-liverpool",
              "displayName": "Liverpool City Region",
              "packageVersion": "1.0.0",
              "regionCode": "GB-LIV",
              "totalSizeBytes": 12,
              "files": [
                {"path":"map/region.mbtiles","role":"MAP_DATA","sizeBytes":5,"sha256":"$checksum"},
                {"path":"routing/valhalla.tar","role":"ROUTING_GRAPH","sizeBytes":5,"sha256":"$checksum"},
                {"path":"search/postcodes.json","role":"SEARCH_INDEX","sizeBytes":2,"sha256":"$checksum"}
              ]
            }
        """.trimIndent()
        val manifest = decodeMapPackageManifest(json)
        assertEquals("gb-liverpool", manifest.id)
        assertEquals(3, manifest.files.size)
        assertEquals(12L, manifest.totalSizeBytes)
    }

    @Test
    fun rejectsUnknownFileRoles() {
        val json = """
            {"formatVersion":1,"id":"gb-liverpool","displayName":"Liverpool",
             "packageVersion":"1","regionCode":"GB-LIV","totalSizeBytes":10,
             "files":[
               {"path":"map.mbtiles","role":"MAP_DATA","sizeBytes":5,"sha256":"$checksum"},
               {"path":"graph.bin","role":"UNKNOWN","sizeBytes":5,"sha256":"$checksum"}
             ]}
        """.trimIndent()
        assertFailsWith<IllegalArgumentException> { decodeMapPackageManifest(json) }
    }

    @Test
    fun rejectsManifestLargerThanLimit() {
        assertFailsWith<IllegalArgumentException> {
            decodeMapPackageManifest(" ".repeat(1_048_577))
        }
    }
}
