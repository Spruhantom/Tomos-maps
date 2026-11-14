package uk.co.tomosmaps.offline

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class OfflineSearchIndexTest {
    private val sample = """
        {
          "formatVersion": 1,
          "records": [
            {"id":"p1","label":"Liverpool Lime Street","latitude":53.4074,"longitude":-2.9775,"postcode":"L1 1JD","category":"railway station"},
            {"id":"p2","label":"Liverpool Cathedral","latitude":53.3967,"longitude":-2.9732,"postcode":"L1 7AZ","category":"place of worship"},
            {"id":"p3","label":"Manchester Piccadilly","latitude":53.4774,"longitude":-2.2309,"postcode":"M1 2GH","category":"railway station"}
          ]
        }
    """.trimIndent()

    @Test
    fun findsFullAndPartialPostcodesOffline() {
        val index = OfflineSearchIndex.parse(sample)
        assertEquals("Liverpool Lime Street", index.search("L1 1JD").single().record.label)
        assertEquals(2, index.search("L1").size)
        assertTrue(index.search("L1").all { it.matchedBy == OfflineSearchMatch.POSTCODE })
    }

    @Test
    fun searchesPlaceNamesAndCategories() {
        val index = OfflineSearchIndex.parse(sample)
        assertEquals("Liverpool Cathedral", index.search("cathedral").single().record.label)
        assertEquals(2, index.search("railway station").size)
    }

    @Test
    fun rejectsUnsupportedVersionsDuplicateIdsAndBadCoordinates() {
        assertFailsWith<IllegalArgumentException> {
            OfflineSearchIndex.parse("""{"formatVersion":2,"records":[]}""")
        }
        assertFailsWith<IllegalArgumentException> {
            OfflineSearchIndex.parse("""{"formatVersion":1,"records":[
                {"id":"x","label":"One","latitude":0,"longitude":0},
                {"id":"x","label":"Two","latitude":0,"longitude":0}
            ]}""")
        }
        assertFailsWith<IllegalArgumentException> {
            OfflineSearchIndex.parse("""{"formatVersion":1,"records":[
                {"id":"x","label":"Bad","latitude":95,"longitude":0}
            ]}""")
        }
    }
}
