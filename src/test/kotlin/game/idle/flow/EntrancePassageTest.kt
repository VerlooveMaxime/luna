package game.idle.flow

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EntrancePassageTest {

    @Test
    fun `an option of one word goes through without naming the way`() {
        assertEquals(EntrancePassage("enter", Way.THROUGH, wayNamed = false, "cave entrance"), EntrancePassage.of("Enter", "Cave Entrance", climbsDown = false))
    }

    @Test
    fun `an option naming its way after a space goes that way`() {
        assertEquals(EntrancePassage("crawl", Way.DOWN, wayNamed = true, "cart tunnel"), EntrancePassage.of("Crawl down", "Cart tunnel", climbsDown = false))
    }

    @Test
    fun `an option naming its way after a hyphen goes that way`() {
        assertEquals(EntrancePassage("climb", Way.OVER, wayNamed = true, "mud pile"), EntrancePassage.of("Climb-over", "Mud pile", climbsDown = false))
    }

    @Test
    fun `an option naming a way past it goes through`() {
        assertEquals(EntrancePassage("squeeze", Way.THROUGH, wayNamed = true, "hole"), EntrancePassage.of("Squeeze-through", "Hole", climbsDown = false))
    }

    @Test
    fun `a plain climb goes up, as a ladder says it`() {
        assertEquals(EntrancePassage("climb", Way.UP, wayNamed = true, "climbing rope"), EntrancePassage.of("Climb", "Climbing rope", climbsDown = false))
    }

    @Test
    fun `a plain climb Luna climbs down goes down`() {
        assertEquals(EntrancePassage("climb", Way.DOWN, wayNamed = true, "iron ladder"), EntrancePassage.of("Climb", "Iron ladder", climbsDown = true))
    }

    @Test
    fun `descending goes down without naming the way`() {
        assertEquals(EntrancePassage("descend", Way.DOWN, wayNamed = false, "stairs"), EntrancePassage.of("Descend", "Stairs", climbsDown = false))
    }

    @Test
    fun `climbing down is only a plain climb's`() {
        assertEquals(Way.THROUGH, EntrancePassage.of("Enter", "Cave", climbsDown = true).way)
    }
}
