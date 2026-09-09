package io.github.fmaruejol.ardoise.ui.components

import java.util.Currency

/**
 * `EUR (€)`, or just the code when the JDK has no symbol worth showing: the
 * code is what the server stores and the symbol is what the reader knows.
 *
 * Only the *symbol* comes from `java.util.Currency`, the number of decimals
 * is `GroupCurrency`'s answer, and the two disagree.
 */
fun currencyLabel(code: String): String {
    val symbol = runCatching { Currency.getInstance(code).symbol }.getOrNull()
    return if (symbol.isNullOrBlank() || symbol == code) code else "$code ($symbol)"
}
