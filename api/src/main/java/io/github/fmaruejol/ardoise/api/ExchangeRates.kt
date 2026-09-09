package io.github.fmaruejol.ardoise.api

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.coroutines.cancellation.CancellationException

/**
 * What one currency was worth in another on a given day.
 *
 * **Not Spliit's API**: it is [Frankfurter](https://frankfurter.dev), which is
 * what upstream's `useCurrencyRate` queries. Two clients filling one field
 * from two sources would put two different rates on the same expense. It is
 * the only host the app talks to that is not the chosen instance, and it is
 * told nothing but a date and two currency codes.
 */
interface ExchangeRates {
    /**
     * How many [target] units one [base] unit bought on [date], or null. The
     * expense's own currency is the base, so the answer is the stored rate.
     */
    suspend fun rate(date: LocalDate, base: String, target: String): BigDecimal?
}

/** Frankfurter. [baseUrl] is a parameter so a test can point it elsewhere. */
class FrankfurterExchangeRates(
    private val client: HttpClient,
    private val baseUrl: String = FRANKFURTER,
) : ExchangeRates {
    override suspend fun rate(date: LocalDate, base: String, target: String): BigDecimal? {
        if (base == target) return null
        return try {
            // The rate wanted is the one that applied when the money was spent.
            val response = client.get("$baseUrl/v1/$date") {
                url.parameters.append("base", base)
                url.parameters.append("symbols", target)
            }
            if (!response.status.isSuccess()) return null

            val rates = Lenient.parseToJsonElement(response.bodyAsText())
                .jsonObject["rates"] as? JsonObject
                ?: return null

            // Read as text: a rate multiplies an amount, so no Double.
            val quoted = rates[target] as? JsonPrimitive ?: return null
            BigDecimal(quoted.content)
        } catch (e: CancellationException) {
            // Not a look-up that failed: the caller went away. Swallowing it
            // would report "no rate" and leave the coroutine looking complete.
            throw e
        } catch (e: Exception) {
            // No rate is a state the form handles: the field stays empty and
            // the user is offered the look-up again.
            null
        }
    }

    private companion object {
        const val FRANKFURTER = "https://api.frankfurter.dev"

        /** This response is not superjson, and text keeps the rate's digits. */
        val Lenient = Json { ignoreUnknownKeys = true }
    }
}
