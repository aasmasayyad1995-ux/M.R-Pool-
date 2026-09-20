plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    // Reads app/google-services.json. Online play is the only thing that needs it; see
    // README "Online play" for how to create the project it comes from.
    id("com.google.gms.google-services") version "4.4.2" apply false
}
