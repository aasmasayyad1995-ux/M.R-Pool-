package com.mrpool.eightball.net

/**
 * Cleans up what one player types before the other one reads it.
 *
 * Be clear about what this is: a speed bump, not a wall. Anyone who wants to get an insult
 * past a word list can, by spelling it a way the list does not have. What it does buy is
 * that the ordinary, lazy abuse — which is most of it — does not land, and that a player
 * who is being pestered has [OnlineMatch] mute to fall back on.
 *
 * All of it is here, away from Android and away from the network, so the awkward cases can
 * be tested rather than hoped about.
 */
object ChatFilter {

    /** Longer than this and it is not chat, it is someone pasting something. */
    const val MAX_LENGTH = 120

    /** What a blocked word is replaced with. */
    const val MASK = "●●●"

    /**
     * Trims, caps the length and collapses runs of whitespace.
     *
     * A message of only spaces, or of nothing, comes back empty — the caller is expected
     * to send nothing at all rather than an empty bubble.
     */
    fun tidy(raw: String): String =
        raw.replace(Regex("\\s+"), " ").trim().take(MAX_LENGTH)

    /**
     * The message with anything on the word list masked.
     *
     * Matching is done on a flattened copy — lower cased, with the digit-for-letter tricks
     * undone and padding characters dropped — while the reply is built from the original,
     * so "H.E.L.L.O" is not mistaken for anything and ordinary words keep their spelling.
     */
    fun clean(message: String): String {
        val words = message.split(" ")
        return words.joinToString(" ") { word ->
            if (isBlocked(word)) MASK else word
        }
    }

    /** True when [message] would be changed by [clean]. */
    fun hasBlockedWords(message: String): Boolean =
        message.split(" ").any { isBlocked(it) }

    private fun isBlocked(word: String): Boolean {
        val swapped = flatten(word)
        if (swapped.isEmpty()) return false

        // The word as typed, and the word with an ordinary ending taken off it, so that
        // "fucking" and "fucked" do not each have to be written on the list.
        val forms = forms(swapped)

        // Checked before the list, so a digit swap cannot turn an ordinary word into a
        // masked one. "sh0t" reads as "shot" here, which in a pool room it almost always
        // is, rather than as the insult one letter away from it.
        if (forms.any { it in ALLOWED }) return false
        if (forms.any { it in BLOCKED }) return true

        // Only when somebody actually reached for a digit or a symbol is the word compared
        // by its consonants alone. That is what catches "f4ck", where the digit stands for
        // a letter it does not normally stand for. Words typed in plain letters are never
        // fuzzed like this, because "shot" and "shit" share their consonants and masking
        // the commonest thing anybody says in a pool game would be far worse than letting
        // one insult through.
        if (!usedSubstitutes(word)) return false
        return forms.any {
            val skeleton = consonants(it)
            skeleton.length >= 3 && skeleton in BLOCKED_SKELETONS
        }
    }

    /**
     * The word, and its stem if it has an ending that can be taken off.
     *
     * Only one ending comes off, and only when what is left is still four letters, which
     * is what keeps the short entries on the list — "mc", "fck" — from matching the tail
     * of ordinary words.
     */
    private fun forms(word: String): List<String> {
        val stem = SUFFIXES.firstNotNullOfOrNull { suffix ->
            word.removeSuffix(suffix).takeIf { it.length < word.length && it.length >= MIN_STEM }
        }
        return if (stem == null) listOf(word) else listOf(word, stem)
    }

    /** Longest first, so "ing" is taken off before the "g" inside it would be. */
    private val SUFFIXES = listOf(
        "ings", "ing", "ers", "er", "ed", "ies", "es", "s", "y", "in", "a", "e", "i", "o", "u"
    )

    /** How short a stem may get before an ending is no longer worth taking off. */
    private const val MIN_STEM = 4

    /** True when the writer put a digit or a symbol where a letter belongs. */
    private fun usedSubstitutes(word: String): Boolean =
        word.any { it in SUBSTITUTES }

