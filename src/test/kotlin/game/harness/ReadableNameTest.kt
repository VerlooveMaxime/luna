package game.harness

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ReadableNameTest {

    @Test
    fun `a named class reads as its simple name`() {
        assertEquals("ReadableNameTest", readableName(ReadableNameTest::class.java))
    }

    @Test
    fun `an anonymous class reads as its binary name`() {
        val anonymous = object : Runnable {
            override fun run() = Unit
        }

        assertEquals(anonymous.javaClass.name, readableName(anonymous.javaClass))
    }
}
