plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.gruncode.browserdial"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.gruncode.browserdial"
        // API 26 is the floor because notification channels — which this app
        // depends on for its call alerts — arrived in Android 8.
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
        viewBinding = true
    }
}

// Firebase is optional at build time. Drop your own google-services.json into
// app/ and the plugin activates, enabling the Play Services transport; leave it
// out and the app still builds and runs on the ntfy transport alone. That is
// what keeps a single source tree publishable both on Google Play and on
// F-Droid, which refuses proprietary dependencies.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")

    // The ntfy subscriber uses the JDK's own HTTP client — no networking
    // library — so the only heavyweight dependency here is Firebase, and it is
    // inert unless a google-services.json is present.
    implementation(platform("com.google.firebase:firebase-bom:33.1.2"))
    implementation("com.google.firebase:firebase-messaging-ktx")

    testImplementation("junit:junit:4.13.2")
}
