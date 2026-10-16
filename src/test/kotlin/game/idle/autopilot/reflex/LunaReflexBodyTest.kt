package game.idle.autopilot.reflex

import api.combat.specialAttack.SpecialAttackHandler
import engine.combat.prayer.CombatPrayer.PROTECT_FROM_MELEE
import engine.combat.prayer.CombatPrayer.ROCK_SKIN
import engine.combat.prayer.CombatPrayer.THICK_SKIN
import engine.combat.status.StatusEffectType
import game.idle.autopilot.EndlessAction
import game.idle.flow.Health
import game.idle.flow.WornSpecial
import game.testworld.TestWorld
import io.luna.game.event.impl.ItemClickEvent.ItemFirstClickEvent
import io.luna.game.model.Direction
import io.luna.game.model.Position
import io.luna.game.model.item.Equipment
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.combat.SpecialAttackType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LunaReflexBodyTest {

    private val here = Position(3200, 3200)
    private val chicken = 41
    private val bread = 2309
    private val trout = 333
    private val logs = 1511
    private val deadTree = 1286

    @AfterEach
    fun resetWorld() = TestWorld.reset()

    private fun login(): Player = TestWorld.login("reflexes", here)

    private fun attackedBy(player: Player, at: Position): Npc = TestWorld.spawnNpc(chicken, at).also { it.combat.target = player }

    private fun eats(): MutableList<Pair<Int, Int>> {
        val eaten = mutableListOf<Pair<Int, Int>>()
        TestWorld.listen(ItemFirstClickEvent::class.java) { eaten += it.id to it.index }
        return eaten
    }

    @Test
    fun `health is the player's hitpoints out of their full hitpoints`() {
        val player = login()
        player.health = 6

        assertEquals(Health(6, 10), LunaReflexBody(player).health())
    }

    @Test
    fun `with no food picked the first food in the bag is found`() {
        val player = login()
        player.inventory.set(0, Item(logs))
        player.inventory.set(2, Item(bread))

        assertEquals(2, LunaReflexBody(player).foodSlot(emptySet()))
    }

    @Test
    fun `with foods picked only those are found, first in bag order`() {
        val player = login()
        player.inventory.set(1, Item(bread))
        player.inventory.set(3, Item(trout))

        assertEquals(3, LunaReflexBody(player).foodSlot(setOf(trout)))
    }

    @Test
    fun `without food there is no food slot`() {
        val player = login()
        player.inventory.add(Item(logs))

        assertNull(LunaReflexBody(player).foodSlot(emptySet()))
    }

    @Test
    fun `eating clicks the food like the client`() {
        val player = login()
        player.inventory.set(4, Item(bread))
        val eaten = eats()

        LunaReflexBody(player).eat(4)

        assertEquals(listOf(bread to 4), eaten)
    }

    @Test
    fun `an empty slot is not eaten`() {
        val eaten = eats()

        LunaReflexBody(login()).eat(4)

        assertEquals(emptyList<Pair<Int, Int>>(), eaten)
    }

    @Test
    fun `an npc fighting the player with an open way attacks them`() {
        val player = login()
        attackedBy(player, Position(3203, 3200))

        assertTrue(LunaReflexBody(player).underAttack())
    }

    @Test
    fun `an npc fighting the player but fenced in does not attack them`() {
        val player = login()
        val npc = attackedBy(player, Position(3205, 3200))
        Direction.ALL_EXCEPT_NONE.forEach { TestWorld.place(deadTree, npc.position.translate(1, it)) }

        assertFalse(LunaReflexBody(player).underAttack())
    }

    @Test
    fun `an npc fighting someone else does not attack the player`() {
        val player = login()
        attackedBy(TestWorld.login("other", Position(3204, 3200)), Position(3203, 3200))

        assertFalse(LunaReflexBody(player).underAttack())
    }

    @Test
    fun `a dead npc does not attack`() {
        val player = login()
        attackedBy(player, Position(3203, 3200)).health = 0

        assertFalse(LunaReflexBody(player).underAttack())
    }

    @Test
    fun `with nothing attacking the player stays put and keeps their run toggle`() {
        val player = login()

        LunaReflexBody(player).flee()
        TestWorld.tick()

        assertEquals(listOf(true, false), listOf(player.walking.isEmpty, player.isRunning))
    }

    @Test
    fun `fleeing runs from the attacker`() {
        val player = login()
        attackedBy(player, Position(3199, 3200))

        LunaReflexBody(player).flee()
        TestWorld.tick()

        assertFalse(player.walking.isEmpty)
    }

    @Test
    fun `fleeing runs, the run toggle on`() {
        val player = login()
        attackedBy(player, Position(3199, 3200))

        LunaReflexBody(player).flee()

        assertTrue(player.isRunning)
    }

    @Test
    fun `with no run energy the flight walks, the run toggle left alone`() {
        val player = login().apply { setRunEnergy(0.0, true) }
        attackedBy(player, Position(3199, 3200))

        LunaReflexBody(player).flee()

        assertFalse(player.isRunning)
    }

    @Test
    fun `running is turned on once a flight, so a flight out of energy walks on`() {
        val player = login()
        attackedBy(player, Position(3199, 3200))
        LunaReflexBody(player).flee()
        player.isRunning = false

        LunaReflexBody(player).flee()

        assertFalse(player.isRunning)
    }

    @Test
    fun `once calm the run toggle is back as it was before the flight`() {
        val player = login()
        attackedBy(player, Position(3199, 3200))
        LunaReflexBody(player).flee()
        LunaReflexBody(player).flee()

        LunaReflexBody(player).calm()

        assertFalse(player.isRunning)
    }

    @Test
    fun `calm without a flight leaves the run toggle alone`() {
        val player = login().apply { isRunning = true }

        LunaReflexBody(player).calm()

        assertTrue(player.isRunning)
    }

    /** A player with base Prayer [level] and as many points. */
    private fun praying(level: Int): Player = login().also { player ->
        val prayer = player.skill(Skill.PRAYER)
        prayer.staticLevel = level
        prayer.level = level
    }

    @Test
    fun `a prayer is on once Luna's set has it`() {
        val player = praying(1)
        val body = LunaReflexBody(player)
        val before = body.praying(THICK_SKIN)

        player.combat.prayers.activate(THICK_SKIN)

        assertEquals(listOf(false, true), listOf(before, body.praying(THICK_SKIN)))
    }

    @Test
    fun `praying lights the prayer through Luna's set and says it is on`() {
        val player = praying(1)

        assertTrue(LunaReflexBody(player).pray(THICK_SKIN))
        assertTrue(player.combat.prayers.isActive(THICK_SKIN))
    }

    @Test
    fun `praying a prayer already on leaves it on`() {
        val player = praying(1)
        player.combat.prayers.activate(THICK_SKIN)

        assertTrue(LunaReflexBody(player).pray(THICK_SKIN))
        assertTrue(player.combat.prayers.isActive(THICK_SKIN))
    }

    @Test
    fun `a prayer Luna turns down is not on afterwards`() {
        val player = praying(1)

        assertFalse(LunaReflexBody(player).pray(PROTECT_FROM_MELEE))
    }

    @Test
    fun `a prayer can be lit with the base level, points and no disabling`() {
        assertTrue(LunaReflexBody(praying(43)).canPray(PROTECT_FROM_MELEE))
    }

    @Test
    fun `a prayer above the base level cannot be lit, however boosted`() {
        val player = praying(42)
        player.skill(Skill.PRAYER).level = 60

        assertFalse(LunaReflexBody(player).canPray(PROTECT_FROM_MELEE))
    }

    @Test
    fun `without prayer points no prayer can be lit`() {
        val player = praying(43)
        player.skill(Skill.PRAYER).level = 0

        assertFalse(LunaReflexBody(player).canPray(THICK_SKIN))
    }

    @Test
    fun `a locked player, teleporting or dying, lights no prayer`() {
        val player = praying(43)
        player.lock()

        assertFalse(LunaReflexBody(player).canPray(THICK_SKIN))
    }

    @Test
    fun `while a dragon scimitar's hit disables prayers none can be lit`() {
        val player = praying(43)
        player.status.add(checkNotNull(StatusEffectType.DRAGON_SCIMITAR.loadFunction(player)))

        assertFalse(LunaReflexBody(player).canPray(THICK_SKIN))
    }

    @Test
    fun `putting out a prayer that is on says it was`() {
        val player = praying(10)
        player.combat.prayers.activate(ROCK_SKIN)

        assertTrue(LunaReflexBody(player).stopPraying(ROCK_SKIN))
        assertFalse(player.combat.prayers.isActive(ROCK_SKIN))
    }

    @Test
    fun `putting out a prayer that is off says it was not`() {
        assertFalse(LunaReflexBody(praying(10)).stopPraying(ROCK_SKIN))
    }

    @Test
    fun `fleeing stops what the player was doing`() {
        val player = login()
        attackedBy(player, Position(3199, 3200))
        player.submitAction(EndlessAction(player))

        LunaReflexBody(player).flee()
        TestWorld.tick()

        assertNull(player.actions.first(EndlessAction::class.java))
    }

    @Test
    fun `a player with hitpoints left is alive`() {
        assertFalse(LunaReflexBody(login()).dead())
    }

    @Test
    fun `a player with no hitpoints left is dead`() {
        val player = login()
        player.health = 0

        assertTrue(LunaReflexBody(player).dead())
    }

    @Test
    fun `skill levels include a boost and depleted prayer points`() {
        val player = login()
        player.skill(2).level = 4
        player.skill(5).level = 0
        val body = LunaReflexBody(player)

        assertEquals(listOf(4, 0), listOf(body.level(2), body.level(5)))
    }

    @Test
    fun `potions follow list order before bag order`() {
        val player = login()
        player.inventory.set(0, Item(113))
        player.inventory.set(3, Item(2440))

        assertEquals(3, LunaReflexBody(player).potionSlot(listOf(161, 159, 157, 2440, 119, 117, 115, 113)))
    }

    @Test
    fun `the lowest held dose is drunk before a full vial`() {
        val player = login()
        player.inventory.set(0, Item(113))
        player.inventory.set(3, Item(117))
        player.inventory.set(5, Item(119))

        assertEquals(5, LunaReflexBody(player).potionSlot(listOf(119, 117, 115, 113)))
    }

    @Test
    fun `an absent first potion falls through to the next kind`() {
        val player = login()
        player.inventory.set(2, Item(113))

        assertEquals(2, LunaReflexBody(player).potionSlot(listOf(161, 159, 157, 2440, 119, 117, 115, 113)))
    }

    @Test
    fun `no matching potion means drinking cannot be done`() {
        val player = login()
        player.inventory.set(0, Item(229))

        assertNull(LunaReflexBody(player).potionSlot(listOf(119, 117, 115, 113)))
    }

    @Test
    fun `drinking clicks the potion like the client`() {
        val player = login()
        player.inventory.set(4, Item(113))
        val clicked = eats()

        LunaReflexBody(player).drink(4)

        assertEquals(listOf(113 to 4), clicked)
    }

    @Test
    fun `a potion slot emptied before drinking is ignored`() {
        val clicked = eats()

        LunaReflexBody(login()).drink(4)

        assertEquals(emptyList<Pair<Int, Int>>(), clicked)
    }

    private val darklight = 6746
    private val excalibur = 35

    /** The dragon halberd: no test registers its special, as `TestWorld` loads no plugin. */
    private val unregisteredHalberd = 3204

    /** A player wielding [weapon], the combat weapon refreshed as the equipment plugin does on a real server. */
    private fun wielding(weapon: Int): Player = login().also { player ->
        player.equipment.set(Equipment.WEAPON, Item(weapon))
        player.combat.weapon.refreshWeapon(player.combat.weapon.styleDef.stance)
    }

    /** Darklight's and Excalibur's specials as their scripts register them, since `TestWorld` loads no plugin. */
    private fun registerSpecials() {
        SpecialAttackHandler.attack(SpecialAttackType.DARKLIGHT, drain = 50) {}
        SpecialAttackHandler.activation(SpecialAttackType.EXCALIBUR, drain = 100) {}
    }

    @Test
    fun `special energy is the bar's`() {
        val player = login()
        player.combat.specialBar.setEnergy(40)

        assertEquals(40, LunaReflexBody(player).specialEnergy())
    }

    @Test
    fun `without a weapon there is no special`() {
        assertNull(LunaReflexBody(login()).special())
    }

    @Test
    fun `a weapon whose special is not registered has none`() {
        assertNull(LunaReflexBody(wielding(unregisteredHalberd)).special())
    }

    @Test
    fun `a worn weapon's registered special says its drain and that it lands with a hit`() {
        registerSpecials()

        assertEquals(WornSpecial(50, attack = true), LunaReflexBody(wielding(darklight)).special())
    }

    @Test
    fun `a boost special fires by itself`() {
        registerSpecials()

        assertEquals(WornSpecial(100, attack = false), LunaReflexBody(wielding(excalibur)).special())
    }

    @Test
    fun `a locked player, teleporting or dying, keeps the weapon's special as a fact`() {
        registerSpecials()
        val player = wielding(darklight)
        player.lock()

        assertEquals(WornSpecial(50, attack = true), LunaReflexBody(player).special())
    }

    @Test
    fun `the bar is free until Luna lights it`() {
        registerSpecials()
        val player = wielding(darklight)
        val body = LunaReflexBody(player)
        val before = body.specialBarFree()

        player.combat.specialBar.toggleOn()

        assertEquals(listOf(true, false), listOf(before, body.specialBarFree()))
    }

    @Test
    fun `a bar locked by a special under way is not free`() {
        val player = login()
        player.combat.specialBar.isLocked = true

        assertFalse(LunaReflexBody(player).specialBarFree())
    }

    @Test
    fun `a locked player, teleporting or dying, has no free bar`() {
        val player = login()
        player.lock()

        assertFalse(LunaReflexBody(player).specialBarFree())
    }

    @Test
    fun `fighting means a combat target`() {
        val player = login()
        val body = LunaReflexBody(player)
        val before = body.fighting()

        player.combat.target = TestWorld.spawnNpc(chicken, here.translate(1, 0))

        assertEquals(listOf(false, true), listOf(before, body.fighting()))
    }

    @Test
    fun `using the special lights the bar through Luna and says it is lit`() {
        registerSpecials()
        val player = wielding(darklight)

        assertTrue(LunaReflexBody(player).useSpecial())
        assertTrue(player.combat.specialBar.isActivated)
    }

    @Test
    fun `a special Luna turns down is not lit afterwards`() {
        assertFalse(LunaReflexBody(wielding(unregisteredHalberd)).useSpecial())
    }

    @Test
    fun `a locked bar is not lit however it is asked`() {
        registerSpecials()
        val player = wielding(darklight)
        player.combat.specialBar.isLocked = true

        assertFalse(LunaReflexBody(player).useSpecial())
    }

    @Test
    fun `with energy short of the drain Luna leaves the bar off`() {
        registerSpecials()
        val player = wielding(darklight)
        player.combat.specialBar.setEnergy(40)

        assertFalse(LunaReflexBody(player).useSpecial())
    }
}
