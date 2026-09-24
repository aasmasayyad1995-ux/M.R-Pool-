package com.mrpool.eightball.net

/**
 * How hard to try the match server before telling the player it is not there.
 *
 * The server runs on a free instance that is put to sleep after a spell with nobody on it,
 * and takes the better part of a minute to come back. While it is waking, a connection is
 * not slow -- it is *refused*, immediately, because the platform answers with an error page
 * of its own. So a longer connect timeout does nothing at all for the case it looks like it
 * should fix: the attempt fails in well under a second, every time, until the server is up.
 *
 * The only thing that helps is asking again. These are the numbers for that: try, wait a
 * little longer each time, and keep going for about as long as a cold start takes.
 *
 * The cost is that a server which is genuinely gone takes [GIVE_UP_MILLIS] to say so
 * instead of failing fast. That is the right way round. Telling somebody the game is broken
 * when it would have worked ten seconds later is much worse than a wait with a Cancel
 * button next to it -- and it is exactly what a Play reviewer would have met, since theirs
 * is by definition the first connection after a quiet spell.
 */
object ServerWake {

    /** Per attempt. Short, because a wake shows up as a refusal rather than a hang. */
    const val ATTEMPT_TIMEOUT_SECONDS = 20L

    /** Roughly how long a sleeping free instance takes to come back. */
    const val GIVE_UP_MILLIS = 75_000L

    /** A backstop, so a server that refuses instantly cannot spin the retry loop. */
    const val MAX_ATTEMPTS = 8

    private const val FIRST_RETRY_MILLIS = 1_500L
    private const val MAX_RETRY_MILLIS = 15_000L

    /**
     * Whether to try again, given how many attempts have already been made and how long the
     * player has been waiting since the first one.
     */
    fun shouldRetry(attemptsMade: Int, elapsedMillis: Long): Boolean =
        attemptsMade in 1 until MAX_ATTEMPTS && elapsedMillis < GIVE_UP_MILLIS

    /**
     * How long to wait before attempt number [attemptsMade] + 1.
     *
     * It doubles and then holds, so a server that is only just out of bed is not hammered,
     * and the gaps never grow so wide that a player is left watching a still screen.
     */
    fun retryDelayMillis(attemptsMade: Int): Long {
        if (attemptsMade <= 1) return FIRST_RETRY_MILLIS
        var delay = FIRST_RETRY_MILLIS
        repeat(attemptsMade - 1) {
            delay *= 2
            if (delay >= MAX_RETRY_MILLIS) return MAX_RETRY_MILLIS
        }
        return delay
    }
}
