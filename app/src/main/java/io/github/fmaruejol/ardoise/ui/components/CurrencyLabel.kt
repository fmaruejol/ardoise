package io.github.fmaruejol.ardoise.ui.components

import io.github.fmaruejol.ardoise.core.currency.GroupCurrency

/**
 * `EUR (€)`, or just the code when the symbol is the code. The symbol is the
 * one a new group would store, so the picker shows what the group will.
 */
fun currencyLabel(code: String): String {
    val symbol = GroupCurrency.symbolFor(code)
    return if (symbol == code) code else "$code ($symbol)"
}
