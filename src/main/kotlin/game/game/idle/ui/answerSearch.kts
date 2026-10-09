package game.idle.ui

import api.predef.*

on(SearchPageEvent::class) { SearchPrompts.page(plr, serial, offset, count, query) }

on(SearchPickEvent::class) { SearchPrompts.pick(plr, serial, index) }

on(SearchClosedEvent::class) { SearchPrompts.close(plr, serial) }

on(SearchNameEvent::class) { SearchPrompts.name(plr, serial, typed) }
