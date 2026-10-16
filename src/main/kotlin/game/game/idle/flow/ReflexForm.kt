package game.idle.flow

import engine.combat.prayer.CombatPrayer
import game.idle.flow.ReflexKeys.ATTACKED
import game.idle.flow.ReflexKeys.BELOW
import game.idle.flow.ReflexKeys.BELOW_LABEL
import game.idle.flow.ReflexKeys.BELOW_RANGE
import game.idle.flow.ReflexKeys.BELOW_RULE
import game.idle.flow.ReflexKeys.DEFAULT_BELOW
import game.idle.flow.ReflexKeys.DEFAULT_SPECIAL_FOR
import game.idle.flow.ReflexKeys.DO
import game.idle.flow.ReflexKeys.DRINK
import game.idle.flow.ReflexKeys.EAT
import game.idle.flow.ReflexKeys.FOODS
import game.idle.flow.ReflexKeys.HITPOINTS
import game.idle.flow.ReflexKeys.JUMP
import game.idle.flow.ReflexKeys.POTIONS
import game.idle.flow.ReflexKeys.PRAY
import game.idle.flow.ReflexKeys.PRAYERS
import game.idle.flow.ReflexKeys.SKILL
import game.idle.flow.ReflexKeys.SKILL_BELOW
import game.idle.flow.ReflexKeys.SKILL_NAME
import game.idle.flow.ReflexKeys.SKILL_RANGE
import game.idle.flow.ReflexKeys.SKILL_RULE
import game.idle.flow.ReflexKeys.SPECIAL_ATTACK
import game.idle.flow.ReflexKeys.SPECIAL_ENERGY
import game.idle.flow.ReflexKeys.SPECIAL_FOR
import game.idle.flow.ReflexKeys.SPECIAL_FOR_LABEL
import game.idle.flow.ReflexKeys.SPECIAL_FOR_RANGE
import game.idle.flow.ReflexKeys.SPECIAL_FOR_RULE
import game.idle.flow.ReflexKeys.STEP
import game.idle.flow.ReflexKeys.STOP
import game.idle.flow.ReflexKeys.WHEN
import game.idle.flow.option.GameNames
import game.idle.flow.option.JumpTargetOptions
import game.idle.flow.option.OptionFacts
import game.idle.flow.option.OptionIcon
import game.idle.flow.option.OptionSource
import game.idle.flow.option.SkillOptions
import game.idle.flow.option.StepOption
import game.idle.flow.option.StepTarget
import io.luna.game.model.mob.Skill

/**
 * A reflex as the builder shows it (S07c): its screen's fields, header and warnings, and the sentence and picture its
 * row shows. Its screen edits it as settings like a step's ([settings]), so the configure screen's toggles, typed
 * fields, lists and searches work on it unchanged: When on the left with its value under it, Do on the right with what
 * it needs under it (S13, Maxime 2026-10-15). An eat reflex picks from [foods]; [portions] gives every portion of the
 * food each edible id belongs to. Drink picks from [potions], with every dose in [doses]; Pray from [prayers] (S13 part
 * 3); a jump picks one of the flow's steps, worded with [types]; items are named with [names]. Special attack picks
 * nothing: [drains] gives, for every weapon whose special Luna's scripts register, what one use drains of the bar,
 * read when asked since those scripts may load after this form (S13 part 4).
 */
