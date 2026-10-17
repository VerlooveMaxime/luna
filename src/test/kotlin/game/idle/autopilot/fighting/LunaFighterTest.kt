package game.idle.autopilot.fighting

import game.idle.autopilot.Candidate
import game.idle.autopilot.EndlessAction
import game.idle.flow.WorkSpot
import game.idle.location.Area
import game.idle.location.Tile
import game.idle.movement.Doorways
import game.testworld.TestWorld
import io.luna.game.event.impl.NpcClickEvent.AttackNpcEvent
import io.luna.game.model.Direction
import io.luna.game.model.Position
import io.luna.game.model.item.Equipment
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.combat.CombatStance
import io.luna.game.model.mob.combat.damage.CombatDamageRequest
import io.luna.game.model.mob.overlay.StandardInterface
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LunaFighterTest {

    private val anchor = Position(3200, 3200)
    private val area = Area(Tile(3200, 3200), radius = 10)
    private val chicken = 41
    private val cow = 81
    private val runescapeGuide = 945
    private val chickens = FightTarget("chicken", setOf(chicken, runescapeGuide), "Chicken", 1..1)
    private val shortbow = 841

    @AfterEach
    fun resetWorld() = TestWorld.reset()

    private fun login(position: Position = anchor): Player = TestWorld.login("fighter", position)

    private fun fighter(player: Player) = LunaFighter(player, chickens, area, Doorways.NONE)

    /** [npc] dies with [killer]'s hit the last it took, or nobody's hit when null. */
    private fun kill(npc: Npc, killer: Player?) {
        if (killer != null) npc.combat.lastDamageReceived = CombatDamageRequest.zero(killer, npc).resolve()
        npc.health = 0
    }

    private fun attacked(player: Player, npc: Npc): LunaFighter {
        val fighter = fighter(player)
        fighter.attack(Candidate(npc.index, npc.position, distance = 0, usableFromHere = true, approach = player.position))
        return fighter
    }

    private fun attackClicks(): MutableList<Int> {
        val clicked = mutableListOf<Int>()
        TestWorld.listen(AttackNpcEvent::class.java) { clicked += it.targetNpc.id }
        return clicked
    }

    @Test
    fun `an idle player is not busy`() {
        assertFalse(fighter(login()).isBusy())
    }

    @Test
    fun `a walking player is busy`() {
        val player = login()
        player.walking.addStep(Direction.EAST)

        assertTrue(fighter(player).isBusy())
    }

    @Test
    fun `a player fighting a live npc is busy`() {
        val player = login()
        player.combat.target = TestWorld.spawnNpc(chicken, Position(3201, 3200))

        assertTrue(fighter(player).isBusy())
    }

    @Test
    fun `a player whose npc died is not busy`() {
        val player = login()
        val npc = TestWorld.spawnNpc(chicken, Position(3201, 3200))
        player.combat.target = npc
        kill(npc, player)

        assertFalse(fighter(player).isBusy())
    }

    @Test
    fun `an open window keeps the player busy`() {
        val player = login()
        player.overlays.open(StandardInterface(5292))

        assertTrue(fighter(player).isBusy())
    }

    @Test
    fun `an npc of the target in reach is a candidate`() {
        val player = login()
        val npc = TestWorld.spawnNpc(chicken, Position(3201, 3200))

        assertEquals(listOf(Candidate(npc.index, npc.position, distance = 0, usableFromHere = true, approach = anchor)), fighter(player).look().targets)
    }

    @Test
    fun `with a bow an npc in sight and range is in reach`() {
        val player = login()
        player.equipment.set(Equipment.WEAPON, Item(shortbow))
        player.combat.weapon.refreshWeapon(CombatStance.ACCURATE)
        TestWorld.spawnNpc(chicken, Position(3205, 3200))

        assertTrue(fighter(player).look().targets.single().usableFromHere)
    }

    @Test
    fun `npcs of other kinds are not candidates`() {
        val player = login()
        TestWorld.spawnNpc(cow, Position(3201, 3200))

        assertEquals(emptyList<Candidate>(), fighter(player).look().targets)
    }

    @Test
    fun `a dead npc is not a candidate`() {
        val player = login()
        kill(TestWorld.spawnNpc(chicken, Position(3201, 3200)), killer = null)

        assertEquals(emptyList<Candidate>(), fighter(player).look().targets)
    }

    @Test
    fun `an npc that cannot be attacked is not a candidate`() {
        val player = login()
        TestWorld.spawnNpc(runescapeGuide, Position(3201, 3200))

        assertEquals(emptyList<Candidate>(), fighter(player).look().targets)
    }

    @Test
    fun `an npc someone else fights is not a candidate`() {
        val player = login()
        val npc = TestWorld.spawnNpc(chicken, Position(3201, 3200))
        npc.combat.target = TestWorld.login("other", Position(3202, 3200))

        assertEquals(emptyList<Candidate>(), fighter(player).look().targets)
    }

    @Test
    fun `an npc fighting the player is a candidate that attacks them`() {
        val player = login()
        val npc = TestWorld.spawnNpc(chicken, Position(3201, 3200))
        npc.combat.target = player

        assertTrue(fighter(player).look().targets.single().attacking)
    }

    @Test
    fun `an npc of another kind attacking the player is a candidate, to fight back`() {
        val player = login()
        val npc = TestWorld.spawnNpc(cow, Position(3201, 3200))
        npc.combat.target = player

        assertEquals(listOf(npc.index to true), fighter(player).look().targets.map { it.id to it.attacking })
    }

    @Test
    fun `attacking clicks the npc like the client`() {
        val player = login()
        val clicked = attackClicks()

        attacked(player, TestWorld.spawnNpc(chicken, Position(3201, 3200)))
        TestWorld.tick()

        assertEquals(listOf(chicken), clicked)
    }

    @Test
    fun `an npc gone since the look is not attacked`() {
        val player = login()
        val npc = TestWorld.spawnNpc(chicken, Position(3201, 3200))
        val clicked = attackClicks()
        TestWorld.world.npcs.remove(npc)

        attacked(player, npc)
        TestWorld.tick()

        assertEquals(emptyList<Int>(), clicked)
    }

    @Test
    fun `an npc that moved since the look is still attacked`() {
        val player = login()
        val npc = TestWorld.spawnNpc(chicken, Position(3201, 3200))
        val clicked = attackClicks()

        fighter(player).attack(Candidate(npc.index, Position(3202, 3200), distance = 0, usableFromHere = true, approach = anchor))
        TestWorld.tick()

        assertEquals(listOf(chicken), clicked)
    }

    @Test
    fun `another kind of npc where the target was is not attacked`() {
        val player = login()
        val clicked = attackClicks()

        attacked(player, TestWorld.spawnNpc(cow, Position(3201, 3200)))
        TestWorld.tick()

        assertEquals(emptyList<Int>(), clicked)
    }

    @Test
    fun `an npc of another kind attacking the player is attacked back`() {
        val player = login()
        val cowAttacking = TestWorld.spawnNpc(cow, Position(3201, 3200)).also { it.combat.target = player }
        val clicked = attackClicks()

        attacked(player, cowAttacking)
        TestWorld.tick()

        assertEquals(listOf(cow), clicked)
    }

    @Test
    fun `a dead npc is not attacked`() {
        val player = login()
        val npc = TestWorld.spawnNpc(chicken, Position(3201, 3200))
        kill(npc, killer = null)
        val clicked = attackClicks()

        attacked(player, npc)
        TestWorld.tick()

        assertEquals(emptyList<Int>(), clicked)
    }

    @Test
    fun `a walk back goes to the area's anchor`() {
        assertEquals(Tile(3200, 3200), fighter(login(Position(3220, 3200))).anchor())
    }

    @Test
    fun `an attacked npc that died with the player's hit last is a kill`() {
        val player = login()
        val npc = TestWorld.spawnNpc(chicken, Position(3201, 3200))
        val fighter = attacked(player, npc)

        kill(npc, player)

        assertEquals(1, fighter.kills())
    }

    @Test
    fun `an attacked npc still alive is no kill yet`() {
        val player = login()
        val fighter = attacked(player, TestWorld.spawnNpc(chicken, Position(3201, 3200)))

        assertEquals(0, fighter.kills())
    }

    @Test
    fun `a kill counts once`() {
        val player = login()
        val npc = TestWorld.spawnNpc(chicken, Position(3201, 3200))
        val fighter = attacked(player, npc)
        kill(npc, player)
        fighter.kills()

        assertEquals(1, fighter.kills())
    }

    @Test
    fun `before any kill nothing is dying`() {
        assertFalse(fighter(login()).lastKillDying())
    }

    @Test
    fun `a kill is dying until its drop lands`() {
        val player = login()
        val npc = TestWorld.spawnNpc(chicken, Position(3201, 3200))
        val fighter = attacked(player, npc)

        kill(npc, player)

        assertTrue(fighter.lastKillDying())
    }

    @Test
    fun `a kill taken out of the world is dying no more`() {
        val player = login()
        val npc = TestWorld.spawnNpc(chicken, Position(3201, 3200))
        val fighter = attacked(player, npc)
        kill(npc, player)
        fighter.kills()

        TestWorld.world.npcs.remove(npc)

        assertFalse(fighter.lastKillDying())
    }

    @Test
    fun `a kill whose death is over is dying no more`() {
        val player = login()
        val npc = TestWorld.spawnNpc(chicken, Position(3201, 3200))
        val fighter = attacked(player, npc)
        kill(npc, player)
        fighter.kills()

        TestWorld.tick(times = 8)

        assertFalse(fighter.lastKillDying())
    }

    @Test
    fun `an npc someone else killed is no kill`() {
        val player = login()
        val npc = TestWorld.spawnNpc(chicken, Position(3201, 3200))
        val fighter = attacked(player, npc)

        kill(npc, TestWorld.login("other", Position(3202, 3200)))

        assertEquals(0, fighter.kills())
    }

    @Test
    fun `an npc that died with nobody's hit on it is no kill`() {
        val player = login()
        val npc = TestWorld.spawnNpc(chicken, Position(3201, 3200))
        val fighter = attacked(player, npc)

        kill(npc, killer = null)

        assertEquals(0, fighter.kills())
    }

    @Test
    fun `an npc of the target the player fought back is a kill`() {
        val player = login()
        val npc = TestWorld.spawnNpc(chicken, Position(3201, 3200))
        val fighter = fighter(player)
        player.combat.target = npc
        fighter.isBusy()

        kill(npc, player)

        assertEquals(1, fighter.kills())
    }

    @Test
    fun `an npc of another kind the step attacked back is no kill`() {
        val player = login()
        val cowAttacking = TestWorld.spawnNpc(cow, Position(3201, 3200)).also { it.combat.target = player }
        val fighter = attacked(player, cowAttacking)

        kill(cowAttacking, player)

        assertEquals(0, fighter.kills())
    }

    @Test
    fun `an npc of another kind the player fought is no kill`() {
        val player = login()
        val npc = TestWorld.spawnNpc(cow, Position(3201, 3200))
        val fighter = fighter(player)
        player.combat.target = npc
        fighter.isBusy()

        kill(npc, player)

        assertEquals(0, fighter.kills())
    }

    @Test
    fun `interrupting breaks off the fight in progress`() {
        val player = login()
        player.submitAction(EndlessAction(player))

        fighter(player).interrupt()
        TestWorld.tick()

        assertEquals(null, player.actions.first(EndlessAction::class.java))
    }

    @Test
    fun `a fight step fights through a Luna fighter`() {
        val step = FightStep(chickens, radius = 10, WorkSpot.RunTile)

        assertInstanceOf(FightWork::class.java, step.work(login(), Tile(3200, 3200)))
    }
}
