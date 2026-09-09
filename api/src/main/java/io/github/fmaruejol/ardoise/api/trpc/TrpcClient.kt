package io.github.fmaruejol.ardoise.api.trpc

import io.github.fmaruejol.ardoise.api.SpliitJson
import io.github.fmaruejol.ardoise.api.superjson.SuperJson
import io.github.fmaruejol.ardoise.api.superjson.intOrNull
import io.github.fmaruejol.ardoise.api.superjson.stringOrNull
import io.github.fmaruejol.ardoise.api.superjson.trpcErrorOrNull
import io.github.fmaruejol.ardoise.api.superjson.trpcResultEnvelope
import io.github.fmaruejol.ardoise.core.result.SpliitError
import io.github.fmaruejol.ardoise.core.result.SpliitResult
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlin.coroutines.cancellation.CancellationException

/**
 * Where the instance lives, resolved per request so a change of server takes
 * effect without a restart.
 */
fun interface BaseUrlProvider {
    suspend fun baseUrl(): String
}

/**
 * Transport for Spliit's tRPC endpoint at `{baseUrl}api/trpc`, the only way
 * in, queries are `GET …/{procedure}?input=<envelope>`, mutations `POST` with
 * the envelope as the body.
 *
 * **Both unbatched.** Batching is a different wire format that nothing here
 * parses. Do not add it.
 */
class TrpcClient(
    private val httpClient: HttpClient,
    private val baseUrl: BaseUrlProvider,
) {
    private val json: Json = SpliitJson

    suspend fun <O> query(
        procedure: String,
        deserializer: DeserializationStrategy<O>,
        input: JsonElement? = null,
    ): SpliitResult<O> = call(Method.GET, procedure, deserializer, input)

    suspend fun <O> mutation(
        procedure: String,
        deserializer: DeserializationStrategy<O>,
        input: JsonElement? = null,
    ): SpliitResult<O> = call(Method.POST, procedure, deserializer, input)

    private suspend fun <O> call(
        method: Method,
        procedure: String,
        deserializer: DeserializationStrategy<O>,
        input: JsonElement?,
    ): SpliitResult<O> {
        val response = try {
            execute(method, procedure, input)
        } catch (e: CancellationException) {
            throw e
        } catch (e: SerializationException) {
            return SpliitResult.Failure(SpliitError.Malformed(e))
        } catch (e: Exception) {
            // No response at all: connectivity, DNS, TLS, or a timeout.
            return SpliitResult.Failure(SpliitError.Network(e))
        }

        val body = try {
            response.bodyAsText()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return SpliitResult.Failure(SpliitError.Network(e))
        }

        val root = try {
            json.parseToJsonElement(body)
        } catch (e: SerializationException) {
            // Not tRPC's doing, a proxy, a captive portal, an error page.
            return SpliitResult.Failure(SpliitError.Http(response.status.value, body))
        }

        root.trpcErrorOrNull()?.let { return SpliitResult.Failure(it.toProcedureError()) }

        if (!response.status.isSuccess()) {
            return SpliitResult.Failure(SpliitError.Http(response.status.value, body))
        }

        val envelope = root.trpcResultEnvelope()
            ?: return SpliitResult.Failure(
                SpliitError.Malformed(IllegalStateException("Response has neither result nor error")),
            )

        return try {
            SpliitResult.Success(json.decodeFromJsonElement(deserializer, SuperJson.payload(envelope)))
        } catch (e: SerializationException) {
            SpliitResult.Failure(SpliitError.Malformed(e))
        } catch (e: IllegalArgumentException) {
            SpliitResult.Failure(SpliitError.Malformed(e))
        }
    }

    private suspend fun execute(
        method: Method,
        procedure: String,
        input: JsonElement?,
    ): HttpResponse {
        val url = "${normalisedBaseUrl()}api/trpc/$procedure"
        return when (method) {
            Method.GET -> httpClient.get(url) {
                if (input != null) parameter("input", encodeEnvelope(input))
            }

            Method.POST -> httpClient.post(url) {
                contentType(ContentType.Application.Json)
                setBody(encodeEnvelope(input ?: JsonObject(emptyMap())))
            }
        }
    }

    private fun encodeEnvelope(input: JsonElement): String =
        json.encodeToString(JsonElement.serializer(), SuperJson.envelope(input))

    private suspend fun normalisedBaseUrl(): String =
        baseUrl.baseUrl().let { if (it.endsWith("/")) it else "$it/" }

    private enum class Method { GET, POST }
}

/**
 * tRPC reports a rejected call in the body: the useful `code` is under `data`,
 * while the top-level one is a JSON-RPC number that says much less.
 */
private fun JsonObject.toProcedureError(): SpliitError {
    val data = this["data"] as? JsonObject
    val code = data?.stringOrNull("code") ?: "INTERNAL_SERVER_ERROR"
    // Recognised here, so no screen has to know tRPC spells it "NOT_FOUND".
    if (code == NOT_FOUND) return SpliitError.NotFound
    return SpliitError.Procedure(
        code = code,
        message = stringOrNull("message").orEmpty(),
    )
}

/** tRPC's code for "there is no such thing", which becomes [SpliitError.NotFound]. */
private const val NOT_FOUND = "NOT_FOUND"