class ReflexForm(
    private val foods: OptionSource,
    private val portions: Map<Int, Set<Int>>,
    private val types: StepTypes,
    private val names: GameNames,
    private val potions: OptionSource,
    private val doses: Map<Int, List<Int>>,
    private val prayers: OptionSource,
    private val drains: () -> Map<Int, Int>,
) {

    /** [reflex] as the settings its screen edits. */
    fun settings(reflex: ReflexSettings): StepSettings = StepSettings(KIND, reflex.values, reflex.id)

    /** The reflex [settings] hold. */
    fun reflex(settings: StepSettings): ReflexSettings = ReflexSettings(settings.id, settings.values)

    /** When and its values on the left; Do and its food, potions, prayers or jump target on the right. */
    fun fields(steps: List<StepSettings>): List<StepField> =
        listOf(
            StepField.Toggle(
                WHEN, "When", listOf(Choice(HITPOINTS, "Hitpoints"), Choice(SKILL, "Skill")),
                current = { settings, _ -> settings[WHEN] ?: HITPOINTS },
            ),
            StepField.Toggle(
                WHEN, "", listOf(Choice(SPECIAL_ENERGY, "Special energy"), Choice(ATTACKED, "Under attack")),
                current = { settings, _ -> settings[WHEN] ?: HITPOINTS },
            ),
            StepField.Search(
                "Skill", StepTarget(SKILL_NAME, OptionSource { context -> SkillOptions.options(context).filter { it.value != "hitpoints" } }),
                "Which skill?", visible = { it[WHEN] == SKILL },
            ),
            StepField.Typed(
                SKILL_BELOW, BELOW_LABEL, SKILL_RANGE, SKILL_RULE, shown = { it ?: "@gry@not set yet" },
                visible = { it[WHEN] == SKILL },
            ),
            StepField.Typed(BELOW, BELOW_LABEL, BELOW_RANGE, BELOW_RULE, shown = { "${it ?: DEFAULT_BELOW}%" }, visible = { (it[WHEN] ?: HITPOINTS) == HITPOINTS }),
            StepField.Typed(
                SPECIAL_FOR, SPECIAL_FOR_LABEL, SPECIAL_FOR_RANGE, SPECIAL_FOR_RULE, shown = { specialsWord(it) },
                visible = { it[WHEN] == SPECIAL_ENERGY },
            ),
            StepField.Toggle(
                DO, "Do", listOf(Choice(EAT, "Eat"), Choice(DRINK, "Drink"), Choice(PRAY, "Pray"), Choice(STOP, "Stop"), Choice(JUMP, "Jump")),
                current = { settings, _ -> doing(settings[DO]) }, column = FieldColumn.RIGHT,
            ),
            StepField.Toggle(
                DO, "", listOf(Choice(SPECIAL_ATTACK, "Special attack")),
                current = { settings, _ -> doing(settings[DO]) }, column = FieldColumn.RIGHT,
            ),
            StepField.Items(
                FOODS, "Food", foods, "What would you like to eat?", "+ Add or remove food...", rows = FOOD_ROWS,
                column = FieldColumn.RIGHT, visible = { doing(it[DO]) == EAT }, empty = "+ Any food: pick some...",
            ),
            StepField.Items(
                POTIONS, "Potions", potions, "What would you like to drink?", "+ Add or remove potions...", rows = FOOD_ROWS,
                column = FieldColumn.RIGHT, visible = { doing(it[DO]) == DRINK },
            ),
            StepField.Items(
                PRAYERS, "Prayers", prayers, "What would you like to pray?", "+ Add or remove prayers...", rows = FOOD_ROWS,
                column = FieldColumn.RIGHT, visible = { doing(it[DO]) == PRAY },
            ),
            StepField.Search(
                "Step", StepTarget(STEP, JumpTargetOptions(steps, types, names)), "Which step should it jump to?",
                visible = { doing(it[DO]) == JUMP }, missing = "Deleted step", column = FieldColumn.RIGHT,
            ),
        )

    /**
     * What the reflex does, as its screen's header names it: "Eat", "Drink", "Pray", "Special attack", "Stop the flow",
     * "Jump to a step".
     */
    fun label(reflex: ReflexSettings): String =
        when (doing(reflex[DO])) {
            EAT -> "Eat"
            DRINK -> "Drink"
            PRAY -> "Pray"
            SPECIAL_ATTACK -> "Special attack"
            JUMP -> "Jump to a step"
            else -> "Stop the flow"
        }

    fun description(reflex: ReflexSettings): String =
        when (doing(reflex[DO])) {
            EAT -> "Eats the first of its foods the bag holds."
            DRINK -> "Drinks a dose of the first of its potions the bag holds."
            PRAY -> "Keeps its prayers on while its condition holds."
            SPECIAL_ATTACK -> "Uses the worn weapon's special attack."
            JUMP -> "Goes on from another step, running clear first while attacked."
            else -> "Stops the flow, running clear first while attacked."
        }

    /**
     * The first food, potion or prayer; shrimps for any food, an empty vial for no potion, the Prayer skill's icon for
     * no prayer; the Attack skill's icon for the special attack, which picks nothing; the cross to stop and arrow to
     * jump.
     */
    fun icon(reflex: ReflexSettings): StepIcon =
        when (doing(reflex[DO])) {
            EAT -> StepIcon.Item(foodIds(reflex).firstOrNull() ?: SHRIMPS)
            DRINK -> StepIcon.Item(potionIds(reflex).firstOrNull() ?: VIAL)
            PRAY -> prayerList(reflex).firstOrNull()?.let(Prayers::picture) ?: StepIcon.Skill(Skill.PRAYER)
            SPECIAL_ATTACK -> SPECIAL_ICON
            JUMP -> JUMP_ICON
            else -> STOP_ICON
        }

    /**
     * The reflex in a sentence, its jump numbered as the step stands in [steps]: "Hitpoints below 50%: eat trout,
     * lobster", "Under attack: pray Protect from melee", "Special energy for 2 specials: special attack", "Under
     * attack: jump to step 2".
     */
    fun sentence(reflex: ReflexSettings, steps: List<StepSettings>): String {
        val what = when (doing(reflex[DO])) {
            EAT -> "eat ${eats(reflex)}"
            DRINK -> "drink ${drinks(reflex)}"
            PRAY -> "pray ${prays(reflex)}"
            SPECIAL_ATTACK -> "special attack"
            JUMP -> jumps(reflex, steps)
            else -> "stop the flow"
        }
        return "${whenWords(reflex)}: $what"
    }

    /**
     * The flow's [reflexes] as a step's attach search offers them (S07c2b), by id in the flow's order: "1 Hitpoints below
     * 50%: eat trout" with its picture, a jump numbered as the step stands in [steps].
     */
    fun attachOptions(reflexes: List<ReflexSettings>, steps: List<StepSettings>): List<StepOption> =
        reflexes.withIndex().map { (index, reflex) ->
            StepOption(reflex.id.toString(), "${index + 1} ${sentence(reflex, steps)}", OptionIcon.of(icon(reflex)), group = index)
        }

    /** What an eat or drink reflex needs carried or withdrawn, including every portion or dose. */
    fun need(reflex: ReflexSettings): ToolNeed? =
        when (doing(reflex[DO])) {
            EAT -> {
                val ids = foodIds(reflex).flatMap { portions[it].orEmpty() }.ifEmpty { portions.keys }
                ToolNeed("food", ids.associateWith { 0 }, countable = false)
            }
            DRINK -> {
                val ids = potionIds(reflex).flatMap { doses[it].orEmpty() }
                ToolNeed("potion", ids.associateWith { 0 })
            }
            else -> null
        }

    /**
     * What its pair of When and Do wastes, in yellow on its screen: it acts all the same. Praying on low Prayer points
     * drains the points watched; every other pair with Pray has its use (Maxime, 2026-10-16), as does every pair with
     * Special attack. Then what the special attack's [facts] about the worn weapon say (S13 part 4).
     */
    fun warnings(reflex: ReflexSettings, facts: OptionFacts): List<String> =
        when ((reflex[WHEN] ?: HITPOINTS) to doing(reflex[DO])) {
            ATTACKED to EAT -> listOf(EATS_AT_FULL)
            ATTACKED to DRINK -> listOf(DRINKS_WHILE_ATTACKED)
            SKILL to EAT -> listOf(EATS_ON_SKILL)
            SKILL to PRAY -> listOfNotNull(PRAYS_ON_LOW_POINTS.takeIf { reflex[SKILL_NAME].equals("prayer", ignoreCase = true) })
            else -> emptyList()
        } + specialWarnings(reflex, facts)

    /**
     * A special energy When or a special attack Do with no worn weapon whose special Luna knows never holds or is never
     * done; a count of specials the bar cannot hold at the worn weapon's drain never holds either (Maxime, 2026-10-16).
     * Said in yellow, not red: the weapon changes without the flow.
     */
    private fun specialWarnings(reflex: ReflexSettings, facts: OptionFacts): List<String> {
        val onEnergy = (reflex[WHEN] ?: HITPOINTS) == SPECIAL_ENERGY
        if (!onEnergy && doing(reflex[DO]) != SPECIAL_ATTACK) return emptyList()
        val known = drains()
        val weapon = facts.worn.firstOrNull { it in known } ?: return listOf(NO_SPECIAL_WEAPON)
        val drain = known.getValue(weapon)
        val fit = 100 / drain
        val wanted = reflex[SPECIAL_FOR]?.toIntOrNull() ?: DEFAULT_SPECIAL_FOR
        if (!onEnergy || wanted <= fit) return emptyList()
        return listOf("${names.item(weapon)}'s special costs $drain%, at most $fit ${if (fit == 1) "fits" else "fit"} the bar")
    }

    private fun whenWords(reflex: ReflexSettings): String {
        val whenValue = reflex[WHEN] ?: HITPOINTS
        return when (whenValue) {
            ATTACKED -> "Under attack"
            SPECIAL_ENERGY -> "Special energy for ${specialsWord(reflex[SPECIAL_FOR])}"
            SKILL -> {
                val name = reflex[SKILL_NAME]
                val skill = Skill.NAMES.firstOrNull { it.equals(name, ignoreCase = true) } ?: name ?: "Skill"
                "$skill below ${reflex[SKILL_BELOW] ?: "not set yet"}"
            }
            else -> "Hitpoints below ${reflex[BELOW] ?: DEFAULT_BELOW}%"
        }
    }

    private fun eats(reflex: ReflexSettings): String =
        foodIds(reflex).joinToString(", ") { names.item(it).lowercase() }.ifEmpty { "any food" }

    private fun drinks(reflex: ReflexSettings): String =
        potionIds(reflex).joinToString(", ") { names.item(it).removeSuffix("(4)").trimEnd().lowercase() }.ifEmpty { "a potion" }

    private fun prays(reflex: ReflexSettings): String = prayerList(reflex).joinToString(", ", transform = Prayers::named).ifEmpty { "a prayer" }

    /** The At least count as the row and the sentence word it: "1 special", "2 specials", one without a count. */
    private fun specialsWord(count: String?): String {
        val text = count ?: DEFAULT_SPECIAL_FOR.toString()
        return "$text ${if (text == "1") "special" else "specials"}"
    }

    private fun jumps(reflex: ReflexSettings, steps: List<StepSettings>): String {
        val id = reflex[STEP]?.toIntOrNull() ?: return "jump to a step"
        val index = steps.indexOfFirst { it.id == id }
        return if (index < 0) "jump to a deleted step" else "jump to step ${index + 1}"
    }

    private fun foodIds(reflex: ReflexSettings): List<Int> = StepItems.parse(reflex[FOODS]).map { it.id }

    private fun potionIds(reflex: ReflexSettings): List<Int> = StepItems.parse(reflex[POTIONS]).map { it.id }

    /** The prayers the list holds, a key that is no prayer skipped (its screen says what is wrong). */
    private fun prayerList(reflex: ReflexSettings): List<CombatPrayer> = StepItems.parse(reflex[PRAYERS]).mapNotNull { Prayers.of(it.id) }

    /** A reflex without a Do eats; one with a Do no reflex has shows as stopping, its screen saying what is wrong. */
    private fun doing(value: String?): String = value ?: EAT

    companion object {
        /** The kind a reflex's settings carry on its screen, which no step has. */
        const val KIND = "reflex"

        const val FOOD_ROWS = 4
        const val SHRIMPS = 315
        const val VIAL = 229

        /** The red cross a click leaves on the ground (Maxime, 2026-10-15). */
        val STOP_ICON = StepIcon.Media("cross", 5)

        /** The yellow arrow that points at what a hint is about (Maxime, 2026-10-15). */
        val JUMP_ICON = StepIcon.Media("headicons_hint", 0)

        /** The special attack picks nothing, so the Attack skill stands for it (Maxime, 2026-10-16). */
        val SPECIAL_ICON = StepIcon.Skill(Skill.ATTACK)

        const val NO_SPECIAL_WEAPON = "You wield no weapon with a special attack"

        const val DRINKS_WHILE_ATTACKED = "Drinks while attacked, a dose each time"
        const val EATS_ON_SKILL = "Eats when the skill is low, even at full hitpoints"

        const val EATS_AT_FULL = "Eats while attacked, even at full hitpoints"

        const val PRAYS_ON_LOW_POINTS = "Prays when Prayer points are low, draining them further"
    }
}
