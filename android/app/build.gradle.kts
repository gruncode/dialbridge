plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.gruncode.dialbridge"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.gruncode.dialbridge"
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

dependencies {
    // Deliberately minimal. No networking library: the subscriber uses the
    // JDK's own HTTP client, which keeps the app small and free of any
    // proprietary dependency, so it can be distributed through F-Droid.
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")

    testImplementation("junit:junit:4.13.2")
}
