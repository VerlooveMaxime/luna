package game.idle.content.combat

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CombatSourcesTest {

    private val guardConfig = """
        [jailguard]
        name=Jail guard
        respawnrate=60
        hitpoints=32
        attack=19
        strength=23
        defence=21
        param=attackrate,5
        param=attackbonus,9
        param=strengthbonus,5
        param=magicattack,2
        param=rangeattack,3
        param=stabdefence,8
        param=slashdefence,9
        param=crushdefence,10
        param=magicdefence,4
        param=rangedefence,6
        param=attack_anim,human_blunt_pound
        param=defend_anim,human_blunt_block
        param=death_anim,human_death
        huntmode=aggressive_melee
    """.trimIndent()

    private val ratConfig = "[newbiegiantrat]\nname=Giant rat\nhitpoints=3\n"

    private val sequences = mapOf("human_blunt_pound" to 401, "human_blunt_block" to 404, "human_death" to 836)

    private fun sources(
        lostCity377: LostCityBranch =
            branch(mapOf(447 to "jailguard", 950 to "newbiegiantrat"), guardConfig + "\n" + ratConfig, sequences),
        lostCity289: LostCityBranch = branch(),
        osrs: List<OsrsMonster> = emptyList(),
    ) = CombatSources(lostCity377, lostCity289, OsrsMonsters(osrs))

    private fun guard(config: String = guardConfig, osrs: List<OsrsMonster> = emptyList()) =
        sources(lostCity377 = branch(mapOf(447 to "jailguard"), config, sequences), osrs = osrs).find(JAIL_GUARD)

    @Test
    fun `LostCity 377 is read by id`() {
        assertEquals(CombatSource.LOSTCITY_377 to 32, guard()?.let { it.source to it.hitpoints })
    }

    @Test
    fun `levels come in Luna's order`() {
        assertEquals(listOf(19, 23, 21, 1, 1), guard()?.skills)
    }

    @Test
    fun `a level the config leaves out is 1`() {
        assertEquals(listOf(1, 1, 1, 1, 1), sources().find(GIANT_RAT)?.skills)
    }

    @Test
    fun `the attack rate is the attack speed`() {
        assertEquals(5, guard()?.attackSpeed)
    }

    @Test
    fun `no attack rate is LostCity's 4`() {
        assertEquals(4, sources().find(GIANT_RAT)?.attackSpeed)
    }

    @Test
    fun `the respawn rate is in ticks`() {
        assertEquals(60, guard()?.respawnTicks)
    }

    @Test
    fun `no respawn rate is LostCity's 100 ticks`() {
        assertEquals(100, sources().find(GIANT_RAT)?.respawnTicks)
    }

    @Test
    fun `attack bonuses map to Luna's three`() {
        assertEquals(listOf(9, 2, 3), guard()?.let { listOf(it.attackBonus, it.magicBonus, it.rangedBonus) })
    }

    @Test
    fun `defences then strength bonus make Luna's six bonuses`() {
        assertEquals(listOf(8, 9, 10, 4, 6, 5), guard()?.bonuses)
    }

    @Test
    fun `bonuses the config leaves out are 0`() {
        assertEquals(listOf(0, 0, 0, 0, 0, 0), sources().find(GIANT_RAT)?.bonuses)
    }

    @Test
    fun `animations map through the 377 sequence ids`() {
        val guard = guard()

        assertEquals(
            listOf(401, 404, 836),
            listOf(guard?.attackAnimation, guard?.defenceAnimation, guard?.deathAnimation),
        )
    }

    @Test
    fun `an animation the 377 pack lacks stays unknown`() {
        assertNull(guard(guardConfig.replace("human_death", "human_death_old"))?.deathAnimation)
    }

    @Test
    fun `an entry without hitpoints falls back to 289 through the 377 debugname`() {
        val older = branch(configs = "[jailguard]\nname=Jail guard\nhitpoints=30\n")
        val unported = branch(mapOf(447 to "jailguard"), "[jailguard]\nname=Jail guard\n")
        val found = sources(lostCity377 = unported, lostCity289 = older).find(JAIL_GUARD)

        assertEquals(CombatSource.LOSTCITY_289 to 30, found?.let { it.source to it.hitpoints })
    }

    @Test
    fun `a 289 entry under another name is not used`() {
        val older = branch(configs = "[jailguard]\nname=Unferth\nhitpoints=30\n")

        assertNull(sources(lostCity377 = branch(mapOf(447 to "jailguard")), lostCity289 = older).find(JAIL_GUARD))
    }

    @Test
    fun `an id unknown to LostCity goes to OSRS`() {
        assertEquals(CombatSource.OSRS, fromOsrs(osrsMonster())?.source)
    }

    @Test
    fun `no source has the npc`() {
        assertNull(sources(lostCity377 = branch()).find(JAIL_GUARD))
    }

    @Test
    fun `OSRS gives the max hit when it has the same monster`() {
        assertEquals(
            MaximumHit(7, "OSRS, same stats as LostCity"),
            guard(osrs = listOf(osrsMonster(maximumHit = 7)))?.maximumHit,
        )
    }

    @Test
    fun `an OSRS monster with other stats leaves the melee formula`() {
        assertEquals(
            MaximumHit(3, "LostCity melee formula"),
            guard(osrs = listOf(osrsMonster(hitpoints = 40, maximumHit = 7)))?.maximumHit,
        )
    }

    @Test
    fun `an OSRS monster with other levels leaves the melee formula`() {
        assertEquals(3, guard(osrs = listOf(osrsMonster(attack = 30, maximumHit = 7)))?.maximumHit?.value)
    }

    @Test
    fun `an OSRS monster without a max hit leaves the melee formula`() {
        assertEquals(3, guard(osrs = listOf(osrsMonster(maximumHit = null)))?.maximumHit?.value)
    }

    @Test
    fun `a ranged npc gets the ranged formula`() {
        val archer = guardConfig.replace("defence=21", "defence=21\nranged=40") +
            "\nparam=damagetype,^ranged_style\nparam=rangebonus,20"

        assertEquals(MaximumHit(6, "LostCity ranged formula"), guard(archer)?.maximumHit)
    }

    @Test
    fun `a magic npc has no derived max hit`() {
        assertNull(guard(guardConfig + "\nparam=damagetype,^magic_style")?.maximumHit?.value)
    }

    @Test
    fun `no hunt mode is no aggression`() {
        assertEquals(AggressionImport.Known(null), sources().find(GIANT_RAT)?.aggression)
    }

    @Test
    fun `an aggressive hunt mode always attacks`() {
        assertEquals(AggressionImport.Known(Aggression.ALWAYS), guard()?.aggression)
    }

    @Test
    fun `the cowardly hunt mode follows the combat level rule`() {
        assertEquals(
            AggressionImport.Known(Aggression.BY_COMBAT_LEVEL),
            guard(guardConfig.replace("aggressive_melee", "cowardly"))?.aggression,
        )
    }

    @Test
    fun `another hunt mode is reported, not translated`() {
        val tackler = guardConfig.replace("aggressive_melee", "gnomeball_tackler")

        assertEquals(AggressionImport.Unrecognised("gnomeball_tackler"), guard(tackler)?.aggression)
    }

    private fun fromOsrs(monster: OsrsMonster) =
        sources(lostCity377 = branch(), osrs = listOf(monster)).find(JAIL_GUARD)

    @Test
    fun `an OSRS monster gives its stats in Luna's order`() {
        assertEquals(
            listOf(listOf(19, 23, 21, 1, 1), listOf(8, 9, 10, 4, 9, 5)),
            fromOsrs(osrsMonster())?.let { listOf(it.skills, it.bonuses) },
        )
    }

    @Test
    fun `an OSRS monster gives its max hit, attack speed and attack bonuses`() {
        assertEquals(
            listOf(MaximumHit(3, "OSRS"), 5, 9, 0, 0),
            fromOsrs(osrsMonster())?.let {
                listOf(it.maximumHit, it.attackSpeed, it.attackBonus, it.magicBonus, it.rangedBonus)
            },
        )
    }

    @Test
    fun `an OSRS monster without an attack speed attacks every 4 ticks`() {
        assertEquals(4, fromOsrs(osrsMonster(attackSpeed = null))?.attackSpeed)
    }

    @Test
    fun `an aggressive OSRS monster follows the combat level rule`() {
        assertEquals(AggressionImport.Known(Aggression.BY_COMBAT_LEVEL), fromOsrs(osrsMonster())?.aggression)
    }

    @Test
    fun `a peaceful OSRS monster has no aggression`() {
        assertEquals(AggressionImport.Known(null), fromOsrs(osrsMonster(aggressive = false))?.aggression)
    }

    @Test
    fun `OSRS gives no respawn time or animations`() {
        assertEquals(
            listOf(null, null, null, null),
            fromOsrs(osrsMonster())?.let {
                listOf(it.respawnTicks, it.attackAnimation, it.defenceAnimation, it.deathAnimation)
            },
        )
    }

    @Test
    fun `the jail guard's melee max hit is 3`() {
        assertEquals(3, CombatSources.formula(level = 23, bonus = 5))
    }
}
