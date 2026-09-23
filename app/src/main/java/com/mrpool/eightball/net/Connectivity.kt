package com.mrpool.eightball.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Whether the phone can reach the internet.
 *
 * Android is asked for a network that it has actually *validated* — one it has checked
 * really reaches the internet — rather than merely one that is connected. The difference
 * is the hotel wifi that a phone joins happily and that carries nothing: counting that as
 * online would let a player into the game and then break everything they tried to do.
 *
 * The flicker is smoothed here rather than on screen. Every handover between wifi and
 * mobile data reports a moment with nothing, and [ConnectionGate.GRACE_MILLIS] is how long
 * one has to last before the game believes it. The decision itself is in [ConnectionState],
 * which is plain Kotlin and tested.
 */
class Connectivity(context: Context) {

    private val manager =
        context.applicationContext.getSystemService(ConnectivityManager::class.java)

    private val state = ConnectionState()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var countdown: Job? = null

    private val _online = MutableStateFlow(true)

    /** True while the phone has a validated connection. Starts true, corrected on the first report. */
    val online: StateFlow<Boolean> = _online.asStateFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = report(true)

        override fun onLost(network: Network) = report(hasValidatedNetwork())

        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            report(
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            )
        }
    }

    init {
        report(hasValidatedNetwork())
        runCatching {
            manager?.registerNetworkCallback(
                NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build(),
                callback
            )
        }.onFailure {
            // A phone that will not let us watch the network must not be a phone that
            // cannot play: assume there is a connection rather than locking the player out.
            Log.w(TAG, "could not watch the network", it)
            _online.value = true
        }
    }

    /** Asks Android directly, for the first reading and after a network goes away. */
    private fun hasValidatedNetwork(): Boolean {
        val active = manager?.activeNetwork ?: return false
        val caps = manager.getNetworkCapabilities(active) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /** Re-reads the network and publishes it. For the Retry button on the offline screen. */
    fun recheck() = report(hasValidatedNetwork())

    private fun report(connected: Boolean) {
        val now = System.currentTimeMillis()
        state.report(connected, now)
        countdown?.cancel()

        if (connected) {
            _online.value = true
            return
        }

        // The loss has to outlast the grace period. Waiting here, rather than in the
        // screen, keeps every caller from having to know about it.
        val wait = state.millisUntilOffline(now) ?: 0L
        countdown = scope.launch {
            delay(wait)
            if (state.isOffline(System.currentTimeMillis())) _online.value = false
        }
    }

    fun release() {
        countdown?.cancel()
        scope.cancel()
        runCatching { manager?.unregisterNetworkCallback(callback) }
    }

    private companion object {
        const val TAG = "Connectivity"
    }
}
