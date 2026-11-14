plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "uk.co.tomosmaps"
    compileSdk = 37

    defaultConfig {
        val mapsKey = providers.gradleProperty("TOMOS_GOOGLE_MAPS_API_KEY").orNull
            ?: System.getenv("TOMOS_GOOGLE_MAPS_API_KEY")
            ?: ""
        manifestPlaceholders["tomosGoogleMapsApiKey"] = mapsKey
        applicationId = "uk.co.tomosmaps"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.2.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    implementation(project(":composeApp"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.window:window:1.4.0")

    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
}
