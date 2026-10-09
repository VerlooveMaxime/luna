package game.idle.ui

import game.idle.flow.StepIcon
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Skill
import io.luna.net.codec.ByteMessage
import io.luna.net.codec.MessageType
import io.luna.net.msg.GameMessageWriter
import io.netty.buffer.ByteBuf

/**
 * What a picture widget the IdleRS client defines in code shows (`idlers.WidgetPicture` in `luna-client`): nothing, a
 * sprite of its media archive, an item's inventory icon, or an npc's whole body playing its stand animation.
 */
sealed interface WidgetPicture {

    data object None : WidgetPicture

    data class Media(val name: String, val index: Int) : WidgetPicture

    data class Item(val id: Int) : WidgetPicture

    data class NpcBody(val id: Int) : WidgetPicture

    companion object {
        /** A step's icon as a picture; a skill's icon is the sprite the skills tab shows for it. */
        fun of(icon: StepIcon): WidgetPicture =
            when (icon) {
                is StepIcon.Skill -> SkillIcons.sprite(icon.skill)
                is StepIcon.Media -> Media(icon.name, icon.index)
                is StepIcon.Item -> Item(icon.id)
            }
    }
}

/**
 * The skills tab's icon of each of Luna's skills: `staticons` 0-17 in the tab's order (interfaces 3965-3982), then
 * `staticons2` for Runecrafting, Slayer and Farming (S04 findings).
 */
object SkillIcons {

    private val SPRITES: Map<Int, WidgetPicture.Media> =
        listOf(
            Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE, Skill.RANGED, Skill.PRAYER, Skill.MAGIC, Skill.HITPOINTS,
            Skill.AGILITY, Skill.HERBLORE, Skill.THIEVING, Skill.CRAFTING, Skill.FLETCHING, Skill.MINING, Skill.SMITHING,
            Skill.FISHING, Skill.COOKING, Skill.FIREMAKING, Skill.WOODCUTTING,
        ).mapIndexed { index, skill -> skill to WidgetPicture.Media("staticons", index) }.toMap() +
            listOf(Skill.RUNECRAFTING, Skill.SLAYER, Skill.FARMING)
                .mapIndexed { index, skill -> skill to WidgetPicture.Media("staticons2", index) }.toMap()

    fun sprite(skill: Int): WidgetPicture.Media = SPRITES[skill] ?: throw IllegalArgumentException("No skill $skill")
}

/**
 * Shows a [WidgetPicture] on widget [widgetId] (opcode [OPCODE], unused by the 377 protocol): the widget id, a source
 * byte, then a sprite's name and index, or an item's or npc's id. Plain fields, so the harness log records them.
 */
class PictureMessageWriter(private val widgetId: Int, picture: WidgetPicture) : GameMessageWriter() {

    private val source: Int =
        when (picture) {
            WidgetPicture.None -> NONE
            is WidgetPicture.Media -> MEDIA
            is WidgetPicture.Item -> ITEM
            is WidgetPicture.NpcBody -> NPC_BODY
        }

    private val name: String = (picture as? WidgetPicture.Media)?.name.orEmpty()

    /** A sprite's index, or the item's or npc's id. */
    private val id: Int =
        when (picture) {
            WidgetPicture.None -> 0
            is WidgetPicture.Media -> picture.index
            is WidgetPicture.Item -> picture.id
            is WidgetPicture.NpcBody -> picture.id
        }

    override fun write(player: Player?, buffer: ByteBuf): ByteMessage {
        val message = ByteMessage.message(OPCODE, MessageType.VAR, buffer).putShort(widgetId).put(source)
        when (source) {
            MEDIA -> message.putString(name).put(id)
            ITEM, NPC_BODY -> message.putShort(id)
        }
        return message
    }

    companion object {
        const val OPCODE = 102
        const val NONE = 0
        const val MEDIA = 1
        const val ITEM = 2
        const val NPC_BODY = 3
    }
}
