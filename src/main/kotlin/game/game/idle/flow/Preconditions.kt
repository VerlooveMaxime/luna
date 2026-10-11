package game.idle.flow

/** What the player has on them, as the run-time checks read it; `LunaCarried` is the in-game one. */
interface Carried {

    /** The level in [skill] with boosts counted, as Luna's own checks read it. */
    fun level(skill: Int): Int

    fun holds(id: Int): Boolean

    fun wears(id: Int): Boolean

    fun bagFull(): Boolean
}

/**
 * The run-time twin of the configure screen's tool warnings (Maxime, 2026-10-11): before a kind acts, what its step
 * [StepNeeds] is checked against what the player carries, tools then bait then level, in that one order for every
 * kind, with Luna's rule per tool (a worn axe counts, a net only when carried, [ToolNeed.worn]). A step that can work
 * several ways blocks only when none is clear, on the first way's first lack.
 */
object Preconditions {

    fun check(needs: List<StepNeeds>, carried: Carried): Reason? {
        val lacks = needs.map { lacking(it, carried) }
        return if (lacks.any { it == null }) null else lacks.firstOrNull()
    }

    private fun lacking(way: StepNeeds, carried: Carried): Reason? =
        way.tools.firstNotNullOfOrNull { tool -> if (usable(tool, carried)) null else Reason.NeedsTool(tool) }
            ?: way.level?.let { if (carried.level(it.skill) < it.level) Reason.NeedsLevel(it.skill, it.level) else null }

    private fun usable(tool: ToolNeed, carried: Carried): Boolean =
        tool.allowed(carried::level).any { carried.holds(it) || tool.worn && carried.wears(it) }
}
