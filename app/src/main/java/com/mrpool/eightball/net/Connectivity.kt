package com.mrpool.eightball.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Whether the phone can reach the internet.
 *
 * ## Why this asks rather than listens
 *
 * The first version of this trusted Android's network callbacks alone, and it did not
 * work: turning mobile data off in the middle of a match changed nothing on screen. Three
 * separate faults, any one of which was enough:
 *
 *  - `onLost` read `activeNetwork` to decide what was left, and at the instant a network
 *    is lost that still hands back the network which is going away, still marked as
 *    validated. So the loss reported a connection.
 *  - `onCapabilitiesChanged` believed whichever network it was told about. The dying
 *    network's own capabilities arrive with the loss and still say validated, so that put
 *    the connection back after the loss had taken it away.
 *  - `onAvailable` reported a connection for any network at all, before Android had
 *    checked whether it carried anything.
 *
 * So the callbacks are no longer believed on their own. They are a nudge: something
 * changed, look again. The answer always comes from asking Android afresh, and a slow tick
 * asks anyway, so a callback that never arrives or arrives with stale news cannot leave
 * the game believing it is online when it is not. Being a second late is a small cost;
 * being wrong until the player restarts the app is not.
 *
 * ## What counts as connected
 *
 * A network Android has *validated* — one it has checked really carries traffic — rather
 * than one the phone has merely joined. The difference is the cafe wifi that connects
 * happily and carries nothing.
 *
 * Reaching the match server is deliberately not part of it: the server sleeps when nobody
 * is using it and takes up to a minute to wake, so tying the game to it would lock the
 * player out of their own game every morning.
 *
 * The flicker between wifi and mobile data is smoothed by [ConnectionState], which is
 * plain Kotlin and tested.
 */
class Connectivity(context: Context) {

    private val manager =
        context.applicationContext.getSystemService(ConnectivityManager::class.java)

    private val state = ConnectionState()

    /** One thread decides, so a callback and the tick cannot race each other. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _online = MutableStateFlow(true)

    /** True while the phone has a validated connection. */
    val online: StateFlow<Boolean> = _online.asStateFlow()

    /**
     * Every callback says the same thing: something moved, go and look.
     *
     * None of them is trusted for *what* changed, because each of the three lied in the
     * version this replaces.
     */
    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = poke()
        override fun onLost(network: Network) = poke()
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = poke()
    }

    private var watching = false

    init {
        refresh()
        watching = runCatching {
            manager?.registerNetworkCallback(
                NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build(),
                callback
            )
            true
        }.getOrElse {
            // Not being allowed to watch the network must not mean not being allowed to
            // play. The tick below still asks, so this loses the instant reaction and
            // nothing else.
            Log.w(TAG, "could not watch the network", it)
            false
        }

        // The tick is what makes this reliable. A missed callback, a stale one, or a phone
        // that simply does not send them can no longer leave the game believing it is on.
        scope.launch {
            while (isActive) {
                delay(TICK_MILLIS)
                refresh()
            }
        }
    }

    /** Re-reads the network on the one thread that is allowed to decide. */
    private fun poke() {
        scope.launch { refresh() }
    }

    /** Asks Android, applies the grace period, and publishes the answer. */
    private fun refresh() {
        val now = System.currentTimeMillis()
        state.report(hasValidatedNetwork(), now)
        _online.value = !state.isOffline(now)
    }

    /**
     * Whether Android currently has a network it has validated.
     *
     * Asked fresh every time. `activeNetwork` is stale for a moment after a loss, which is
     * exactly why nothing acts on a single reading: [ConnectionState] needs the loss to
     * hold for the whole grace period, by which time this has been asked several times.
     */
    private fun hasValidatedNetwork(): Boolean {
        val active = manager?.activeNetwork ?: return false
        val caps = manager.getNetworkCapabilities(active) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /** Re-reads the network now. For the Try again button on the offline screen. */
    fun recheck() = poke()

    fun release() {
        if (watching) runCatching { manager?.unregisterNetworkCallback(callback) }
        scope.cancel()
    }

    private companion object {
        const val TAG = "Connectivity"

        /**
         * How often the network is asked about regardless of callbacks.
         *
         * Fast enough that the grace period, not this, decides how long the player waits;
         * slow enough to be nothing at all next to drawing a 3D table sixty times a second.
         */
        const val TICK_MILLIS = 1_000L
    }
}
