package engine.combat.drops

import io.luna.util.GsonUtils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.file.Paths

class TutorialRatDropsTest {

    private val tables = GsonUtils.readAsType(Paths.get("data", "game", "def", "npcs", "drops.jsonc"), Array<StaticNpcDropTable>::class.java)

    private val islandRat = 950

    @Test
    fun `the island's giant rat has one drop table`() {
        assertEquals(1, tables.count { islandRat in it.ids })
    }

    @Test
    fun `the island's giant rat drops only bones`() {
        assertEquals(listOf(526), tables.single { islandRat in it.ids }.drops.map { it.id })
    }
}
