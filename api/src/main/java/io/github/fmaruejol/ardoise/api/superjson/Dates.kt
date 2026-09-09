package io.github.fmaruejol.ardoise.api.superjson

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * An instant on its way *out*. Serialises to a marker that
 * [SuperJson.envelope] replaces with an ISO string plus its `meta.values`
 * annotation, so the annotations follow the tree rather than a list of paths.
 */
@Serializable(with = SuperJsonDateSerializer::class)
internal data class SuperJsonDate(val value: Instant) {
    companion object {
        /**
         * Upstream sends an expense date as UTC midnight. Local midnight
         * would move the expense by a day for anyone east of UTC.
         */
        fun ofDate(date: LocalDate): SuperJsonDate =
            SuperJsonDate(date.atStartOfDay(ZoneOffset.UTC).toInstant())
    }
}

internal object SuperJsonDateSerializer : KSerializer<SuperJsonDate> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("io.github.fmaruejol.ardoise.api.superjson.SuperJsonDate", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: SuperJsonDate) {
        val jsonEncoder = requireNotNull(encoder as? JsonEncoder) {
            "SuperJsonDate is only meaningful inside a superjson envelope"
        }
        jsonEncoder.encodeJsonElement(
            buildJsonObject {
                put(
                    SuperJson.DATE_MARKER_KEY,
                    JsonPrimitive(DateTimeFormatter.ISO_INSTANT.format(value.value)),
                )
            },
        )
    }

    override fun deserialize(decoder: Decoder): SuperJsonDate =
        SuperJsonDate(Instant.parse(decoder.decodeString()))
}

/** An instant on its way *in*. Responses carry plain ISO strings. */
internal object InstantSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("java.time.Instant", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Instant) {
        encoder.encodeString(DateTimeFormatter.ISO_INSTANT.format(value))
    }

    override fun deserialize(decoder: Decoder): Instant = Instant.parse(decoder.decodeString())
}

/** The calendar date of an instant the server produced at UTC midnight. */
internal fun Instant.toUtcLocalDate(): LocalDate = atZone(ZoneOffset.UTC).toLocalDate()
