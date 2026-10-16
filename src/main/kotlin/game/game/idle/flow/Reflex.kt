package game.idle.flow

import engine.combat.prayer.CombatPrayer

/**
 * A reflex of a flow as the player set it up (S07c): "when X, do Y", kept beside the steps and saved with them, working
 * only while a step it is attached to runs. [id] stays put while the reflexes move, so steps attach it by id (0 until
 * the flow gives it one, [FlowIds]). Its settings by key, as text, like a step's: a missing one reads as its default
 * (keys in [ReflexKeys]).
 */
data class ReflexSettings(val id: Int = 0, val values: Map<String, String> = emptyMap()) {

    operator fun get(key: String): String? = values[key]
}

/** The settings of a reflex and their values. */
object ReflexKeys {

    /** When it fires: [HITPOINTS] (the default), [SKILL], [SPECIAL_ENERGY], or [ATTACKED]. */
    const val WHEN = "when"
    const val HITPOINTS = "hitpoints"
    const val ATTACKED = "attacked"
    const val SKILL = "skill"
    const val SPECIAL_ENERGY = "special"
    const val SKILL_NAME = "skillName"
    const val SKILL_BELOW = "skillBelow"
    val SKILL_RANGE = 1..99
    const val SKILL_WORDS = "a level from 1 to 99"
    val SKILL_RULE = Reasons.takes("Below", SKILL_WORDS)

    /** On [HITPOINTS]: below this share of full, [DEFAULT_BELOW] without one. */
    const val BELOW = "below"
    const val DEFAULT_BELOW = 50
    val BELOW_RANGE = 1..99
    const val BELOW_LABEL = "Below"
    const val BELOW_WORDS = "1 to 99 percent of hitpoints"
    val BELOW_RULE = Reasons.takes(BELOW_LABEL, BELOW_WORDS)

    /**
     * On [SPECIAL_ENERGY]: energy for this many of the worn weapon's specials, [DEFAULT_SPECIAL_FOR] without one (S13
     * part 4, Maxime 2026-10-16: counted in specials, not percent, so one reflex serves any weapon).
     */
    const val SPECIAL_FOR = "specialFor"
    const val DEFAULT_SPECIAL_FOR = 1
    val SPECIAL_FOR_RANGE = 1..10
    const val SPECIAL_FOR_LABEL = "At least"
    const val SPECIAL_FOR_WORDS = "1 to 10 specials"
    val SPECIAL_FOR_RULE = Reasons.takes(SPECIAL_FOR_LABEL, SPECIAL_FOR_WORDS)

    /**
     * What it does: [EAT] (the default), [DRINK], [PRAY], [SPECIAL_ATTACK], [STOP] the flow, or [JUMP] to the step
     * whose id [STEP] holds. [PRAYERS] lists the prayers a pray reflex keeps on, by their tab buttons ([Prayers], as a
     * list of [StepItems]).
     */
    const val DO = "do"
    const val EAT = "eat"
    const val DRINK = "drink"
    const val POTIONS = "potions"
    const val PRAY = "pray"
    const val PRAYERS = "prayers"
    const val SPECIAL_ATTACK = "special"
    const val STOP = "stop"
    const val JUMP = "jump"
    const val STEP = "step"

    /** What an eat reflex eats ([StepItems]), any food when none. */
    const val FOODS = "foods"
}

/** A reflex checked against its flow: its [number] in the flow's list (from 1), when it fires and what it does. */
data class ResolvedReflex(val number: Int, val trigger: ReflexTrigger, val action: ReflexAction)

/** When a reflex fires, the player's When. */
sealed interface ReflexTrigger {

    /** What [body] shows that makes this When hold, for the player's words; null while it does not hold. */
    fun fired(body: ReflexBody): Fired?

    data class HitpointsBelow(val percent: Int) : ReflexTrigger {

        override fun fired(body: ReflexBody): Fired? = body.health().takeIf { it.below(percent) }?.let(Fired::AtHitpoints)
    }

    /** The current level, including boosts and drains; Prayer uses its remaining points. */
    data class SkillBelow(val skill: Int, val level: Int) : ReflexTrigger {

        override fun fired(body: ReflexBody): Fired? = body.level(skill).takeIf { it < level }?.let { Fired.AtSkill(skill, it) }
    }

    /** Anything that can get at the player attacks them (S13). */
    data object UnderAttack : ReflexTrigger {

        override fun fired(body: ReflexBody): Fired? = Fired.UnderAttack.takeIf { body.underAttack() }
    }

    /**
     * The bar holds energy for [specials] of the worn weapon's special attack (S13 part 4, Maxime 2026-10-16): one
     * reflex serves any weapon; without a worn weapon whose special Luna knows it never holds.
     */
    data class SpecialEnergyFor(val specials: Int) : ReflexTrigger {

        override fun fired(body: ReflexBody): Fired? {
            val worn = body.special() ?: return null
            return body.specialEnergy().takeIf { it >= specials * worn.drain }?.let(Fired::AtSpecialEnergy)
        }
    }
}

/** What a reflex fired at, as the chat line says it: "fired at 11/20 hitpoints", "fired under attack", "fired at 70% special energy". */
sealed interface Fired {

    data class AtHitpoints(val health: Health) : Fired

    data class AtSkill(val skill: Int, val level: Int) : Fired

    data object UnderAttack : Fired

    data class AtSpecialEnergy(val percent: Int) : Fired
}

/**
 * The worn weapon's special attack as Luna's scripts register it: what one use [drain]s of the bar's 100, and whether
 * it is an [attack], landing with the next hit, rather than a boost that fires by itself (Excalibur, the dragon axes).
 */
data class WornSpecial(val drain: Int, val attack: Boolean)

/** What a reflex does, the player's Do. */
sealed interface ReflexAction {

    /** Eats the first of [foods] the bag holds, in bag order; any food when [foods] is empty. */
    data class Eat(val foods: Set<Int>) : ReflexAction

    /** Drinks the first held id in [doses]: potion list order, then lowest dose first within each potion. */
    data class Drink(val doses: List<Int>) : ReflexAction

    /**
     * Keeps [prayers] on while the When holds, lighting each that is off and can be lit, and puts out what it lit once
     * the When stops holding (S13 part 3, Maxime 2026-10-15); never two of one group, which cannot be on together.
     */
    data class Pray(val prayers: List<CombatPrayer>) : ReflexAction

    /**
     * Lights the worn weapon's special attack bar, as its button does (S13 part 4): the next hit is the special, or
     * the boost fires by itself. One shot: the bar is never lit twice.
     */
    data object SpecialAttack : ReflexAction

    /** Stops the flow, running clear first while attacked. */
    data object StopFlow : ReflexAction

    /** Goes on from the step at [index], running clear first while attacked; counts no lap. */
    data class JumpTo(val index: Int) : ReflexAction
}

/** Reflex [number] lit ([on]) or put out [prayer], for the chat line: "Autopilot: reflex 1 prays Protect from melee." */
data class PrayerChange(val number: Int, val prayer: CombatPrayer, val on: Boolean)

/** The player's hitpoints out of [full], their unboosted level. */
data class Health(val hitpoints: Int, val full: Int) {

    fun below(percent: Int): Boolean = hitpoints * 100 < full * percent

    /** As the chat box says it: "9/20". */
    fun text(): String = "$hitpoints/$full"
}
