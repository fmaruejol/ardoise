package io.github.fmaruejol.ardoise.api

import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

/** Frankfurter's shape, against a real server. */
class ExchangeRatesTest {
    private lateinit var server: MockWebServer
    private lateinit var rates: ExchangeRates

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        rates = FrankfurterExchangeRates(
            client = spliitHttpClient(OkHttp.create()),
            baseUrl = server.url("/").toString().trimEnd('/'),
        )
    }

    @After
    fun tearDown() = server.shutdown()

    private fun respond(body: String, code: Int = 200) {
        server.enqueue(MockResponse().setResponseCode(code).setBody(body))
    }

    @Test
    fun `asks for the day the money was spent, with the expense's currency as the base`() =
        runTest {
            respond("""{"amount":1.0,"base":"BRL","date":"2026-09-11","rates":{"EUR":0.17857}}""")

            rates.rate(LocalDate.parse("2026-09-11"), base = "BRL", target = "EUR")

            val request = server.takeRequest()
            // The date is in the path and the base is the original currency,
            // which is upstream's own query, so the two clients get the same
            // number.
            assertTrue(request.path!!.startsWith("/v1/2026-09-11"))
            assertTrue(request.path!!.contains("base=BRL"))
            assertTrue(request.path!!.contains("symbols=EUR"))
        }

    @Test
    fun `reads the rate exactly, digit for digit`() = runTest {
        respond("""{"amount":1.0,"base":"BRL","date":"2026-09-11","rates":{"EUR":0.178574617}}""")

        val rate = rates.rate(LocalDate.parse("2026-09-11"), "BRL", "EUR")

        // Through the JSON text rather than a Double: it multiplies an amount.
        assertEquals(BigDecimal("0.178574617"), rate)
    }

    @Test
    fun `takes the rate the day actually has, whichever day that is`() = runTest {
        // A Saturday has no fixing, so the API answers with Friday's. It is
        // still the rate that applied: nothing moved over the weekend.
        respond("""{"amount":1.0,"base":"BRL","date":"2026-09-11","rates":{"EUR":0.17857}}""")

        val rate = rates.rate(LocalDate.parse("2026-09-12"), "BRL", "EUR")

        assertEquals(BigDecimal("0.17857"), rate)
    }

    @Test
    fun `an answer without the currency asked for is no answer`() = runTest {
        respond("""{"amount":1.0,"base":"BRL","date":"2026-09-11","rates":{"USD":0.19}}""")

        assertNull(rates.rate(LocalDate.parse("2026-09-11"), "BRL", "EUR"))
    }

    @Test
    fun `a refusal is no answer either`() = runTest {
        respond("""{"message":"not found"}""", code = 404)

        assertNull(rates.rate(LocalDate.parse("2026-09-11"), "BRL", "XXX"))
    }

    @Test
    fun `nothing is asked when both sides are the same currency`() = runTest {
        assertNull(rates.rate(LocalDate.parse("2026-09-11"), "EUR", "EUR"))

        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a cancelled look-up is cancelled, not a rate of null`() = runTest {
        // Nothing enqueued, so the request hangs until the job is cancelled.
        var outcome: Throwable? = null
        val job = launch(Dispatchers.IO) {
            try {
                rates.rate(LocalDate.parse("2026-09-11"), "BRL", "EUR")
            } catch (e: Throwable) {
                outcome = e
            }
        }
        while (server.requestCount == 0) Thread.sleep(10)
        job.cancelAndJoin()

        // Swallowed, the caller would read "no rate for that day" and the
        // coroutine would look like it had finished its work.
        assertTrue("expected cancellation, got $outcome", outcome is CancellationException)
    }
}
