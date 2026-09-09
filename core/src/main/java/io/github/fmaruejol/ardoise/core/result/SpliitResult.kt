package io.github.fmaruejol.ardoise.core.result

/** Errors from :api are values, never exceptions across a module boundary. */
sealed interface SpliitResult<out T> {
    data class Success<out T>(val value: T) : SpliitResult<T>

    data class Failure(val error: SpliitError) : SpliitResult<Nothing>
}

sealed interface SpliitError {
    /** The request never produced a response: no connectivity, DNS, timeout. */
    data class Network(val cause: Throwable) : SpliitError

    /** A response arrived, but not one tRPC produced (a proxy or an error page). */
    data class Http(val status: Int, val body: String?) : SpliitError

    /**
     * There is no such group, expense or participant: a state to render
     * rather than a failure to apologise for. Screens branch on it.
     */
    data object NotFound : SpliitError

    /**
     * The server rejected the call; [code] is the tRPC code. `NOT_FOUND` is
     * [NotFound] instead and never arrives here.
     */
    data class Procedure(
        val code: String,
        val message: String,
    ) : SpliitError

    /** The response did not match what this client expects. */
    data class Malformed(val cause: Throwable) : SpliitError
}

inline fun <T, R> SpliitResult<T>.map(transform: (T) -> R): SpliitResult<R> = when (this) {
    is SpliitResult.Success -> SpliitResult.Success(transform(value))
    is SpliitResult.Failure -> this
}

/**
 * Maps the value, turning anything the transform throws into
 * [SpliitError.Malformed]. This is where DTO to domain mapping belongs: a
 * response that does not fit our model is a decoding failure, not a crash.
 */
inline fun <T, R> SpliitResult<T>.mapCatching(transform: (T) -> R): SpliitResult<R> = when (this) {
    is SpliitResult.Success -> {
        try {
            SpliitResult.Success(transform(value))
        } catch (e: Exception) {
            SpliitResult.Failure(SpliitError.Malformed(e))
        }
    }

    is SpliitResult.Failure -> {
        this
    }
}

inline fun <T, R> SpliitResult<T>.fold(
    onSuccess: (T) -> R,
    onFailure: (SpliitError) -> R,
): R = when (this) {
    is SpliitResult.Success -> onSuccess(value)
    is SpliitResult.Failure -> onFailure(error)
}

fun <T> SpliitResult<T>.getOrNull(): T? = (this as? SpliitResult.Success)?.value
