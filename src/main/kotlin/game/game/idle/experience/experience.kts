package game.idle.experience

import api.predef.*
import game.idle.tutorial.TutorialExperience

world.experienceModifier = RatedExperience(ExperienceRates.load(ExperienceRates.PATH), TutorialExperience())
