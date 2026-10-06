package game.idle.tutorial

import api.predef.*
import io.luna.game.event.impl.FlashingTabClickEvent
import io.luna.game.event.impl.LoginEvent
import io.luna.game.event.impl.SkillChangeEvent

val tutorialData = TutorialData.load(TutorialData.PATH)
val tutorial = LunaTutorial(TutorialScript(tutorialData), tutorialData, world)

on(LoginEvent::class)
    .filter { !plr.isBot }
    .then { tutorial.onLogin(plr) }

npc1(TutorialScript.RUNESCAPE_GUIDE) { tutorial.talkToGuide(plr, targetNpc) }

npc1(TutorialScript.SURVIVAL_EXPERT) { tutorial.talkToSurvivalExpert(plr, targetNpc) }

on(FlashingTabClickEvent::class) { tutorial.tabOpened(plr, tab) }

on(SkillChangeEvent::class)
    .filter { !plr.isBot }
    .then { tutorial.experienceChanged(plr, this) }

tutorialData.doors.forEach { door ->
    door.closed.forEach { leaf -> object1(leaf.id) { tutorial.openDoor(plr, door, leaf) } }
}
