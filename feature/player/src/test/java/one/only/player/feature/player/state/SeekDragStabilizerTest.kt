package one.only.player.feature.player.state

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SeekDragStabilizerTest {
    @Test
    fun `ignores small direction reversals around the current preview`() {
        val stabilizer = SeekDragStabilizer()

        assertEquals(8f, stabilizer.add(dragAmount = 12f, hysteresisPx = 4f))
        assertNull(stabilizer.add(dragAmount = -2f, hysteresisPx = 4f))
        assertNull(stabilizer.add(dragAmount = 1f, hysteresisPx = 4f))
        assertNull(stabilizer.add(dragAmount = -2f, hysteresisPx = 4f))
        assertNull(stabilizer.add(dragAmount = 2f, hysteresisPx = 4f))
    }

    @Test
    fun `follows deliberate movement in either direction`() {
        val stabilizer = SeekDragStabilizer()

        assertEquals(8f, stabilizer.add(dragAmount = 12f, hysteresisPx = 4f))
        assertEquals(12f, stabilizer.add(dragAmount = 4f, hysteresisPx = 4f))
        assertEquals(6f, stabilizer.add(dragAmount = -14f, hysteresisPx = 4f))
    }

    @Test
    fun `reset removes displacement from the previous gesture`() {
        val stabilizer = SeekDragStabilizer()

        assertEquals(8f, stabilizer.add(dragAmount = 12f, hysteresisPx = 4f))
        stabilizer.reset()

        assertNull(stabilizer.add(dragAmount = 3f, hysteresisPx = 4f))
        assertEquals(1f, stabilizer.add(dragAmount = 2f, hysteresisPx = 4f))
    }
}
