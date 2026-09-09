package io.github.fmaruejol.ardoise.api.superjson

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class SuperJsonTest {
    private val json = Json

    @Serializable
    private data class Nested(val expenseDate: SuperJsonDate, val title: String)

    @Serializable
    private data class Wrapper(val groupId: String, val expenseFormValues: Nested)

    @Serializable
    private data class WithList(val entries: List<Nested>)

    @Test
    fun `annotates a nested date with its dotted path`() {
        val input = json.encodeToJsonElement(
            Wrapper.serializer(),
            Wrapper(
                groupId = "g1",
                expenseFormValues = Nested(
                    expenseDate = SuperJsonDate.ofDate(LocalDate.of(2026, 9, 4)),
                    title = "Dinner",
                ),
            ),
        )

        val envelope = SuperJson.envelope(input)

        assertEquals(
            """{"groupId":"g1","expenseFormValues":{"expenseDate":"2026-09-04T00:00:00Z","title":"Dinner"}}""",
            envelope["json"].toString(),
        )
        assertEquals(
            """{"values":{"expenseFormValues.expenseDate":["Date"]}}""",
            envelope["meta"].toString(),
        )
    }

    @Test
    fun `annotates dates inside arrays by index`() {
        val input = json.encodeToJsonElement(
            WithList.serializer(),
            WithList(
                entries = listOf(
                    Nested(SuperJsonDate(Instant.parse("2026-01-01T00:00:00Z")), "a"),
                    Nested(SuperJsonDate(Instant.parse("2026-02-01T00:00:00Z")), "b"),
                ),
            ),
        )

        val values = (SuperJson.envelope(input)["meta"] as JsonObject)["values"] as JsonObject

        assertEquals(
            setOf("entries.0.expenseDate", "entries.1.expenseDate"),
            values.keys,
        )
    }

    @Test
    fun `omits meta entirely when there is no date to annotate`() {
        val envelope = SuperJson.envelope(buildJsonObject { put("groupId", json.parseToJsonElement("\"g1\"")) })

        assertEquals("""{"json":{"groupId":"g1"}}""", envelope.toString())
        assertNull(envelope["meta"])
    }

    @Test
    fun `unwraps a response payload and ignores its annotations`() {
        val response = json.parseToJsonElement(
            """{"json":{"createdAt":"2026-01-02T03:04:05.000Z"},"meta":{"values":{"createdAt":["Date"]}}}""",
        )

        assertEquals("""{"createdAt":"2026-01-02T03:04:05.000Z"}""", SuperJson.payload(response).toString())
    }

    @Test
    fun `a procedure returning nothing unwraps to an empty object`() {
        assertEquals("{}", SuperJson.payload(json.parseToJsonElement("{}")).toString())
    }

    @Test
    fun `sends an expense date as UTC midnight, matching the web client`() {
        val date = SuperJsonDate.ofDate(LocalDate.of(2026, 3, 1))

        assertEquals(Instant.parse("2026-03-01T00:00:00Z"), date.value)
    }
}
