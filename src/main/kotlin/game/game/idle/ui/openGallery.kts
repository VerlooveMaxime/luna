package game.idle.ui

import api.predef.*
import io.luna.game.event.impl.ButtonClickEvent

val gallery = WidgetGallery()

cmd("widgets", RIGHTS_DEV) { gallery.open(plr) }

on(ButtonClickEvent::class)
    .filter { GalleryWidgets.owns(id) }
    .then { gallery.click(plr, id) }
