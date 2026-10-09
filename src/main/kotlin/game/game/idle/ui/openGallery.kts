package game.idle.ui

import api.predef.*
import game.idle.autopilot.IdleSteps
import game.idle.flow.StepSettings
import game.idle.autopilot.fighting.FightTargetCatalog
import game.idle.flow.option.ItemCatalog
import game.idle.flow.option.LunaGameNames
import game.idle.flow.option.LunaOptionFacts
import game.idle.location.BankCatalog
import io.luna.game.event.impl.ArrangeItemEvent
import io.luna.game.event.impl.ButtonClickEvent

val gallery = WidgetGallery(
    IdleSteps.load().types.all.map { GalleryKind(it.kind, WidgetPicture.of(it.icon(StepSettings(it.kind)))) },
    LunaGameNames::npc,
    GallerySearches.all(
        LunaGameNames,
        BankCatalog.load(BankCatalog.PATH),
        FightTargetCatalog.fromCache(),
        ItemCatalog.fromCache(),
        LunaOptionFacts::of,
    ),
    ClientFont.fromCache(ctx.cache),
)

cmd("widgets", RIGHTS_DEV) { gallery.open(plr) }

on(ButtonClickEvent::class)
    .filter { GalleryWidgets.owns(id) }
    .then { gallery.click(plr, id) }

on(ArrangeItemEvent::class)
    .filter { widgetId == GalleryWidgets.SCROLL }
    .then { gallery.arrange(plr, fromIndex, toIndex) }
