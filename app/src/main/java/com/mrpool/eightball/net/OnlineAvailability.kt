package com.mrpool.eightball.net

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.database.FirebaseDatabase

/**
 * Whether this build can play online at all.
 *
 * Online play needs a Firebase project: a `google-services.json` in `app/`, and a Realtime
 * Database created in the console. A build without it is perfectly playable — the robot and
 * the two player game do not touch the network — so the online screen has to be able to say
 * so politely rather than taking the app down with it.
 */
object OnlineAvailability {

    /** Set up once the first time online play is asked for. */
    private var database: FirebaseDatabase? = null

    fun database(context: Context): FirebaseDatabase? {
        database?.let { return it }
        return try {
            // Returns null rather than throwing when no google-services.json was bundled.
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context) ?: return null
            }
            FirebaseDatabase.getInstance().also {
                it.setPersistenceEnabled(false)
                database = it
            }
        } catch (t: Throwable) {
            Log.w(TAG, "online play is not configured in this build", t)
            null
        }
    }

    fun isConfigured(context: Context): Boolean = database(context) != null

    const val SETUP_HINT =
        "Online play needs a Firebase project. Add google-services.json to app/ and " +
            "create a Realtime Database — see the README."

    private const val TAG = "OnlineAvailability"
}
