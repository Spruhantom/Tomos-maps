package uk.co.tomosmaps.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NavigationModelsTest {
    @Test
    fun acceptsValidCoordinates() {
        assertEquals(51.5072, GeoPoint(51.5072, -0.1276).latitude)
    }

    @Test
    fun rejectsOutOfRangeLatitude() {
        assertFailsWith<IllegalArgumentException> {
            GeoPoint(91.0, 0.0)
        }
    }

    @Test
    fun rejectsNegativeInstructionDistance() {
        assertFailsWith<IllegalArgumentException> {
            ManeuverInstruction("Turn left", -1)
        }
    }
}
