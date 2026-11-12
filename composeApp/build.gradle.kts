plugins {
    kotlin("multiplatform")
    kotlin("android")
    kotlin("plugin.compose")
    id("com.android.application")
    id("org.jetbrains.compose")
}
kotlin {
    androidTarget()
    jvm("desktop")
    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        androidMain.dependencies {
            implementation("androidx.activity:activity-compose:1.10.1")
        }
        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
        }
    }
}
android {
    namespace = "uk.co.tomosmaps"
    compileSdk = 36
    defaultConfig {
        applicationId = "uk.co.tomosmaps"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }
}
compose.desktop {
    application {
        mainClass = "uk.co.tomosmaps.MainKt"
    }
}
