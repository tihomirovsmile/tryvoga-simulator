package space.tihsmile.trivogasim

import org.junit.Assert.*
import org.junit.Test
import space.tihsmile.trivogasim.core.AlertKind

class AlertKindTest {
    @Test fun allKindsHaveInstructions() {
        for (kind in AlertKind.entries) {
            assertTrue(kind.label.isNotBlank())
            assertTrue(kind.advice.isNotBlank())
        }
    }

    @Test fun supplementaryThreatsHaveNoAllClear() {
        for (kind in listOf(AlertKind.INCREASED, AlertKind.DRONE, AlertKind.MISSILE,
            AlertKind.BALLISTIC, AlertKind.BOMB)) {
            assertFalse(kind.hasAllClear)
        }
        assertTrue(AlertKind.AIR.hasAllClear)
        assertTrue(AlertKind.SPECIAL.hasAllClear)
    }

    @Test fun invalidKindFallsBackToSpecial() {
        assertEquals(AlertKind.SPECIAL, AlertKind.from("unknown"))
    }
}
