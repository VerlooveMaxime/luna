package game.idle.tutorial

import api.combat.player.PlayerCombatHandler.playerDefence
import api.predef.*
import game.idle.autopilot.AutopilotConfig
import game.idle.autopilot.IdleSteps
import game.idle.ui.IdleTab
import game.idle.ui.IdleUi
import io.luna.game.event.impl.FlashingTabClickEvent
import io.luna.game.event.impl.LoginEvent
import io.luna.game.event.impl.SkillChangeEvent
import io.luna.game.model.mob.combat.damage.CombatDamageRequest

val tutorialData = TutorialData.load(TutorialData.PATH)
val idleTab = IdleTab.fromCache(ctx.cache, AutopilotConfig.load(AutopilotConfig.PATH).savedFlowSlots)
val tutorial = LunaTutorial(TutorialScript(tutorialData), tutorialData, world, IdleUi(IdleSteps.load().types::summary, idleTab))

on(LoginEvent::class)
    .filter { !plr.isBot }
    .then { tutorial.onLogin(plr) }

npc1(TutorialScript.RUNESCAPE_GUIDE) { tutorial.talkToGuide(plr, targetNpc) }

npc1(TutorialScript.SURVIVAL_EXPERT) { tutorial.talkToSurvivalExpert(plr, targetNpc) }

npc1(TutorialScript.MASTER_CHEF) { tutorial.talkToChef(plr, targetNpc) }

npc1(TutorialScript.QUEST_GUIDE) { tutorial.talkToQuestGuide(plr, targetNpc) }

npc1(TutorialScript.MINING_INSTRUCTOR) { tutorial.talkToMiningInstructor(plr, targetNpc) }

playerDefence { tutorial.spares(player, other) }.then { damage = CombatDamageRequest.zero(other, player).resolve() }

on(FlashingTabClickEvent::class) { tutorial.tabOpened(plr, tab) }

cmd("tutorial", RIGHTS_DEV) { plr.sendMessage(tutorial.jumpTo(plr, args.firstOrNull().orEmpty())) }

on(SkillChangeEvent::class)
    .filter { !plr.isBot }
    .then { tutorial.experienceChanged(plr, this) }

tutorialData.doors.forEach { door ->
    door.closed.forEach { leaf -> object1(leaf.id) { tutorial.openDoor(plr, door, leaf) } }
}

tutorialData.ladders.forEach { ladder -> object1(ladder.id) { tutorial.ladderClimbed(plr, ladder) } }
