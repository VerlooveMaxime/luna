package game.idle.ui

import api.predef.*
import game.idle.autopilot.IdleSteps
import game.idle.flow.StepSettings
import io.luna.game.event.impl.ArrangeItemEvent
import io.luna.game.event.impl.ButtonClickEvent
import io.luna.game.model.def.NpcDefinition

val gallery = WidgetGallery(IdleSteps.load().types.all.map { GalleryKind(it.kind, WidgetPicture.of(it.icon(StepSettings(it.kind)))) }) {
    NpcDefinition.ALL.retrieve(it).name
}

cmd("widgets", RIGHTS_DEV) { gallery.open(plr) }

on(ButtonClickEvent::class)
    .filter { GalleryWidgets.owns(id) }
    .then { gallery.click(plr, id) }

on(ArrangeItemEvent::class)
    .filter { widgetId == GalleryWidgets.SCROLL }
    .then { gallery.arrange(plr, fromIndex, toIndex) }
