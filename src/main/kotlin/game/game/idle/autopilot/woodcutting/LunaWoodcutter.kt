package game.idle.autopilot.woodcutting

import game.idle.autopilot.Candidate
import game.idle.autopilot.LunaClicks
import game.idle.autopilot.ObjectsAround
import game.idle.location.Area
import game.idle.location.Tile
import game.idle.movement.Doorways
import game.skill.woodcutting.cutTree.Tree
import game.skill.woodcutting.cutTree.TreeStump
import io.luna.game.event.impl.ObjectClickEvent.ObjectFirstClickEvent
import io.luna.game.model.mob.Player

/**
 * [Woodcutter] for a logged-in player cutting one [kind] of tree within [area]: only that kind's standing trees are seen. A chop
 * goes through the same event and interaction action as the client's click, so Luna's reach checks, animations and XP
 * apply unchanged.
 */
class LunaWoodcutter(private val player: Player, private val kind: Tree, private val area: Area, doorways: Doorways) : Woodcutter {

    private val around = ObjectsAround(player, area, TreeStump.ALIVE_TREE_MAP.get(kind), doorways)

    override fun isBusy(): Boolean = LunaClicks.busy(player)

    override fun look(): WoodcuttingView = WoodcuttingView(around.atLocation(), around.candidates())

    override fun chop(tree: Candidate) {
        val target = around.find(tree) ?: return
        LunaClicks.interact(player, ObjectFirstClickEvent(player, target), target, ObjectFirstClickEvent::class.java)
    }

    override fun anchor(): Tile = area.anchor

    override fun logs(): Int = player.inventory.computeAmountForId(kind.logId)

    override fun interrupt() = LunaClicks.interrupt(player)
}
