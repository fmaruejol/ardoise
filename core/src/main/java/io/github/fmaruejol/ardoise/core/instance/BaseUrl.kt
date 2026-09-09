package io.github.fmaruejol.ardoise.core.instance

import io.github.fmaruejol.ardoise.core.prefs.GroupPreferences
import java.net.URI
import java.net.URISyntaxException

/**
 * The address of a Spliit instance: what the user typed, normalised into a
 * base URL, or why it cannot be one.
 */
object BaseUrl {
    /** The instance run by the Spliit project, and the default. */
    const val CLOUD: String = GroupPreferences.DEFAULT_BASE_URL

    sealed interface Result {
        /** Always ends in `/`, because the API layer appends `api/trpc` to it. */
        data class Valid(val baseUrl: String) : Result

        data class Invalid(val reason: Reason) : Result
    }

    enum class Reason {
        Empty,

        /** Not a URL at all, spaces, control characters, nothing parseable. */
        Malformed,

        /** Parseable but with nothing to connect to, e.g. `https://`. */
        MissingHost,

        /** A scheme that is not `http` or `https`. */
        UnsupportedScheme,
    }

/**
     * Normalises [input]. Forgiving about a missing scheme, case and slashes;
     * `http` is allowed, since a self-hosted instance often has no certificate.
     */
    fun normalise(input: String): Result {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return Result.Invalid(Reason.Empty)

        // Without a scheme a URI reads the whole thing as a path, so the host
        // comes back null.
        val withScheme = if (SCHEME.containsMatchIn(trimmed)) trimmed else "https://$trimmed"

        val uri = try {
            URI(withScheme)
        } catch (e: URISyntaxException) {
            // A bare "https://" does not parse at all, and "nothing to
            // connect to" is the more useful thing to say.
            return if (SCHEME.matchEntire(withScheme) != null) {
                Result.Invalid(Reason.MissingHost)
            } else {
                Result.Invalid(Reason.Malformed)
            }
        }

        val scheme = uri.scheme?.lowercase() ?: return Result.Invalid(Reason.Malformed)
        if (scheme != "http" && scheme != "https") return Result.Invalid(Reason.UnsupportedScheme)

        val host = uri.host?.lowercase()
        if (host.isNullOrEmpty()) return Result.Invalid(Reason.MissingHost)

        val port = if (uri.port == -1) "" else ":${uri.port}"
        // A query or fragment is meaningless on a base URL.
        val path = uri.path.orEmpty().trimEnd('/')

        return Result.Valid("$scheme://$host$port$path/")
    }

    /** Whether [baseUrl] points at the project's own instance. */
    fun isCloud(baseUrl: String): Boolean =
        (normalise(baseUrl) as? Result.Valid)?.baseUrl == CLOUD

    private val SCHEME = Regex("^[a-zA-Z][a-zA-Z0-9+.\\-]*://")
}
