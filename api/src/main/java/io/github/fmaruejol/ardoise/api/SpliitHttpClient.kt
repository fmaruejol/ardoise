package io.github.fmaruejol.ardoise.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Somewhere to send HTTP diagnostics. Failures here are values rather than
 * exceptions, so without this they leave no trace anywhere.
 */
fun interface HttpLogger {
    fun log(message: String)
}

/**
 * JSON for the tRPC layer. Decoding ignores superjson's `meta.values`. The
 * models are typed, so the shape is known. Encoding still emits them, where
 * the envelope is built.
 */
internal val SpliitJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}

/** :api brings the Ktor client but never an engine, which keeps it pure JVM. */
fun spliitHttpClient(
    engine: HttpClientEngine,
    logger: HttpLogger? = null,
): HttpClient = HttpClient(engine) {
    expectSuccess = false
    install(ContentNegotiation) {
        json(SpliitJson)
    }
    if (logger != null) {
        install(Logging) {
            this.logger = object : Logger {
                override fun log(message: String) = logger.log(message)
            }
            // URL and status but no headers or bodies: a URL already carries a
            // group id, and a body would carry the whole expense.
            level = LogLevel.INFO
        }
    }
}
