package game.idle.content.audit

import api.drops.DropTableHandler
import api.event.Matcher
import game.testworld.TestWorld
import io.luna.game.event.Event
import io.luna.game.event.impl.NpcClickEvent.NpcFirstClickEvent
import io.luna.game.event.impl.NpcClickEvent.NpcFourthClickEvent
import io.luna.game.event.impl.NpcClickEvent.NpcSecondClickEvent
import io.luna.game.event.impl.NpcClickEvent.NpcThirdClickEvent
import io.luna.game.event.impl.ObjectClickEvent.ObjectFirstClickEvent
import io.luna.game.event.impl.ObjectClickEvent.ObjectSecondClickEvent
import io.luna.game.event.impl.ObjectClickEvent.ObjectThirdClickEvent
import io.luna.game.model.mob.interact.InteractionPolicy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import kotlin.reflect.KClass

/**
 * Luna's matchers and drop tables are global to the JVM, so what these tests register stays for the other tests.
 * The ids are far above any cache id, so no other test meets them.
 */
class LunaRegistriesTest {

    @BeforeEach
    fun bindLuna() {
        TestWorld.context
    }

    @Suppress("UNCHECKED_CAST")
    private fun registerHandler(event: KClass<out Event>, id: Int) {
        Matcher.get<Event, Int>(event as KClass<Event>).set(id, {}, InteractionPolicy.UNSPECIFIED_BIF)
    }

    @ParameterizedTest(name = "{0} is npc option index {1}")
    @MethodSource("npcEvents")
    fun `an npc handler is listed under the option that sends its event`(event: KClass<out Event>, index: Int) {
        registerHandler(event, NPC_KEY + index)

        assertTrue(NPC_KEY + index in LunaRegistries.npcHandlers().getValue(index))
    }

    @ParameterizedTest(name = "{0} is object option index {1}")
    @MethodSource("objectEvents")
    fun `an object handler is listed under the option that sends its event`(event: KClass<out Event>, index: Int) {
        registerHandler(event, OBJECT_KEY + index)

        assertTrue(OBJECT_KEY + index in LunaRegistries.objectHandlers().getValue(index))
    }

    @Test
    fun `npc option 2 is an attack and has no click handlers`() {
        assertEquals(setOf(0, 2, 3, 4), LunaRegistries.npcHandlers().keys)
    }

    @Test
    fun `object options 4 and 5 are never decoded and have no click handlers`() {
        assertEquals(setOf(0, 1, 2), LunaRegistries.objectHandlers().keys)
    }

    @Test
    fun `a combat definition loaded from npc_combat is read with its stats`() {
        assertEquals(
            CombatStats(maximumHit = 1, attack = 19, strength = 23, defence = 21, ranged = 1, magic = 1),
            LunaRegistries.combatStats(JAIL_GUARD),
        )
    }

    @Test
    fun `an npc missing from npc_combat has no combat stats`() {
        assertNull(LunaRegistries.combatStats(0))
    }

    @Test
    fun `a registered drop table is found`() {
        DropTableHandler.createNpc(DROP_TABLE_ID) {}

        assertTrue(LunaRegistries.hasDropTable(DROP_TABLE_ID))
    }

    @Test
    fun `an npc without a registered drop table has none`() {
        assertFalse(LunaRegistries.hasDropTable(DROP_TABLE_ID + 1))
    }

    companion object {
        private const val NPC_KEY = 900_000
        private const val OBJECT_KEY = 910_000
        private const val DROP_TABLE_ID = 920_000
        private const val JAIL_GUARD = 447

        @JvmStatic
        fun npcEvents(): List<Arguments> = listOf(
            Arguments.of(NpcFirstClickEvent::class, 0),
            Arguments.of(NpcSecondClickEvent::class, 2),
            Arguments.of(NpcThirdClickEvent::class, 3),
            Arguments.of(NpcFourthClickEvent::class, 4),
        )

        @JvmStatic
        fun objectEvents(): List<Arguments> = listOf(
            Arguments.of(ObjectFirstClickEvent::class, 0),
            Arguments.of(ObjectSecondClickEvent::class, 1),
            Arguments.of(ObjectThirdClickEvent::class, 2),
        )
    }
}
