package com.mrpool.server

import kotlin.random.Random

/**
 * The short code one player reads out to another.
 *
 * Deliberately identical to the client's own alphabet: letters and digits that cannot be
 * confused over a phone call, with no O or 0, no I, L or 1, and no S or 5.
 */
object RoomCode {

    const val LENGTH = 5
    private const val ALPHABET = "ABCDEFGHJKMNPQRTUVWXYZ2346789"

    fun generate(random: Random = Random.Default): String =
        buildString(LENGTH) {
            repeat(LENGTH) { append(ALPHABET[random.nextInt(ALPHABET.length)]) }
        }

    fun normalise(input: String): String = buildString {
        for (char in input.trim().uppercase()) {
            val mapped = when (char) {
                'O', '0' -> 'Q'
                'I', 'L', '1' -> 'J'
                'S', '5' -> 'Z'
                else -> char
            }
            if (mapped in ALPHABET) append(mapped)
        }
    }

    fun isValid(code: String): Boolean = code.length == LENGTH && code.all { it in ALPHABET }
}
