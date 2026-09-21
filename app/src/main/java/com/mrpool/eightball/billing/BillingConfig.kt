package com.mrpool.eightball.billing

import com.mrpool.eightball.BuildConfig

/**
 * Where the subscription server is, and whether this build has one at all.
 *
 * Like online play, the subscription is the only thing that needs a server, and a build
 * without one has to be able to say so rather than failing at a dead socket. There are no
 * keys here and there never will be: the app knows a URL, and nothing else.
 */
object BillingConfig {

    /** Set with `-PbillingServerUrl=...` at build time, or in gradle.properties. */
    val serverUrl: String = BuildConfig.BILLING_SERVER_URL.trim().trimEnd('/')

    val isConfigured: Boolean get() = serverUrl.isNotBlank()

    const val SETUP_HINT =
        "Subscriptions need the server. Deploy server/ with your Razorpay keys and build " +
            "with -PbillingServerUrl=https://your-server — see the README."
}
