plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.mrpool.eightball"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.mrpool.eightball"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Where the match server lives. Run server/ locally or deploy it, then point this
        // at it. An empty value simply disables online play; see README "Online play".
        buildConfigField(
            "String",
            "MATCH_SERVER_URL",
            "\"${project.findProperty("matchServerUrl") ?: ""}\""
        )

        // AdMob.
        //
        // The defaults are Google's own test units, published by Google for exactly this
        // and safe to build, run and share. Your real ids go in as gradle properties.
        //
        // NEVER test with your real ad units. Google reads your own taps on your own ads
        // as invalid traffic and closes AdMob accounts for it. That is why the test ids
        // are the default rather than something you have to remember to switch to.
        //
        // None of these are secrets — every shipped Android app carries them in the clear —
        // so they are properties for convenience, not for safety.
        val admobAppId = (project.findProperty("admobAppId") as String?)
            ?: "ca-app-pub-3940256099942544~3347511713"
        manifestPlaceholders["admobAppId"] = admobAppId
        buildConfigField("String", "ADMOB_APP_ID", "\"$admobAppId\"")
        buildConfigField(
            "String",
            "ADMOB_BANNER_UNIT",
            "\"${project.findProperty("admobBannerUnit") ?: "ca-app-pub-3940256099942544/6300978111"}\""
        )
        buildConfigField(
            "String",
            "ADMOB_INTERSTITIAL_UNIT",
            "\"${project.findProperty("admobInterstitialUnit") ?: "ca-app-pub-3940256099942544/1033173712"}\""
        )
        buildConfigField(
            "String",
            "ADMOB_REWARDED_UNIT",
            "\"${project.findProperty("admobRewardedUnit") ?: "ca-app-pub-3940256099942544/5224354917"}\""
        )
        // True while the ids above are Google's test ones, so the app can say so on screen
        // rather than leaving anybody wondering why the ads are all for "Test Ad".
        buildConfigField(
            "boolean",
            "ADMOB_TEST_IDS",
            (project.findProperty("admobAppId") == null).toString()
        )

        // Shown in the corner of the lobby. CI passes the commit it built, so a screenshot
        // always says which build it came from — without that, a phone quietly running an
        // older install looks exactly like a fix that did not work.
        buildConfigField(
            "String",
            "BUILD_ID",
            "\"${(project.findProperty("buildId") as String? ?: "local").take(7)}\""
        )
    }

    // A debug key that is checked in and the same for everyone.
    //
    // Without this, Gradle invents a keystore wherever it happens to be building. Every CI
    // run is a fresh machine, so every build was signed by a different key — and Android
    // refuses to install an update signed by a different key than the app already on the
    // phone. The download succeeds, the install does not, and the old build keeps running.
    //
    // This key is for debug builds only. It is published in this repository and its
    // password is the Android default, so it must never be used to sign a real release.
    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")

    // Reads a photo's orientation. Phones write down which way they were held rather than
    // rotating the pixels, so without this an avatar picked from the camera roll arrives
    // lying on its side.
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Online play. The rest of the game never touches this.
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.android.gms:play-services-ads:23.2.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.06.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
