package game.idle.autopilot.making

import io.luna.game.model.mob.Skill

/** A recipe of [use] on [on], both used up, at Cooking 1. */
fun simpleRecipe(product: Int, label: String, use: Int, on: Int, window: MakeWindow = MakeWindow.Choice): Recipe =
    Recipe(product, label, Skill.COOKING, 1, listOf(RecipeWay(use, on, mapOf(use to 1, on to 1))), window)

/** Bread dough from a pot of flour and a bucket of water. */
val BREAD_DOUGH = simpleRecipe(2307, "Bread dough", 1933, 1929)
