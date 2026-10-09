package game.idle.flow.option

/**
 * The rows of a processing step (light, cook, smelt, smith), which narrow with the input source (S01): from earlier
 * steps only what they get is offered, from the bank everything, with the bank's counts.
 */
object ProcessOptions {

    const val FROM_EARLIER = "from an earlier step"

    /**
     * The row for making [product] out of [inputs] (item ids) with [level] in [skill], kept under [value]; null when the
     * earlier steps do not get every input.
     */
    fun option(
        context: OptionContext,
        product: Int,
        label: String,
        inputs: List<Int>,
        skill: Int,
        level: Int,
        value: String = product.toString(),
    ): StepOption? {
        val fromBank = context.input == InputSource.BANK
        if (!fromBank && !context.before.gathered.containsAll(inputs)) return null
        return StepOption(
            value = value,
            label = label,
            icon = OptionIcon.Item(product),
            note = if (fromBank) bankNote(context.facts, inputs) else FROM_EARLIER,
            blocked = context.facts.lacks(skill, level),
            level = level,
            banked = if (fromBank) inputs.map(context.facts::banked).min() else 0,
        )
    }

    /** The bank's count of the first input, with "..." when more inputs are needed (the client takes ISO-8859-1). */
    private fun bankNote(facts: OptionFacts, inputs: List<Int>): String =
        facts.bankNote(inputs.first()) + if (inputs.size > 1) " ..." else ""
}
