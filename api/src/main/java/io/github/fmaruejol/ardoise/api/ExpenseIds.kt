package io.github.fmaruejol.ardoise.api

import java.security.SecureRandom

/**
 * Ids for the expenses this client creates, in upstream's own format: 21
 * characters of `[A-Za-z0-9_-]`, which `groups.expenses.create` takes from
 * Spliit 1.26.
 *
 * Minted before the first send and kept across every retry, so that a send
 * which landed and lost its answer can be looked up by id instead of guessed
 * at. An older instance ignores the id and mints its own.
 */
object ExpenseIds {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_-"
    private const val LENGTH = 21
    private val random = SecureRandom()

    fun random(): String = buildString(LENGTH) {
        repeat(LENGTH) { append(ALPHABET[random.nextInt(ALPHABET.length)]) }
    }
}
