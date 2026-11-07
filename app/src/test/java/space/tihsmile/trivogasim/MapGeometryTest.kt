package space.tihsmile.trivogasim

import org.junit.Assert.*
import org.junit.Test
import space.tihsmile.trivogasim.data.MapShape

class MapGeometryTest {
    @Test fun polygonBoundsAreCalculatedOnceAndCorrectly() {
        val shape = MapShape("Район", listOf(listOf(
            (20.0 to 45.0), (30.0 to 45.0), (30.0 to 50.0), (20.0 to 50.0))))
        assertEquals(20.0, shape.west, .001)
        assertEquals(30.0, shape.east, .001)
        assertEquals(45.0, shape.south, .001)
        assertEquals(50.0, shape.north, .001)
    }
    @Test fun pointInsidePolygon() {
        val shape = MapShape("Район", listOf(listOf(
            (20.0 to 45.0), (30.0 to 45.0), (30.0 to 50.0), (20.0 to 50.0))))
        assertTrue(shape.contains(25.0, 47.0))
        assertFalse(shape.contains(35.0, 47.0))
        assertFalse(shape.contains(25.0, 60.0))
    }
}
