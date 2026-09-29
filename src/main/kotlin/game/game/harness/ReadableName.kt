package game.harness

/** Simple class name, or the binary name for anonymous and lambda classes, which have no simple name. */
fun readableName(type: Class<*>): String = type.simpleName.ifEmpty { type.name }
