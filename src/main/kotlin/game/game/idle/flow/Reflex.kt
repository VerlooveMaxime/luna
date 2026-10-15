package game.idle.flow

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

    /** When it fires: [HITPOINTS] (the default) or [ATTACKED] (S13). */
    const val WHEN = "when"
    const val HITPOINTS = "hitpoints"
    const val ATTACKED = "attacked"

    /** On [HITPOINTS]: below this share of full, [DEFAULT_BELOW] without one. */
    const val BELOW = "below"
    const val DEFAULT_BELOW = 50
    val BELOW_RANGE = 1..99
    const val BELOW_LABEL = "Below"
    const val BELOW_WORDS = "1 to 99 percent of hitpoints"
    val BELOW_RULE = Reasons.takes(BELOW_LABEL, BELOW_WORDS)

    /** What it does: [EAT] (the default), [STOP] the flow, or [JUMP] to the step whose id [STEP] holds (S13). */
    const val DO = "do"
    const val EAT = "eat"
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

    /** Anything that can get at the player attacks them (S13). */
    data object UnderAttack : ReflexTrigger {

        override fun fired(body: ReflexBody): Fired? = Fired.UnderAttack.takeIf { body.underAttack() }
    }
}

/** What a reflex fired at, as the chat line says it: "fired at 11/20 hitpoints", "fired under attack". */
sealed interface Fired {

    data class AtHitpoints(val health: Health) : Fired

    data object UnderAttack : Fired
}

/** What a reflex does, the player's Do. */
sealed interface ReflexAction {

    /** Eats the first of [foods] the bag holds, in bag order; any food when [foods] is empty. */
    data class Eat(val foods: Set<Int>) : ReflexAction

    /** Stops the flow, running clear first while attacked. */
    data object StopFlow : ReflexAction

    /** Goes on from the step at [index], running clear first while attacked; counts no lap. */
    data class JumpTo(val index: Int) : ReflexAction
}

/** The player's hitpoints out of [full], their unboosted level. */
data class Health(val hitpoints: Int, val full: Int) {

    fun below(percent: Int): Boolean = hitpoints * 100 < full * percent

    /** As the chat box says it: "9/20". */
    fun text(): String = "$hitpoints/$full"
}
