package com.mrpool.eightball.net

import com.mrpool.eightball.BuildConfig

/**
 * Where the match server is, and whether this build has one at all.
 *
 * Online play is the only feature that needs a server; a build without one plays perfectly
 * well against the robot and against a friend on the same device, so the online screen has
 * to be able to say what is missing rather than failing at a dead socket.
 */
object OnlineConfig {

    /** Set with `-PmatchServerUrl=...` at build time, or in gradle.properties. */
    val serverUrl: String = BuildConfig.MATCH_SERVER_URL.trim()

    val isConfigured: Boolean get() = serverUrl.isNotBlank()

    const val SETUP_HINT =
        "Online play needs the match server. Deploy server/ and build with " +
            "-PmatchServerUrl=wss://your-server/ws — see the README."
}
