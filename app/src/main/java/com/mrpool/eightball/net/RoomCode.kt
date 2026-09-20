package com.mrpool.eightball.net

import kotlin.random.Random

/**
 * The short code a player reads out to a friend so they can join the same room.
 *
 * Letters and digits that cannot be confused with each other over a phone call: no O or 0,
 * no I, L or 1, no S or 5. Five characters of this alphabet is about 20 bits, which is
 * plenty for rooms that live for one match.
 */
object RoomCode {

    const val LENGTH = 5
    private const val ALPHABET = "ABCDEFGHJKMNPQRTUVWXYZ2346789"

    fun generate(random: Random = Random.Default): String =
        buildString(LENGTH) {
            repeat(LENGTH) { append(ALPHABET[random.nextInt(ALPHABET.length)]) }
        }

    /**
     * Tidies up what a player typed: trims, upper cases, and maps the characters people
     * habitually substitute onto the ones actually used.
     */
    fun normalise(input: String): String = buildString {
        for (char in input.trim().uppercase()) {
            val mapped = when (char) {
                'O' -> 'Q'
                '0' -> 'Q'
                'I', 'L' -> 'J'
                '1' -> 'J'
                'S' -> 'Z'
                '5' -> 'Z'
                else -> char
            }
            if (mapped in ALPHABET) append(mapped)
        }
    }

    fun isValid(code: String): Boolean =
        code.length == LENGTH && code.all { it in ALPHABET }
}
