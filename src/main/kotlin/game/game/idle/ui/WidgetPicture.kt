package game.idle.ui

import game.idle.flow.StepIcon
import game.idle.flow.option.OptionIcon
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

        /** A search row's icon as a picture: npcs show their whole body, banks the bank step's icon. */
        fun of(icon: OptionIcon): WidgetPicture =
            when (icon) {
                is OptionIcon.Item -> Item(icon.id)
                is OptionIcon.Npc -> NpcBody(icon.id)
                is OptionIcon.Skill -> SkillIcons.sprite(icon.id)
                OptionIcon.Bank -> of(StepIcon.BANK)
                is OptionIcon.Media -> Media(icon.name, icon.index)
            }
    }
}

/**
 * How a packet carries a picture (102, and every row of the search's 105): a source byte, then a sprite's name and
 * index byte, or an item's or npc's id as a short, or nothing.
 */
object PictureEncoding {

    const val NONE = 0
    const val MEDIA = 1
    const val ITEM = 2
    const val NPC_BODY = 3

    fun source(picture: WidgetPicture): Int =
        when (picture) {
            WidgetPicture.None -> NONE
            is WidgetPicture.Media -> MEDIA
            is WidgetPicture.Item -> ITEM
            is WidgetPicture.NpcBody -> NPC_BODY
        }

    /** A sprite's index, or the item's or npc's id. */
    fun id(picture: WidgetPicture): Int =
        when (picture) {
            WidgetPicture.None -> 0
            is WidgetPicture.Media -> picture.index
            is WidgetPicture.Item -> picture.id
            is WidgetPicture.NpcBody -> picture.id
        }

    /** The bytes [write] puts: a sprite's name is a newline-ended string. */
    fun size(picture: WidgetPicture): Int =
        when (picture) {
            WidgetPicture.None -> 1
            is WidgetPicture.Media -> 1 + picture.name.length + 1 + 1
            is WidgetPicture.Item, is WidgetPicture.NpcBody -> 3
        }

    fun write(picture: WidgetPicture, message: ByteMessage): ByteMessage {
        val sourced = message.put(source(picture))
        return when (picture) {
            WidgetPicture.None -> sourced
            is WidgetPicture.Media -> sourced.putString(picture.name).put(picture.index)
            is WidgetPicture.Item, is WidgetPicture.NpcBody -> sourced.putShort(id(picture))
        }
    }

    /** What a log shows of a picture: "none", "media staticons 17", "item 1521", "npc 86". */
    fun describe(picture: WidgetPicture): String =
        when (picture) {
            WidgetPicture.None -> "none"
            is WidgetPicture.Media -> "media ${picture.name} ${picture.index}"
            is WidgetPicture.Item -> "item ${picture.id}"
            is WidgetPicture.NpcBody -> "npc ${picture.id}"
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
 * Shows a [WidgetPicture] on widget [widgetId] (opcode [OPCODE], unused by the 377 protocol): the widget id, then the
 * picture ([PictureEncoding]). Plain fields, so the harness log records them.
 */
class PictureMessageWriter(private val widgetId: Int, private val picture: WidgetPicture) : GameMessageWriter() {

    private val source: Int = PictureEncoding.source(picture)

    private val name: String = (picture as? WidgetPicture.Media)?.name.orEmpty()

    /** A sprite's index, or the item's or npc's id. */
    private val id: Int = PictureEncoding.id(picture)

    override fun write(player: Player?, buffer: ByteBuf): ByteMessage =
        PictureEncoding.write(picture, ByteMessage.message(OPCODE, MessageType.VAR, buffer).putShort(widgetId))

    companion object {
        const val OPCODE = 102
    }
}
