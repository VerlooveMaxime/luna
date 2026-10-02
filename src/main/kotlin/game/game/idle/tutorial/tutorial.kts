package game.idle.tutorial

import api.predef.*
import io.luna.game.event.impl.LoginEvent

val tutorialData = TutorialData.load(TutorialData.PATH)
val tutorial = LunaTutorial(TutorialScript(tutorialData), tutorialData, world)

on(LoginEvent::class)
    .filter { !plr.isBot }
    .then { tutorial.onLogin(plr) }

npc1(TutorialScript.RUNESCAPE_GUIDE) { tutorial.talkToGuide(plr, targetNpc) }

tutorialData.doors.forEach { door ->
    object1(door.id) { tutorial.openDoor(plr, door) }
}
