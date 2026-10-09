package game.idle.ui

import game.idle.autopilot.bank.BankOptions
import game.idle.autopilot.fighting.FightOptions
import game.idle.autopilot.fighting.FightTargetCatalog
import game.idle.autopilot.fishing.FishOptions
import game.idle.autopilot.woodcutting.TreeOptions
import game.idle.flow.option.GameNames
import game.idle.flow.option.ItemCatalog
import game.idle.flow.option.ItemOptions
import game.idle.flow.option.OptionContext
import game.idle.flow.option.OptionFacts
import game.idle.flow.option.OptionSource
import game.idle.flow.option.SkillOptions
import game.idle.location.BankCatalog
import io.luna.game.model.mob.Player

/**
 * The gallery's searches, in its buttons' order, over real option lists judged on the player's [facts]: trees, fishing
 * methods (two columns), skills, banks, every fight target (npc bodies) and every item, the last two long enough to
 * page through.
 */
object GallerySearches {

    fun all(
        names: GameNames,
        banks: BankCatalog,
        fightTargets: FightTargetCatalog,
        items: ItemCatalog,
        facts: (Player) -> OptionFacts,
    ): List<GallerySearch> {
        fun search(title: String, source: OptionSource) =
            GallerySearch(title) { player -> source.options(OptionContext(facts = facts(player))) }
        return listOf(
            search("Which tree would you like to cut?", TreeOptions(names)),
            search("What would you like to fish?", FishOptions(names)),
            search("Which skill?", SkillOptions),
            search("Which bank?", BankOptions(banks)),
            search("What would you like to fight?", FightOptions(fightTargets)),
            search("Which item?", ItemOptions(items)),
        )
    }
}
