package io.github.fmaruejol.ardoise.api

import io.github.fmaruejol.ardoise.api.trpc.TrpcClient
import io.github.fmaruejol.ardoise.core.model.PaidFor
import io.github.fmaruejol.ardoise.core.model.SplitMode
import io.github.fmaruejol.ardoise.core.result.SpliitError
import io.github.fmaruejol.ardoise.core.result.SpliitResult
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.URLDecoder
import java.time.Instant
import java.time.LocalDate

/** The tRPC client against a real HTTP server. */
class SpliitApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: SpliitApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = SpliitApiClient(
            TrpcClient(
                httpClient = spliitHttpClient(OkHttp.create()),
                baseUrl = { server.url("/").toString() },
            ),
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    // --- transport ---------------------------------------------------------

    @Test
    fun `a query is an unbatched GET carrying the envelope in the input parameter`() = runTest {
        server.enqueue(ok(GROUP_RESPONSE))

        api.getGroup("g1")

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/api/trpc/groups.get", request.requestUrl?.encodedPath)
        assertEquals("""{"json":{"groupId":"g1"}}""", request.inputParameter())
        // Batching would add this and change the whole wire format.
        assertNull(request.requestUrl?.queryParameter("batch"))
    }

    @Test
    fun `a query with no input sends no input parameter`() = runTest {
        server.enqueue(ok("""{"result":{"data":{"json":{"categories":[]}}}}"""))

        api.listCategories()

        assertNull(server.takeRequest().requestUrl?.queryParameter("input"))
    }

    @Test
    fun `a mutation POSTs the envelope as the body`() = runTest {
        server.enqueue(ok("""{"result":{"data":{"json":{"groupId":"g9"}}}}"""))

        val result = api.createGroup(
            GroupInput(
                name = "Trip",
                currencySymbol = "€",
                currencyCode = "EUR",
                participants = listOf(ParticipantInput(name = "Ada"), ParticipantInput(name = "Alan")),
            ),
        )

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/trpc/groups.create", request.requestUrl?.encodedPath)
        assertEquals(
            """{"json":{"groupFormValues":{"name":"Trip","currency":"€","currencyCode":"EUR",""" +
                """"participants":[{"name":"Ada"},{"name":"Alan"}]}}}""",
            request.body.readUtf8(),
        )
        assertEquals(SpliitResult.Success("g9"), result)
    }

    // --- superjson ---------------------------------------------------------

    @Test
    fun `an outgoing date is annotated so the server rebuilds a real Date`() = runTest {
        server.enqueue(ok("""{"result":{"data":{"json":{"expenseId":"e1"}}}}"""))

        api.createExpense(
            groupId = "g1",
            expense = ExpenseInput(
                title = "Dinner",
                amount = 4_250,
                expenseDate = LocalDate.of(2026, 9, 4),
                paidById = "p1",
                paidFor = listOf(PaidFor("p1", 100), PaidFor("p2", 100)),
                splitMode = SplitMode.EVENLY,
            ),
        )

        val body = Json.parseToJsonElement(server.takeRequest().body.readUtf8()) as JsonObject
        assertEquals(
            """{"values":{"expenseFormValues.expenseDate":["Date"]}}""",
            body["meta"].toString(),
        )
        assertTrue(
            body["json"].toString().contains(""""expenseDate":"2026-09-04T00:00:00Z""""),
        )
    }

    @Test
    fun `an incoming date is decoded and its annotations ignored`() = runTest {
        server.enqueue(ok(GROUP_RESPONSE))

        val group = (api.getGroup("g1") as SpliitResult.Success).value!!

        assertEquals(Instant.parse("2026-01-02T03:04:05Z"), group.createdAt)
        assertEquals(listOf("Ada", "Alan"), group.participants.map { it.name })
        assertEquals("EUR", group.currencyCode)
        assertEquals("€", group.currencySymbol)
    }

    // --- mapping -----------------------------------------------------------

    @Test
    fun `expense list keeps amounts as minor units and shares as stored`() = runTest {
        server.enqueue(ok(EXPENSES_RESPONSE))

        val page = (api.listExpenses("g1") as SpliitResult.Success).value

        assertEquals(1, page.expenses.size)
        val expense = page.expenses.single()
        assertEquals(4_250L, expense.amount)
        assertEquals(LocalDate.of(2026, 9, 4), expense.expenseDate)
        assertEquals(SplitMode.BY_PERCENTAGE, expense.splitMode)
        // 60% / 40%, stored scaled by 100.
        assertEquals(listOf(6_000L, 4_000L), expense.paidFor.map { it.shares })
        assertEquals("Groceries", expense.category?.name)
        assertEquals(2, expense.documentCount)
        assertTrue(page.hasMore)
    }

    @Test
    fun `groups list parses the createdAt the server already stringified`() = runTest {
        server.enqueue(
            ok(
                """{"result":{"data":{"json":{"groups":[{"id":"g1","name":"Trip","information":null,""" +
                    """"currency":"€","currencyCode":"EUR","createdAt":"2026-01-02T03:04:05.000Z",""" +
                    """"_count":{"participants":3}}]}}}}""",
            ),
        )

        val groups = (api.listGroups(listOf("g1")) as SpliitResult.Success).value

        assertEquals(Instant.parse("2026-01-02T03:04:05Z"), groups.single().createdAt)
        assertEquals(3, groups.single().participantCount)
    }

    @Test
    fun `balances are returned as a list keyed by participant`() = runTest {
        server.enqueue(
            ok(
                """{"result":{"data":{"json":{"balances":{"p1":{"paid":2125,"paidFor":0,"total":2125},""" +
                    """"p2":{"paid":0,"paidFor":2125,"total":-2125}},""" +
                    """"reimbursements":[{"from":"p2","to":"p1","amount":2125}]}}}}""",
            ),
        )

        val balances = (api.listBalances("g1") as SpliitResult.Success).value

        assertEquals(2, balances.balances.size)
        assertEquals(-2125L, balances.balances.single { it.participantId == "p2" }.total)
        assertEquals("p2", balances.reimbursements.single().fromParticipantId)
    }

    @Test
    fun `a procedure returning nothing succeeds`() = runTest {
        server.enqueue(ok("""{"result":{"data":{}}}"""))

        val result = api.updateGroup(
            groupId = "g1",
            group = GroupInput("Trip", "€", "EUR", listOf(ParticipantInput(name = "Ada"))),
        )

        assertEquals(SpliitResult.Success(Unit), result)
    }

    // --- errors ------------------------------------------------------------

    @Test
    fun `a tRPC NOT_FOUND becomes the domain's own not-found`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(404).setBody(
                """{"error":{"json":{"message":"Group not found.","code":-32004,""" +
                    """"data":{"code":"NOT_FOUND","httpStatus":404,"path":"groups.getDetails"}}}}""",
            ),
        )

        val error = (api.getGroupDetails("nope") as SpliitResult.Failure).error

        // Recognised here, so that no screen has to know tRPC spells it
        // "NOT_FOUND". Two ViewModels used to compare that string by hand.
        assertEquals(SpliitError.NotFound, error)
    }

    @Test
    fun `any other tRPC error becomes a procedure failure carrying its code`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(400).setBody(
                """{"error":{"json":{"message":"Invalid input.","code":-32600,""" +
                    """"data":{"code":"BAD_REQUEST","httpStatus":400,"path":"groups.update"}}}}""",
            ),
        )

        val error = (api.getGroupDetails("g1") as SpliitResult.Failure).error

        assertEquals(
            SpliitError.Procedure(code = "BAD_REQUEST", message = "Invalid input."),
            error,
        )
    }

    @Test
    fun `a response that is not tRPC at all becomes an http failure`() = runTest {
        server.enqueue(MockResponse().setResponseCode(502).setBody("<html>Bad Gateway</html>"))

        val error = (api.getGroup("g1") as SpliitResult.Failure).error

        assertEquals(502, (error as SpliitError.Http).status)
    }

    @Test
    fun `an unknown split mode fails rather than guessing`() = runTest {
        server.enqueue(ok(EXPENSES_RESPONSE.replace("BY_PERCENTAGE", "BY_MOON_PHASE")))

        val error = (api.listExpenses("g1") as SpliitResult.Failure).error

        assertTrue(error is SpliitError.Malformed)
    }

    @Test
    fun `an unreachable server becomes a network failure`() = runTest {
        server.shutdown()

        val error = (api.getGroup("g1") as SpliitResult.Failure).error

        assertTrue(error is SpliitError.Network)
    }

    private fun ok(body: String) = MockResponse().setResponseCode(200).setBody(body)

    private fun RecordedRequest.inputParameter(): String? =
        requestUrl?.queryParameter("input")?.let { URLDecoder.decode(it, "UTF-8") }

    private companion object {
        /**
         * A real superjson response: participants carry a `groupId` we do not
         * model, and `createdAt` is annotated in `meta`.
         */
        const val GROUP_RESPONSE = """
            {"result":{"data":{
              "json":{"group":{"id":"g1","name":"Trip","information":null,"currency":"€",
                "currencyCode":"EUR","createdAt":"2026-01-02T03:04:05.000Z",
                "participants":[{"id":"p1","name":"Ada","groupId":"g1"},
                                {"id":"p2","name":"Alan","groupId":"g1"}]}},
              "meta":{"values":{"group.createdAt":["Date"]}}}}}
        """

        const val EXPENSES_RESPONSE = """
            {"result":{"data":{
              "json":{"expenses":[{"id":"e1","title":"Dinner","amount":4250,
                "originalAmount":null,"originalCurrency":null,
                "expenseDate":"2026-09-04T00:00:00.000Z","createdAt":"2026-09-04T18:00:00.000Z",
                "category":{"id":7,"grouping":"Food and Drink","name":"Groceries"},
                "paidBy":{"id":"p1","name":"Ada"},
                "paidFor":[{"participant":{"id":"p1","name":"Ada"},"shares":6000},
                           {"participant":{"id":"p2","name":"Alan"},"shares":4000}],
                "splitMode":"BY_PERCENTAGE","recurrenceRule":"NONE","isReimbursement":false,
                "_count":{"documents":2}}],
              "hasMore":true,"nextCursor":10},
              "meta":{"values":{"expenses.0.expenseDate":["Date"],"expenses.0.createdAt":["Date"]}}}}}
        """
    }
}
