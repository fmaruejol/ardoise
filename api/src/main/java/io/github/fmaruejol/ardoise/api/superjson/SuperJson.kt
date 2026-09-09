package io.github.fmaruejol.ardoise.api.superjson

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject

/**
 * The superjson envelope: `{ "json": <payload>, "meta": { "values": ... } }`,
 * where `meta.values` maps a path inside `json` to the type it really had.
 *
 * The directions are deliberately asymmetric. **Decoding ignores `meta`**,
 * since the models are typed. **Encoding must emit it**: the server rebuilds real
 * `Date` instances from the annotations before its own validation runs.
 */
internal object SuperJson {
    /** Wraps [input], hoisting every [SuperJsonDate] marker into `meta.values`. */
    fun envelope(input: JsonElement): JsonObject {
        val annotations = mutableMapOf<String, JsonElement>()
        val payload = hoistAnnotations(input, path = emptyList(), into = annotations)
        return buildJsonObject {
            put("json", payload)
            if (annotations.isNotEmpty()) {
                put(
                    "meta",
                    buildJsonObject {
                        // A root value carries the type array itself.
                        val root = annotations[ROOT_PATH]
                        if (root != null) put("values", root) else put("values", JsonObject(annotations))
                    },
                )
            }
        }
    }

    /**
     * Unwraps a response envelope. A procedure that returns nothing produces
     * `{}` rather than a `json` key, which is what `Unit` decodes from.
     */
    fun payload(envelope: JsonElement): JsonElement =
        (envelope as? JsonObject)?.get("json") ?: JsonObject(emptyMap())

    private fun hoistAnnotations(
        element: JsonElement,
        path: List<String>,
        into: MutableMap<String, JsonElement>,
    ): JsonElement = when (element) {
        is JsonObject -> {
            val marker = element[DATE_MARKER_KEY]
            if (element.size == 1 && marker is JsonPrimitive && marker.isString) {
                into[pathKey(path)] = DATE_ANNOTATION
                marker
            } else {
                JsonObject(
                    element.mapValues { (key, value) ->
                        hoistAnnotations(value, path + key, into)
                    },
                )
            }
        }

        is JsonArray -> {
            JsonArray(
                element.mapIndexed { index, value ->
                    hoistAnnotations(value, path + index.toString(), into)
                },
            )
        }

        else -> {
            element
        }
    }

    /** superjson joins path segments with `.`, so a segment containing one escapes it. */
    private fun pathKey(path: List<String>): String =
        path.joinToString(".") { it.replace("\\", "\\\\").replace(".", "\\.") }

    private const val ROOT_PATH = ""

    private val DATE_ANNOTATION = JsonArray(listOf(JsonPrimitive("Date")))

    /** Written by [SuperJsonDateSerializer], removed by [envelope]. */
    internal const val DATE_MARKER_KEY: String = "\$superjson\$Date"
}

/** The error of a tRPC response, unwrapped, or null when the call succeeded. */
internal fun JsonElement.trpcErrorOrNull(): JsonObject? =
    (this as? JsonObject)?.get("error")?.let { SuperJson.payload(it) as? JsonObject }

/** The `result.data` envelope of a tRPC response. */
internal fun JsonElement.trpcResultEnvelope(): JsonElement? =
    (this as? JsonObject)?.get("result")?.jsonObject?.get("data")

internal fun JsonObject.stringOrNull(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

internal fun JsonObject.intOrNull(key: String): Int? =
    (this[key] as? JsonPrimitive)?.content?.toIntOrNull()