    private fun consonants(word: String): String =
        word.filterNot { it in "aeiou" }

    /** The characters people reach for when they put something where a letter belongs. */
    private const val SUBSTITUTES = "43105\$@!|78"

    /**
     * A word cut back to the letters it was meant to be read as.
     *
     * Lower cased, the usual digit-for-letter swaps undone, anything that is not a letter
     * dropped, and runs of one letter squashed, so "F.U.C.K", "FUCK" and "fuuuuck" all
     * arrive as the single spelling the list has to carry.
     */
    fun flatten(word: String): String {
        val builder = StringBuilder(word.length)
        for (typed in word.lowercase()) {
            val letter = when (typed) {
                '4', '@' -> 'a'
                '3' -> 'e'
                '1', '!', '|' -> 'i'
                '0' -> 'o'
                '5', '$' -> 's'
                '7' -> 't'
                '8' -> 'b'
                else -> typed
            }
            if (!letter.isLetter()) continue
            // Squashing here is what makes "fuuuuck" the same word as "fuck" without the
            // list having to hold every length of it.
            if (builder.isNotEmpty() && builder.last() == letter) continue
            builder.append(letter)
        }
        return builder.toString()
    }

    /**
     * The list, in flattened form, so it is compared like for like.
     *
     * English and the Hindi and Urdu abuse a player here is most likely to meet, since both
     * turn up in an Indian pool room. Doubled letters are left out because [flatten]
     * squashes them. It is not complete and cannot be; it is meant to be added to.
     */
    private val BLOCKED: Set<String> = setOf(
        // English
        "fuck", "fuk", "fck", "shit", "bitch", "bich", "cunt", "asshole", "arsehole",
        "bastard", "dick", "dickhead", "pussy", "slut", "whore", "wanker", "motherfucker",
        "mofo", "prick", "twat", "retard", "retarded", "nigger", "niger", "nigga", "faggot",
        "fagot", "rape", "rapist",
        // Hindi and Urdu, as they are usually typed in Roman letters
        "chutiya", "chutia", "chut", "gandu", "gaand", "gand", "bhosdike", "bhosdi",
        "bhosda", "madarchod", "madrchod", "mc", "behenchod", "bhenchod", "bc", "lund",
        "lauda", "randi", "kutiya", "kutta", "harami", "haramzada", "saala", "kamina",
        "kamine", "bhadwa", "tatti", "chodu", "chod",
        // Hindi does not end its words the way the suffix rule above expects, so the forms
        // that actually get typed are written out rather than derived.
        "chutiye", "chutiyo", "chutiyapa", "gande", "loda", "lode", "lawda", "lawde",
        "bsdk", "mkc", "bkl", "bhosdiwale", "haramkhor",
        // and in Devanagari
        "चूतिया", "गांडू", "भोसड़ी", "मादरचोद", "बहनचोद", "रंडी", "हरामी", "कमीना", "लौड़ा"
    ).map { flatten(it) }.toSet()

    /** The same list by consonants, for the digit-swap case above. */
    private val BLOCKED_SKELETONS: Set<String> =
        BLOCKED.map { consonants(it) }.filter { it.length >= 3 }.toSet()

    /**
     * Words a digit swap could turn into something on the list, which must go through
     * anyway. "Good shot" is the most common thing said over a pool table and masking it
     * would teach players that the filter is nonsense and to ignore it.
     */
    private val ALLOWED: Set<String> = setOf(
        "shot", "shots", "shoot", "shooting", "sheet", "shut", "shirt", "short",
        "class", "classic", "pass", "passed", "glass", "grass", "bass", "assist",
        "assume", "analysis", "cockpit", "shuttle", "dickens", "hit", "hits",
        // Words a taken-off ending would otherwise walk straight into the list.
        "chute", "chutes", "chutney", "gandhi", "randy", "grape", "grapes", "scrape"
    ).map { flatten(it) }.toSet()
}
