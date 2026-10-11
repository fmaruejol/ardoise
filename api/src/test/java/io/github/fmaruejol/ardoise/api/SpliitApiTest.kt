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
            expenseId = "V1StGXR8_Z5jdHi6B-myT",
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
    fun `no original currency is sent as null, so an update clears the old one`() = runTest {
        repeat(2) { server.enqueue(ok("""{"result":{"data":{"json":{"expenseId":"e1"}}}}""")) }
        val expense = ExpenseInput(
            title = "Dinner",
            amount = 892,
            expenseDate = LocalDate.of(2026, 10, 1),
            paidById = "p1",
            paidFor = listOf(PaidFor("p1", 100)),
            splitMode = SplitMode.EVENLY,
        )

        api.updateExpense("g1", "e1", expense)
        api.updateExpense(
            "g1",
            "e1",
            expense.copy(originalAmount = 1000, originalCurrency = "USD", conversionRate = "0.89238"),
        )

        val sent = List(2) {
            val body = Json.parseToJsonElement(server.takeRequest().body.readUtf8()) as JsonObject
            ((body["json"] as JsonObject)["expenseFormValues"] as JsonObject)["originalCurrency"].toString()
        }
        assertEquals(listOf("null", "\"USD\""), sent)
    }

    @Test
    fun `an empty note is sent, and a null one is left out`() = runTest {
        repeat(2) { server.enqueue(ok("""{"result":{"data":{"json":{"expenseId":"e1"}}}}""")) }
        val expense = ExpenseInput(
            title = "Dinner",
            amount = 4_250,
            expenseDate = LocalDate.of(2026, 9, 4),
            paidById = "p1",
            paidFor = listOf(PaidFor("p1", 100)),
            splitMode = SplitMode.EVENLY,
        )

        api.updateExpense("g1", "e1", expense.copy(notes = ""))
        api.updateExpense("g1", "e1", expense.copy(notes = null))

        // On an update a missing field is "unchanged", so only "" clears a note.
        val values = List(2) {
            val body = Json.parseToJsonElement(server.takeRequest().body.readUtf8()) as JsonObject
            ((body["json"] as JsonObject)["expenseFormValues"] as JsonObject)["notes"]?.toString()
        }
        assertEquals(listOf("\"\"", null), values)
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

    // --- stats -------------------------------------------------------------

    @Test
    fun `the stats overview gives the total and its breakdown`() = runTest {
        server.enqueue(ok(STATS_OVERVIEW_RESPONSE))

        val stats = (api.groupStats("g1") as SpliitResult.Success).value

        assertEquals("/api/trpc/groups.stats.overview", server.takeRequest().requestUrl?.encodedPath)
        assertEquals(57_600L, stats.total)
        val breakdown = stats.breakdown!!
        assertEquals(LocalDate.of(2026, 9, 10), breakdown.firstDate)
        assertEquals(LocalDate.of(2026, 9, 14), breakdown.lastDate)
        assertEquals(listOf("Rent", "General"), breakdown.byCategory.map { it.category.name })
        assertEquals(listOf(48_000L, 9_600L), breakdown.byCategory.map { it.total })
        assertEquals(
            listOf("p1" to 48_000L, "p2" to 9_600L),
            breakdown.byParticipant.map {
                it.participantId to it.paid
            },
        )
    }

    @Test
    fun `an instance older than the overview is asked by the old name`() = runTest {
        server.enqueue(noProcedure("groups.stats.overview"))
        server.enqueue(ok("""{"result":{"data":{"json":{"totalGroupSpendings":57600}}}}"""))

        val stats = (api.groupStats("g1") as SpliitResult.Success).value

        server.takeRequest()
        assertEquals("/api/trpc/groups.stats.get", server.takeRequest().requestUrl?.encodedPath)
        assertEquals(57_600L, stats.total)
        assertNull(stats.breakdown)
    }

    @Test
    fun `an instance with neither says it has no stats`() = runTest {
        server.enqueue(noProcedure("groups.stats.overview"))
        server.enqueue(noProcedure("groups.stats.get"))

        assertEquals(SpliitError.NotFound, (api.groupStats("g1") as SpliitResult.Failure).error)
    }

    @Test
    fun `only a missing procedure falls back`() = runTest {
        server.enqueue(MockResponse().setResponseCode(502).setBody("<html>Bad Gateway</html>"))

        val error = (api.groupStats("g1") as SpliitResult.Failure).error

        assertEquals(502, (error as SpliitError.Http).status)
        assertEquals(1, server.requestCount)
    }

    // --- paging past the server's cap ---------------------------------------

    @Test
    fun `a limit over 100 is split into requests the server accepts`() = runTest {
        server.enqueue(expensePage("e1", hasMore = true))
        server.enqueue(expensePage("e2", hasMore = true))
        server.enqueue(expensePage("e3", hasMore = true))

        val page = (api.listExpenses("g1", cursor = 0, limit = 250) as SpliitResult.Success).value

        assertEquals(listOf(0 to 100, 100 to 100, 200 to 50), List(3) { server.takeRequest().cursorAndLimit() })
        assertEquals(listOf("e1", "e2", "e3"), page.expenses.map { it.id })
        assertTrue(page.hasMore)
    }

    @Test
    fun `paging stops at the last page the server has`() = runTest {
        server.enqueue(expensePage("e1", hasMore = false))

        val page = (api.listExpenses("g1", cursor = 0, limit = 1000) as SpliitResult.Success).value

        assertEquals(1, server.requestCount)
        assertEquals(false, page.hasMore)
    }

    @Test
    fun `a page that fails fails the whole list`() = runTest {
        server.enqueue(expensePage("e1", hasMore = true))
        server.enqueue(MockResponse().setResponseCode(502).setBody("<html>Bad Gateway</html>"))

        val result = api.listExpenses("g1", cursor = 0, limit = 200)

        assertTrue(result is SpliitResult.Failure)
    }

    @Test
    fun `a row pushed into the next page by a new expense is counted once`() = runTest {
        server.enqueue(expensePage("e1", hasMore = true))
        server.enqueue(expensePage("e1", hasMore = false))

        val page = (api.listExpenses("g1", cursor = 0, limit = 200) as SpliitResult.Success).value

        assertEquals(listOf("e1"), page.expenses.map { it.id })
    }

    @Test
    fun `an activity says whether its expense still exists`() = runTest {
        server.enqueue(
            ok(
                """{"result":{"data":{"json":{"activities":[""" +
                    """{"id":"a1","groupId":"g1","time":"2026-10-11T10:00:00.000Z","activityType":"CREATE_EXPENSE",""" +
                    """"expenseId":"e1","data":"Taxi","expense":{"id":"e1","title":"Taxi"}},""" +
                    """{"id":"a2","groupId":"g1","time":"2026-10-11T09:00:00.000Z","activityType":"CREATE_EXPENSE",""" +
                    """"expenseId":"e2","data":"Gone","expense":null}],"hasMore":false},""" +
                    """"meta":{"values":{"activities.0.time":["Date"],"activities.1.time":["Date"],""" +
                    """"activities.1.expense":["undefined"]}}}}}""",
            ),
        )

        val activities = (api.listActivities("g1") as SpliitResult.Success).value.activities

        assertEquals(listOf(true, false), activities.map { it.expenseExists })
    }

    @Test
    fun `the activity log pages past the cap too`() = runTest {
        repeat(2) { server.enqueue(ok("""{"result":{"data":{"json":{"activities":[],"hasMore":true}}}}""")) }

        api.listActivities("g1", cursor = 0, limit = 120)

        assertEquals(listOf(0 to 100, 100 to 20), List(2) { server.takeRequest().cursorAndLimit() })
    }

    @Test
    fun `more than 100 groups are asked for in batches the server accepts`() = runTest {
        repeat(2) { server.enqueue(ok("""{"result":{"data":{"json":{"groups":[]}}}}""")) }

        api.listGroups((1..150).map { "g$it" })

        val sizes = List(2) { server.takeRequest().groupIdCount("groupIds") }
        assertEquals(listOf(100, 50), sizes)
    }

    @Test
    fun `balances across more than 100 groups are asked for in batches`() = runTest {
        repeat(2) { server.enqueue(ok("""{"result":{"data":{"json":{"balances":[]}}}}""")) }

        api.balancesForUser((1..101).map { "g$it" to "p" })

        val sizes = List(2) { server.takeRequest().groupIdCount("groups") }
        assertEquals(listOf(100, 1), sizes)
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

    private fun noProcedure(path: String) = MockResponse().setResponseCode(404).setBody(
        """{"error":{"json":{"message":"No procedure found on path $path","code":-32004,""" +
            """"data":{"code":"NOT_FOUND","httpStatus":404,"path":"$path"}}}}""",
    )

    private fun expensePage(id: String, hasMore: Boolean) = ok(
        EXPENSES_RESPONSE.replace("\"e1\"", "\"$id\"").replace("\"hasMore\":true", "\"hasMore\":$hasMore"),
    )

    private fun RecordedRequest.groupIdCount(field: String): Int {
        val input = Json.parseToJsonElement(inputParameter()!!) as JsonObject
        return ((input["json"] as JsonObject)[field] as kotlinx.serialization.json.JsonArray).size
    }

    private fun RecordedRequest.cursorAndLimit(): Pair<Int, Int> {
        val input = Json.parseToJsonElement(inputParameter()!!) as JsonObject
        val json = input["json"] as JsonObject
        return json["cursor"].toString().toInt() to json["limit"].toString().toInt()
    }

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

        /** Trimmed from a real answer: what is not read is left out. */
        const val STATS_OVERVIEW_RESPONSE = """
            {"result":{"data":{"json":{"totalGroupSpendings":57600,
              "totalParticipantSpendings":null,"totalParticipantShare":null,
              "summary":{"expenseCount":2,"totalSpending":57600,"averageExpense":28800,
                "largestExpense":{"title":"Rent","amount":48000},
                "firstDate":"2026-09-10","lastDate":"2026-09-14"},
              "months":[{"month":"2026-09","total":57600}],
              "participants":[{"participantId":"p1","name":"Ada","paid":48000,"paidCount":1,"share":28800},
                              {"participantId":"p2","name":"Alan","paid":9600,"paidCount":1,"share":28800}],
              "categories":[{"categoryId":1,"grouping":"Home","name":"Rent","total":48000},
                            {"categoryId":0,"grouping":"Uncategorized","name":"General","total":9600}],
              "recurring":{"count":0,"byPeriod":[],"estimatedMonthly":0,"estimatedYearly":0}}}}}
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
