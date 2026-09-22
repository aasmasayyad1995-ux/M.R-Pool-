package com.mrpool.eightball.net

/** One line of the match chat, cleaned and ready to be drawn. */
data class ChatLine(
    /** Rises by one per line, so Compose can key the list without comparing text. */
    val id: Long,
    val author: String,
    val text: String,
    val fromLocal: Boolean,
    /** True for the game's own remarks — "you muted Raj" — rather than a player's words. */
    val note: Boolean = false
)

/** What came of pressing send. */
sealed interface ChatSend {
    /** It went. */
    data object Sent : ChatSend

    /** There was nothing in the box once the spaces were trimmed off. */
    data object Empty : ChatSend

    /** Too many messages too quickly; try again in [waitMillis]. */
    data class TooFast(val waitMillis: Long) : ChatSend
}

/**
 * The chat for one online match.
 *
 * Four things guard it, because opening a text box between strangers without them would be
 * careless:
 *
 *  - every line, sent or received, goes through [ChatFilter];
 *  - both directions are rate limited by [ChatRate], the incoming one because the other
 *    phone is running a copy of this app that we cannot vouch for;
 *  - the player can mute the opponent, after which nothing of theirs is shown at all;
 *  - the player can report them, which mutes them and sends the last few lines to the
 *    server's log.
 *
 * Be straight about the limits of that last one: there are no accounts, so a report cannot
 * ban anybody. It records what was said. Mute is the part that actually helps, and it is
 * the one the player controls.
 *
 * Every method is synchronized: lines arrive on the network thread and the screen reads
 * them on the render thread.
 */
class ChatLog(
    private val localName: String,
    private val remoteName: String,
    /** Puts a line on the wire. */
    private val publish: (String) -> Unit,
    /** Sends a report, with the opponent's recent lines as what it is about. */
    private val publishReport: (List<String>) -> Unit = {},
    private val clock: () -> Long = System::currentTimeMillis
) {

    private val lines = ArrayList<ChatLine>()
    private var nextId = 0L

    private val outgoing = ChatRate()
    private val incoming = ChatRate()

    /** True once the player has silenced the opponent. Never sent anywhere. */
    var muted: Boolean = false
        private set

    /** Lines that have arrived since the player last had the panel open. */
    var unread: Int = 0
        private set

    /** True once a report has gone, so the button can say so instead of sending again. */
    var reported: Boolean = false
        private set

    /** A copy, so the screen can hold it across frames without it changing underneath. */
    @Synchronized
    fun lines(): List<ChatLine> = lines.toList()

    // ---------------------------------------------------------------------- outgoing

    /** Cleans, rate limits and sends what the player typed. */
    @Synchronized
    fun say(raw: String): ChatSend {
        val tidied = ChatFilter.tidy(raw)
        if (tidied.isEmpty()) return ChatSend.Empty

        val now = clock()
        if (!outgoing.allow(now)) return ChatSend.TooFast(outgoing.waitMillis(now))

        // Cleaned before it is sent, not after it is received, so the player is not saying
        // one thing and seeing another — what appears on their screen is what went.
        val cleaned = ChatFilter.clean(tidied)
        publish(cleaned)
        add(ChatLine(nextId++, localName, cleaned, fromLocal = true))
        return ChatSend.Sent
    }

    // ---------------------------------------------------------------------- incoming

    /** Takes a line off the wire. Silently drops it when muted or when it is a flood. */
    @Synchronized
    fun receive(raw: String) {
        if (muted) return

        val tidied = ChatFilter.tidy(raw)
        if (tidied.isEmpty()) return

        // The opponent's app does its own limiting, but the opponent's app is not ours to
        // trust: a changed copy of it could hold the send button down forever.
        if (!incoming.allow(clock())) {
            if (!floodNoted) {
                floodNoted = true
                addNote("$remoteName is sending too fast — some messages are hidden")
            }
            return
        }
        floodNoted = false

        add(ChatLine(nextId++, remoteName, ChatFilter.clean(tidied), fromLocal = false))
        unread++
    }

    private var floodNoted = false

    // ------------------------------------------------------------------ what to do about it

    /** Silences the opponent, or lets them back in. Returns the state it ended in. */
    @Synchronized
    fun toggleMute(): Boolean {
        muted = !muted
        addNote(
            if (muted) "You muted $remoteName for the rest of this match"
            else "You unmuted $remoteName"
        )
        return muted
    }

    /**
     * Reports the opponent: sends their recent lines to the server's log and mutes them.
     *
     * Only what they wrote is sent. The player's own half of the conversation stays on the
     * phone, because the report is about the opponent and nothing else is the server's
     * business.
     */
    @Synchronized
    fun report(): Boolean {
        if (reported) return false
        reported = true

        val theirs = lines.filter { !it.fromLocal && !it.note }
            .takeLast(REPORT_LINES)
            .map { it.text }
        publishReport(theirs)

        if (!muted) {
            muted = true
            addNote("You reported and muted $remoteName")
        } else {
            addNote("You reported $remoteName")
        }
        addNote("There are no accounts in this game, so a report cannot ban anyone. It is written down, and they stay muted.")
        return true
    }

    /** Called when the player opens the panel, so the badge clears. */
    @Synchronized
    fun markRead() {
        unread = 0
    }

    // ----------------------------------------------------------------------- internals

    private fun addNote(text: String) {
        add(ChatLine(nextId++, "", text, fromLocal = true, note = true))
    }

    private fun add(line: ChatLine) {
        lines.add(line)
        // A long match must not grow a list forever; the top of it is scrolled away anyway.
        while (lines.size > MAX_LINES) lines.removeAt(0)
    }

    companion object {
        /** How many lines are kept. Beyond this the oldest fall off the top. */
        const val MAX_LINES = 60

        /** How much of the opponent's talk a report carries. */
        const val REPORT_LINES = 6
    }
}
