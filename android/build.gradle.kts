plugins {
    id("com.android.application") version "8.3.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.23" apply false
    // Declared but never applied here: app/build.gradle.kts applies it only
    // when a google-services.json is present, so the FOSS build needs neither
    // the file nor the plugin.
    id("com.google.gms.google-services") version "4.4.2" apply false
}
