package com.mrpool.eightball.net

/** What the app should be showing the player about the connection. */
sealed interface ConnectionNotice {

    /** Nothing to say. Carry on. */
    data object None : ConnectionNotice

    /**
     * Nothing can be played: cover whatever is on screen until the connection is back.
     *
     * The game needs a connection to be played at all, so this is not a warning the
     * player can wave away — there is nothing behind it to go back to.
     */
    data object Blocked : ConnectionNotice

    /**
     * A match was being played and cannot go on.
     *
     * One button, and it says OK. A match that has lost its connection cannot be resumed
     * from where it stopped, so offering anything but the way out would be offering
     * something that does not work.
     */
    data object MatchEnded : ConnectionNotice
}

/**
 * The rule that decides what a lost connection means, kept away from Android so it can be
 * tested without a phone.
 *
 * Two things live here. The first is which of the three notices belongs on screen. The
 * second is the grace period, which matters more than it looks: Android reports a moment
 * of nothing at every handover between wifi and mobile data, and a game that threw the
 * player out of a match on each of those would be worse to use than one with no check at
 * all. The connection has to be gone for [GRACE_MILLIS] before the game believes it.
 *
 * Reaching the match server is deliberately not part of this. The server sleeps when
 * nobody is using it and takes up to a minute to wake, so tying the whole game to it
 * would lock the player out of their own game every morning.
 */
object ConnectionGate {

    /**
     * How long the connection must stay gone before the game acts on it.
     *
     * Long enough to ride out a wifi-to-mobile handover, short enough that a player who
     * has genuinely lost signal is not left aiming at a table that will not accept the shot.
     */
    const val GRACE_MILLIS = 4_000L

    /**
     * What to show.
     *
     * @param online whether the phone has a usable connection, after the grace period
     * @param inMatch whether a match is on the screen right now
     */
    fun noticeFor(online: Boolean, inMatch: Boolean): ConnectionNotice = when {
        online -> ConnectionNotice.None
        inMatch -> ConnectionNotice.MatchEnded
        else -> ConnectionNotice.Blocked
    }
}

/**
 * Tracks whether the connection has been gone long enough to act on.
 *
 * Fed by whatever the platform reports, which flickers; asked by the screen, which must
 * not. Holds no Android types, so the flicker cases can be driven by hand in a test
 * instead of hoped about.
 */
class ConnectionState(private val graceMillis: Long = ConnectionGate.GRACE_MILLIS) {

    /** When the connection went away, or null while it is there. */
    private var lostAt: Long? = null

    /** What the platform last told us, before the grace period is applied. */
    var connected: Boolean = true
        private set

    /** Records what the platform reported. */
    fun report(connected: Boolean, nowMillis: Long) {
        if (connected) {
            this.connected = true
            lostAt = null
            return
        }
        // Only the first report of a loss starts the clock; a second one must not push
        // the deadline back, or a phone that reports the loss repeatedly never times out.
        if (this.connected) lostAt = nowMillis
        this.connected = false
    }

    /** True once the connection has been gone for the whole grace period. */
    fun isOffline(nowMillis: Long): Boolean {
        val since = lostAt ?: return false
        return nowMillis - since >= graceMillis
    }

    /** How long until [isOffline] turns true, or null when the connection is there. */
    fun millisUntilOffline(nowMillis: Long): Long? {
        val since = lostAt ?: return null
        return (since + graceMillis - nowMillis).coerceAtLeast(0L)
    }
}
