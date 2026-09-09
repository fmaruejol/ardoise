package io.github.fmaruejol.ardoise.ui

import java.net.URI

/**
 * The host of a base URL, for the places that name a server to the user.
 *
 * `https://spliit.app/` reads as `spliit.app`: the scheme and the trailing
 * slash are noise in a sentence, and the host is the part someone recognises.
 * Falls back to the address as given if it cannot be parsed, which is better
 * than showing nothing.
 */
internal fun String.host(): String =
    runCatching { URI(this).host }.getOrNull() ?: trim().removeSuffix("/")
