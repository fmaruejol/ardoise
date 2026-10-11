package io.github.fmaruejol.ardoise.core.currency

import io.github.fmaruejol.ardoise.core.model.Group
import io.github.fmaruejol.ardoise.core.model.GroupSummary
import io.github.fmaruejol.ardoise.core.model.UserGroupBalance
import java.util.Currency
import java.util.Locale

/**
 * One group's currency. [symbol] is free text and says nothing about scale;
 * [code] is the ISO code, and is null on groups created before it existed.
 */
data class GroupCurrency(
    /** ISO 4217, or null for a group with only a custom symbol. */
    val code: String?,
    val symbol: String,
    /** `amount` is in units of `10^-decimalDigits`. */
    val decimalDigits: Int,
) {
    companion object {
        /** What upstream falls back to for an absent or unknown code. */
        const val DEFAULT_DECIMAL_DIGITS: Int = 2

        fun of(currencyCode: String?, currencySymbol: String): GroupCurrency {
            val code = currencyCode?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }
            return GroupCurrency(
                code = code,
                symbol = currencySymbol,
                decimalDigits = decimalDigitsFor(code),
            )
        }

        /**
         * Upstream's table, **not** `Currency.getDefaultFractionDigits()`: the
         * JDK says HUF, COP and IDR have two decimals and Spliit says zero.
         */
        fun decimalDigitsFor(currencyCode: String?): Int {
            val code = currencyCode?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }
                ?: return DEFAULT_DECIMAL_DIGITS
            return if (code in ZERO_DECIMAL_CODES) 0 else DEFAULT_DECIMAL_DIGITS
        }

        /**
         * Upstream's `supportedCurrencyCodes`, in its order, each with the
         * symbol a new group stores: the `en-US` entry of upstream's
         * `currency-data.json`, whatever the device's language. The symbol is
         * stored on the group, so a locale's (`$US` in French) would be shown
         * to every member as that group's currency.
         */
        private val SYMBOLS: Map<String, String> = linkedMapOf(
            "USD" to "$", "EUR" to "€", "JPY" to "¥", "BGN" to "BGN", "CZK" to "Kč",
            "DKK" to "Dkr", "GBP" to "£", "HUF" to "Ft", "PLN" to "zł", "RON" to "RON",
            "SEK" to "Skr", "CHF" to "CHF", "ISK" to "Ikr", "NOK" to "Nkr", "TRY" to "TL",
            "AUD" to "AU$", "BRL" to "R$", "CAD" to "CA$", "CNY" to "CN¥", "HKD" to "HK$",
            "IDR" to "Rp", "ILS" to "₪", "INR" to "Rs", "KRW" to "₩", "MKD" to "MKD",
            "MXN" to "MX$", "MYR" to "RM", "NZD" to "NZ$", "PHP" to "₱", "SGD" to "S$",
            "THB" to "฿", "VND" to "₫", "ZAR" to "R", "COP" to "CO$",
        )

        val SUPPORTED_CODES: List<String> = SYMBOLS.keys.toList()

        /** The code itself for one upstream does not list. */
        fun symbolFor(currencyCode: String): String = SYMBOLS[currencyCode] ?: currencyCode

        /**
         * What a new group starts on: the device's currency, or
         * [FALLBACK_CODE] when that is not one Spliit offers. Only the *code*
         * comes from the JDK, and the scale is [decimalDigitsFor]'s answer.
         */
        fun defaultCodeFor(locale: Locale = Locale.getDefault()): String {
            val code = runCatching { Currency.getInstance(locale).currencyCode }.getOrNull()
            return code?.takeIf { it in SUPPORTED_CODES } ?: FALLBACK_CODE
        }

        /** Where [defaultCodeFor] lands when the device says nothing useful. */
        const val FALLBACK_CODE: String = "EUR"

        /** The supported currencies with no minor unit, from upstream's data. */
        val ZERO_DECIMAL_CODES: Set<String> =
            setOf("COP", "HUF", "IDR", "ISK", "JPY", "KRW", "VND")
    }
}

fun Group.currency(): GroupCurrency = GroupCurrency.of(currencyCode, currencySymbol)

fun GroupSummary.currency(): GroupCurrency = GroupCurrency.of(currencyCode, currencySymbol)

fun UserGroupBalance.currency(): GroupCurrency = GroupCurrency.of(currencyCode, currencySymbol)
