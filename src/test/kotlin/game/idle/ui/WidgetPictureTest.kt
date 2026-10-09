package game.idle.ui

import game.harness.encode
import game.idle.flow.StepIcon
import io.luna.game.model.mob.Skill
import io.luna.net.codec.MessageType
import io.luna.net.msg.GameMessage
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.nio.charset.StandardCharsets

class WidgetPictureTest {

    private val encoded = mutableListOf<GameMessage>()

    @AfterEach
    fun releasePayloads() = encoded.forEach { it.payload.releaseAll() }

    private fun encoded(picture: WidgetPicture) = encode(PictureMessageWriter(30430, picture)).also { encoded += it }

    private fun bytes(message: GameMessage): List<Int> {
        val buffer = message.payload.buffer
        return (buffer.readerIndex() until buffer.writerIndex()).map { buffer.getByte(it).toInt() and 0xff }
    }

    @Test
    fun `the message uses the picture opcode with a byte length`() {
        val message = encoded(WidgetPicture.None)

        assertEquals(listOf<Any>(102, MessageType.VAR), listOf(message.opcode, message.type))
    }

    @Test
    fun `an emptied picture is the widget id and source none`() {
        assertEquals(listOf(0x76, 0xde, 0), bytes(encoded(WidgetPicture.None)))
    }

    @Test
    fun `a sprite picture carries its name and a byte index`() {
        val name = "keys".toByteArray(StandardCharsets.ISO_8859_1).map { it.toInt() }

        assertEquals(listOf(0x76, 0xde, 1) + name + listOf(10, 3), bytes(encoded(WidgetPicture.Media("keys", 3))))
    }

    @Test
    fun `an item picture carries the item's id`() {
        assertEquals(listOf(0x76, 0xde, 2, 0x04, 0x23), bytes(encoded(WidgetPicture.Item(1059))))
    }

    @Test
    fun `an npc picture carries the npc's id`() {
        assertEquals(listOf(0x76, 0xde, 3, 0, 81), bytes(encoded(WidgetPicture.NpcBody(81))))
    }

    @Test
    fun `a skill icon is the skills tab's sprite`() {
        assertEquals(WidgetPicture.Media("staticons", 17), WidgetPicture.of(StepIcon.Skill(Skill.WOODCUTTING)))
        assertEquals(WidgetPicture.Media("staticons", 0), WidgetPicture.of(StepIcon.Skill(Skill.ATTACK)))
        assertEquals(WidgetPicture.Media("staticons", 6), WidgetPicture.of(StepIcon.Skill(Skill.HITPOINTS)))
    }

    @Test
    fun `the three later skills take their icons from the second sheet`() {
        assertEquals(WidgetPicture.Media("staticons2", 0), SkillIcons.sprite(Skill.RUNECRAFTING))
        assertEquals(WidgetPicture.Media("staticons2", 2), SkillIcons.sprite(Skill.FARMING))
    }

    @Test
    fun `a skill Luna does not have is refused`() {
        assertThrows<IllegalArgumentException> { SkillIcons.sprite(21) }
    }

    @Test
    fun `a media icon and an item icon keep what they name`() {
        assertEquals(WidgetPicture.Media("mapmarker", 0), WidgetPicture.of(StepIcon.Media("mapmarker", 0)))
        assertEquals(WidgetPicture.Item(1059), WidgetPicture.of(StepIcon.Item(1059)))
    }
}
