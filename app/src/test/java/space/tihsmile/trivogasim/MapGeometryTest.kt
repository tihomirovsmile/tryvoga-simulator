package space.tihsmile.trivogasim

import org.junit.Assert.*
import org.junit.Test
import space.tihsmile.trivogasim.data.GeoRepository
import space.tihsmile.trivogasim.data.MapShape

class MapGeometryTest {
    @Test fun geographicSlugMatchesSourceNaming() {
        assertEquals("poltavska_oblast", GeoRepository.slug("Полтавська область"))
        assertEquals("kremenchutskyy_rayon", GeoRepository.slug("Кременчуцький район"))
        assertEquals("odeska_oblast", GeoRepository.slug("Одеська область"))
    }
    @Test fun exactPolygonContainsAndExcludes() {
        val shape = MapShape("Тестовий район", listOf(listOf(
            (20.0 to 45.0), (30.0 to 45.0), (30.0 to 50.0), (20.0 to 50.0))))
        assertTrue(shape.contains(25.0, 47.0))
        assertFalse(shape.contains(35.0, 47.0))
        assertEquals(25.0, shape.center.first, 0.01)
    }
}
