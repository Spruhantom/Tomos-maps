plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "uk.co.tomosmaps"
    compileSdk = 37

    defaultConfig {
        applicationId = "uk.co.tomosmaps"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.2.0"
    }
}

dependencies {
    implementation(project(":composeApp"))
    implementation("androidx.activity:activity-compose:1.13.0")
}
